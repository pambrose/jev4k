package website

import com.pambrose.jev4k.ChoiceAnswer
import com.pambrose.jev4k.JevApi
import com.pambrose.jev4k.JevQuery
import com.pambrose.jev4k.NoulAnswer
import com.pambrose.jev4k.ScoreAnswer
import com.pambrose.jev4k.ask
import com.pambrose.jev4k.query
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

// --8<-- [start:object-query]
object Triage : JevQuery() {
    val urgent by noul("Does this message convey urgency or time-sensitivity?") {
        whenTrue("Explicitly time-sensitive, or the customer is blocked right now")
        whenFalse("No urgency expressed")
    }
    val team by choice<Team>("Which team should handle this message?")
    val frustration by score("How frustrated does the customer appear?") {
        level("Calm, just stating facts")
        level("Frustrated but civil")
        level("Very angry, strong language or threatening to leave")
    }
}
// --8<-- [end:object-query]

suspend fun typedAsk(
    jev: JevApi,
    ticket: String,
) {
    // --8<-- [start:ask]
    val result = jev.ask(Triage, state = ticket)

    val urgent: NoulAnswer = result[Triage.urgent]
    val team: ChoiceAnswer<Team> = result[Triage.team] // team.choice is a Team
    val frustration: ScoreAnswer = result[Triage.frustration]

    println("${team.choice} urgent=${urgent.noul} frustration=${frustration.normalized}")
    // --8<-- [end:ask]
}

// --8<-- [start:id-override]
object Sentiment : JevQuery() {
    // The property name is the default id; `id =` sends something else.
    val tone by choice("What is the customer's tone?", id = "customer_tone") {
        options("calm", "frustrated", "angry")
    }
}
// --8<-- [end:id-override]

// --8<-- [start:inheritance]
open class BaseChecks : JevQuery() {
    val spam by noul("Is this message unsolicited advertising or spam?")
    val abusive by noul("Does this message contain abusive or threatening language?")
}

// Subclass questions come after the base class's, in declaration order.
object ForumPostChecks : BaseChecks() {
    val offTopic by noul("Is the post unrelated to software development?")
}
// --8<-- [end:inheritance]

// --8<-- [start:parameterized]
// A query can be a class, so its questions can depend on runtime values.
class PolicyCheck(
    policy: String,
) : JevQuery() {
    val violates by noul("Does the message violate this policy: $policy")
    val severity by score("How serious is the violation of this policy: $policy") {
        levels("No violation", "Minor, a reminder is enough", "Serious, needs moderator action")
    }
}

val policyChecks = listOf("No personal data", "No medical advice").map(::PolicyCheck)

suspend fun policyViolations(
    jev: JevApi,
    message: String,
): List<Double> = policyChecks.map { check -> jev.ask(check, state = message)[check.violates].noul }
// --8<-- [end:parameterized]

suspend fun typedInclude(
    jev: JevApi,
    ticket: String,
) {
    // --8<-- [start:include]
    // Mix a typed query with ad-hoc questions in one request; the typed handles still work.
    val result =
        jev.query(state = ticket) {
            include(Triage)
            noul("mentions_competitor", "Does the message mention a competing product?")
        }
    val team = result[Triage.team].choice
    val competitor = result.noul("mentions_competitor").noul
    // --8<-- [end:include]
    println("$team $competitor")
}

// --8<-- [start:many-states]
// The same query works for any number of states; run them concurrently if you like.
suspend fun triageAll(
    jev: JevApi,
    tickets: List<String>,
): Map<String, Team> =
    coroutineScope {
        tickets
            .map { ticket -> async { ticket to jev.ask(Triage, state = ticket)[Triage.team].choice } }
            .awaitAll()
            .toMap()
    }
// --8<-- [end:many-states]
