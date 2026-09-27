package website

import com.pambrose.jev4k.ChoiceAnswer
import com.pambrose.jev4k.JevApi
import com.pambrose.jev4k.JevOption
import com.pambrose.jev4k.JevQuery
import com.pambrose.jev4k.ask

fun confidenceBands(team: ChoiceAnswer<Team>) {
    // --8<-- [start:bands]
    when {
        // High: act automatically.
        team.confidence >= 0.8 -> routeTo(team.choice.name, "auto")

        // Medium: act, but flag the case for a second look.
        team.confidence >= 0.5 -> routeTo(team.choice.name, "flag for review")

        // Low: don't act on a guess.
        else -> routeTo("triage", "human decides")
    }
    // --8<-- [end:bands]
}

// --8<-- [start:risk-scaled]
enum class BankAction(
    override val description: String,
) : JevOption {
    CHECK_BALANCE("Check the balance of an account"),
    APPROVE_TRANSFER("Approve the pending transfer request"),
    OTHER("Something else"),
}

object VoiceCommand : JevQuery() {
    val action by choice<BankAction>("What action is the user requesting?")
}

// The bar for acting rises with the cost of being wrong.
suspend fun handleVoiceCommand(
    jev: JevApi,
    utterance: String,
): String {
    val action = jev.ask(VoiceCommand, state = utterance)[VoiceCommand.action]
    return when {
        action.confidence < 0.6 -> "route to a support agent"

        // Cheap to get wrong: the floor is enough.
        action.choice == BankAction.CHECK_BALANCE -> "read the balance"

        // Costly to get wrong: act alone only when very sure.
        action.choice == BankAction.APPROVE_TRANSFER && action.confidence > 0.85 -> "approve the transfer"

        action.choice == BankAction.APPROVE_TRANSFER -> "ask the user to confirm first"

        else -> "route to a support agent"
    }
}
// --8<-- [end:risk-scaled]

// --8<-- [start:fallback]
// With a hierarchy of labels, fall back to the parent when the fine-grained answer is uncertain.
enum class Industry(
    val division: String,
) {
    PHARMACEUTICALS("manufacturing"),
    SEMICONDUCTORS("manufacturing"),
    LIFE_INSURANCE("finance"),
    BANKING("finance"),
    SOFTWARE("services"),
}

object IndustryQuery : JevQuery() {
    val industry by choice<Industry>(
        "Which industry does this company operate in? Judge its own operations as this filing describes them.",
    )
}

suspend fun classify(
    jev: JevApi,
    filing: String,
): String {
    val industry = jev.ask(IndustryQuery, state = filing)[IndustryQuery.industry]
    return if (industry.confidence >= 0.9) industry.choice.name else industry.choice.division
}
// --8<-- [end:fallback]

// --8<-- [start:weakest-link]
// A decision built from several answers is only as sure as its least certain part.
fun combinedConfidence(vararg parts: ChoiceAnswer<*>): Double = parts.minOf { it.confidence }
// --8<-- [end:weakest-link]
