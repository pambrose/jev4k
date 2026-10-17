package website

import com.pambrose.jev4k.JevApi
import com.pambrose.jev4k.JevOption
import com.pambrose.jev4k.JevQuery
import com.pambrose.jev4k.ask
import com.pambrose.jev4k.query
import java.time.DateTimeException
import java.time.LocalDate
import java.time.Month

// --8<-- [start:candidate-spans]
private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")

// Code finds the candidates; Jev picks the one the question means. The answer is always a verbatim span.
suspend fun receiptAddress(
    jev: JevApi,
    email: String,
): String? {
    val candidates = EMAIL.findAll(email).map { it.value }.distinct().toList()
    if (candidates.isEmpty()) return null

    val pick =
        jev
            .query(state = email) {
                choice("address", "Which email address does the sender want their receipt sent to?") {
                    candidates.forEach { option(it) }
                    "none" means "None of these is the requested address"
                }
            }.choice("address")

    if (pick.choice == "none" || pick.confidence < 0.6) return null
    return pick.choice.lowercase()
}
// --8<-- [end:candidate-spans]

// --8<-- [start:date-query]
enum class DateMode(
    override val description: String,
) : JevOption {
    ABSOLUTE("A calendar date naming a month, e.g. 'August 14'"),
    RELATIVE("Relative to today: today, tomorrow, or the day after tomorrow"),
    NONE("The document does not state this date"),
}

enum class RelativeDay { TODAY, TOMORROW, DAY_AFTER, NONE }

// The model reads which date parts the text names; code does all of the calendar math.
class DueDateQuery(
    role: String,
) : JevQuery() {
    val mode by choice<DateMode>("How is $role written?")

    // Month names as strings, not choice<Month>: an enum of months has no way to say "none".
    val month by choice("If $role is an absolute date, which month is it in?") {
        Month.entries.forEach { option(it.name) }
        "none" means "The document does not state a month"
    }
    val day by choice("If $role is an absolute date, which day of the month is it?") {
        (1..31).forEach { option(it.toString()) }
        "none" means "The document does not state a day"
    }
    val year by choice("If $role is an absolute date, which year is it in?") {
        (1900..2050).forEach { option(it.toString()) }
        "out_of_range" means "A year is stated for this date, but it isn't one of those listed"
        "none" means "The document states no year for this date"
    }
    val relative by choice<RelativeDay>("If $role is relative to today, which day is it?")
}
// --8<-- [end:date-query]

// --8<-- [start:date-assemble]
data class DueDate(
    val date: LocalDate?,
    val needsReview: Boolean,
)

suspend fun dueDate(
    jev: JevApi,
    document: String,
    today: LocalDate,
): DueDate {
    val dueDateQuery = DueDateQuery("the deadline to return the form")
    val result = jev.ask(dueDateQuery, state = document)
    val mode = result[dueDateQuery.mode]

    val (date, confidence) =
        when (mode.choice) {
            DateMode.ABSOLUTE -> {
                val month = result[dueDateQuery.month]
                val day = result[dueDateQuery.day]
                val year = result[dueDateQuery.year]
                dateOf(month.choice, day.choice, year.choice, today) to
                    minOf(mode.confidence, month.confidence, day.confidence, year.confidence)
            }

            DateMode.RELATIVE -> {
                val relative = result[dueDateQuery.relative]
                val offset =
                    when (relative.choice) {
                        RelativeDay.TODAY -> 0L
                        RelativeDay.TOMORROW -> 1L
                        RelativeDay.DAY_AFTER -> 2L
                        RelativeDay.NONE -> null
                    }
                offset?.let(today::plusDays) to minOf(mode.confidence, relative.confidence)
            }

            DateMode.NONE -> {
                null to mode.confidence
            }
        }
    // Gate on the weakest part that was used.
    return DueDate(date, needsReview = date == null || confidence < 0.6)
}

// The date in the year the text states, or with no year stated, the next occurrence on or after today. Null for a
// "none" month or day, a year outside the list ("out_of_range"), or an impossible date like February 30.
private fun dateOf(
    month: String,
    day: String,
    year: String,
    today: LocalDate,
): LocalDate? {
    val monthOfYear = Month.entries.firstOrNull { it.name == month }
    val dayOfMonth = day.toIntOrNull()
    val statedYear = year.toIntOrNull()
    val yearKnown = statedYear != null || year == "none" // not "out_of_range"
    if (monthOfYear == null || dayOfMonth == null || !yearKnown) return null
    return try {
        if (statedYear != null) {
            LocalDate.of(statedYear, monthOfYear, dayOfMonth)
        } else {
            LocalDate.of(today.year, monthOfYear, dayOfMonth).let { if (it < today) it.plusYears(1) else it }
        }
    } catch (e: DateTimeException) {
        println("impossible date: ${e.message}")
        null
    }
}
// --8<-- [end:date-assemble]

// --8<-- [start:function-calling]
enum class ChartStyle { LINE, CANDLES }

enum class Window { ONE_DAY, ONE_WEEK, ONE_MONTH, THREE_MONTHS }

fun plotPrice(
    symbol: String,
    style: ChartStyle = ChartStyle.LINE,
    window: Window = Window.ONE_MONTH,
) = println("plot $symbol as $style over $window")

// One request routes the call and fills its arguments; "stated" Nouls leave unstated arguments at their defaults.
object PlotCommand : JevQuery() {
    val function by choice("Which of these actions is the user asking for?") {
        "plot_price" means "Plot a stock's price over a period of time"
        "none_of_these" means "Anything else"
    }
    val symbol by choice("Which stock is the user asking about?") {
        "NVDA" means "Nvidia, often written NVDA"
        "AAPL" means "Apple, often written AAPL"
        "TSLA" means "Tesla, often written TSLA"
        "other" means "A stock other than these, or none"
    }
    val style by choice<ChartStyle>("Does the user want a plain line or candles?")
    val styleStated by noul("Does the user say how the chart should be drawn, such as a line or candles?")
    val window by choice<Window>("How far back is the user asking about?")
    val windowStated by noul("Does the user say how far back to look, such as today, this week, or this quarter?")
}

// True when the command was a plot this tool can make, and it was made.
suspend fun runPlotCommand(
    jev: JevApi,
    command: String,
): Boolean {
    val result = jev.ask(PlotCommand, state = command)
    val function = result[PlotCommand.function]
    val symbol = result[PlotCommand.symbol]
    val style = result[PlotCommand.style].takeIf { result[PlotCommand.styleStated].isTrue() }
    val window = result[PlotCommand.window].takeIf { result[PlotCommand.windowStated].isTrue() }
    // Gate on the weakest part the call uses, as the date example does, and on either escape option.
    val confidence = listOfNotNull(function, symbol, style, window).minOf { it.confidence }
    if (function.choice != "plot_price" || symbol.choice == "other" || confidence < 0.6) return false
    plotPrice(symbol.choice, style?.choice ?: ChartStyle.LINE, window?.choice ?: Window.ONE_MONTH)
    return true
}
// --8<-- [end:function-calling]
