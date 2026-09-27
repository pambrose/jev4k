package website

import com.pambrose.jev4k.JevApi
import com.pambrose.jev4k.JevQuery
import com.pambrose.jev4k.ask
import com.pambrose.jev4k.query

// --8<-- [start:broad]
// Too broad: one answer hides several judgments you can't inspect or tune.
object SpamBroad : JevQuery() {
    val spam by noul("Is this message spam?")
}
// --8<-- [end:broad]

// --8<-- [start:atomic]
// Atomic: each question is a snap judgment; code combines them.
object SpamSignals : JevQuery() {
    val requestsCredentials by noul("Does `message.body` ask the recipient for a password or other login credential?")
    val unexpectedReward by noul("Does `message.body` claim the recipient received an unexpected prize or payment?")
    val timePressure by noul("Does `message.subject` or `message.body` pressure the recipient to act quickly?")
    val senderMismatch by noul(
        "Does the organization in `message.sender.display_name` conflict with the domain in `message.sender.email`?",
    )
}
// --8<-- [end:atomic]

// --8<-- [start:levels]
object LevelWording : JevQuery() {
    // Bad: numbers and degrees give the model nothing to match against.
    val vague by score("Rate severity from 0 to 2, where 2 is worst") {
        levels("0", "1", "2")
    }

    // Good: each level describes a situation that can be recognized on its own.
    val concrete by score("How severe is the reported issue?") {
        level("Cosmetic; no impact to functionality")
        level("Broken or degraded feature, but a workaround exists")
        level("Blocking issue; no workaround exists")
    }
}
// --8<-- [end:levels]

// --8<-- [start:degree]
object PythonSkill : JevQuery() {
    // A Noul answers "is this true?": 0.5 means "equally likely", not "medium skill".
    val usedAtWork by noul("Does the resume state that the candidate has used Python at work?")

    // A Score measures degree along described levels.
    val depth by score("How much Python experience does the resume show?") {
        levels("No experience", "Some familiarity", "Daily use", "Deep expertise")
    }
}
// --8<-- [end:degree]

// --8<-- [start:math-in-code]
// Jev reads meaning, not numbers: let it pick out the value, then do the arithmetic in code.
suspend fun overBudget(
    jev: JevApi,
    invoice: String,
    budgetUsd: Double,
): Boolean {
    val amounts = Regex("""\$([\d,]+\.\d{2})""").findAll(invoice).map { it.groupValues[1] }.distinct().toList()
    if (amounts.isEmpty()) return false
    val total =
        jev
            .query(state = invoice) {
                choice("total", "Which amount is the total the customer must pay?") { amounts.forEach { option(it) } }
            }.choice("total")
            .choice
    return total.replace(",", "").toDouble() > budgetUsd
}
// --8<-- [end:math-in-code]

// --8<-- [start:literal]
object RefundLiteral : JevQuery() {
    // Read literally, "Is this about refunds?" is also true for "What's your refund policy?".
    val aboutRefunds by noul("Is this message about refunds?")

    // State the exact condition you mean.
    val requestsRefund by noul("Does the customer explicitly ask for their own money back for a specific purchase?")
}

suspend fun literalCheck(
    jev: JevApi,
    message: String,
) = jev.ask(RefundLiteral, state = message)[RefundLiteral.requestsRefund].isTrue()
// --8<-- [end:literal]
