package website

import com.pambrose.jev4k.JevApi
import com.pambrose.jev4k.JevClient
import com.pambrose.jev4k.JevQuery
import com.pambrose.jev4k.ask

// --8<-- [start:typed-query]
object FirstTriage : JevQuery() {
    val urgent by noul("Does this message convey urgency or time-sensitivity?")
    val team by choice<Team>("Which team should handle this message?")
    val frustration by score("How frustrated does the customer appear?") {
        levels("Calm, just stating facts", "Frustrated but civil", "Very angry, strong language")
    }
}

suspend fun routeTicket(
    jev: JevApi,
    ticket: String,
): String {
    val result = jev.ask(FirstTriage, state = ticket)

    val queue =
        when (result[FirstTriage.team].choice) {
            Team.BILLING -> "billing"
            Team.TECHNICAL -> "engineering"
            Team.SALES -> "sales"
        }
    val priority = if (result[FirstTriage.urgent].isTrue(threshold = 0.7)) "high" else "normal"
    return "$queue ($priority)"
}
// --8<-- [end:typed-query]

// --8<-- [start:blocking]
fun routeTicketBlocking(ticket: String): String =
    JevClient().use { jev ->
        val result = jev.blocking.ask(FirstTriage, state = ticket)
        result[FirstTriage.team].choice.name
    }
// --8<-- [end:blocking]
