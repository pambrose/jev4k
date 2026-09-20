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
    RELATIVE("Relative to today: today, tomorrow, or a named weekday"),
    NONE("The document does not state this date"),
}

enum class RelativeDay { TODAY, TOMORROW, DAY_AFTER, NONE }

// The model reads which date parts the text names; code does all of the calendar math.
class DueDateQuery(
    role: String,
) : JevQuery() {
    val mode by choice<DateMode>("How is $role written?")
    val month by choice<Month>("If $role is an absolute date, which month is it in?")
    val day by choice("If $role is an absolute date, which day of the month is it?") {
        (1..31).forEach { option(it.toString()) }
        "none" means "The document does not state a day"
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
                nextDate(month.choice, day.choice, today) to minOf(mode.confidence, month.confidence, day.confidence)
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

// The next occurrence of month/day on or after today; null for "none" or an impossible date like February 30.
private fun nextDate(
    month: Month,
    day: String,
    today: LocalDate,
): LocalDate? {
    val dayOfMonth = day.toIntOrNull() ?: return null
    return try {
        LocalDate.of(today.year, month, dayOfMonth).let { if (it < today) it.plusYears(1) else it }
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
    val symbol by choice("Which stock is the user asking about?") {
        "NVDA" means "Nvidia, often written NVDA"
        "AAPL" means "Apple, often written AAPL"
        "TSLA" means "Tesla, often written TSLA"
    }
    val style by choice<ChartStyle>("Does the user want a plain line or candles?")
    val styleStated by noul("Does the user say how the chart should be drawn, such as a line or candles?")
    val window by choice<Window>("How far back is the user asking about?")
    val windowStated by noul("Does the user say how far back to look, such as today, this week, or this quarter?")
}

suspend fun runPlotCommand(
    jev: JevApi,
    command: String,
) {
    val result = jev.ask(PlotCommand, state = command)
    plotPrice(
        symbol = result[PlotCommand.symbol].choice,
        style = if (result[PlotCommand.styleStated].isTrue()) result[PlotCommand.style].choice else ChartStyle.LINE,
        window = if (result[PlotCommand.windowStated].isTrue()) result[PlotCommand.window].choice else Window.ONE_MONTH,
    )
}
// --8<-- [end:function-calling]
