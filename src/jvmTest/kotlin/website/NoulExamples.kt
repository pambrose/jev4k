package website

import com.pambrose.jev4k.JevApi
import com.pambrose.jev4k.JevQuery
import com.pambrose.jev4k.NoulBand
import com.pambrose.jev4k.ask
import com.pambrose.jev4k.query
import kotlinx.serialization.json.addAll
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray

suspend fun noulBasic(jev: JevApi) {
    // --8<-- [start:basic]
    val result =
        jev.query(state = "I have asked three times now. Can I please just talk to a real person?") {
            noul("wants_human", "Is the customer asking for a human agent?")
        }
    val wantsHuman = result.noul("wants_human").noul // e.g. 0.99
    // --8<-- [end:basic]
    println(wantsHuman)
}

suspend fun noulCriteria(jev: JevApi) {
    // --8<-- [start:criteria]
    val result =
        jev.query(state = "I have asked three times now. Can I please just talk to a real person?") {
            noul("repeat_contact", "Has the customer contacted support about this before?") {
                whenTrue("Mentions a prior attempt, ticket, or that they have asked before")
                whenFalse("No sign of any previous contact")
            }
        }
    // --8<-- [end:criteria]
    println(result.noul("repeat_contact").noul)
}

suspend fun noulStatement(jev: JevApi) {
    // --8<-- [start:statement]
    // A Noul can also be phrased as a statement to judge; a value near 1 means "true".
    val result =
        jev.query(state = "My card was charged twice for order A-104.") {
            noul("refund_statement", "The customer is requesting a refund.")
            noul("refund_question", "Is the customer requesting a refund?")
        }
    // --8<-- [end:statement]
    println(result.nouls)
}

suspend fun noulThresholds(jev: JevApi) {
    val result =
        jev.query(state = "Our payment page has been down since this morning.") {
            noul("urgent", "Does this message convey urgency?")
        }
    // --8<-- [start:thresholds]
    val urgent = result.noul("urgent")

    // A hard decision: the probability of yes exceeds a threshold you choose.
    if (urgent.isTrue(threshold = 0.7)) println("page the on-call engineer")

    // Three-way decision: send the uncertain middle to a person instead of guessing.
    when (urgent.band(no = 0.3, yes = 0.7)) {
        NoulBand.YES -> println("escalate")
        NoulBand.NO -> println("normal queue")
        NoulBand.UNCERTAIN -> println("human review")
    }
    // --8<-- [end:thresholds]
}

// --8<-- [start:count-in-code]
// Jev doesn't count reliably. Ask one Noul per item and count the answers in code.
suspend fun countFruits(
    jev: JevApi,
    items: List<String>,
): Int {
    val state = buildJsonObject { putJsonArray("items") { addAll(items) } }
    val result =
        jev.query(state = state) {
            items.indices.forEach { i -> noul("item_$i", "Is `items[$i]` the name of a fruit?") }
        }
    return items.indices.count { i -> result.noul("item_$i").isTrue() }
}
// --8<-- [end:count-in-code]

// --8<-- [start:typed]
object RefundSignals : JevQuery() {
    val requested by noul("Does the customer explicitly ask for a refund or credit?") {
        whenTrue("Directly asks for money back or an account credit")
        whenFalse("A complaint or billing question with no requested remedy")
    }
    val duplicateCharge by noul("Does the message describe being charged more than once for the same thing?")
}

suspend fun needsRefundReview(
    jev: JevApi,
    message: String,
): Boolean {
    val result = jev.ask(RefundSignals, state = message)
    return result[RefundSignals.requested].isTrue() || result[RefundSignals.duplicateCharge].isTrue(0.8)
}
// --8<-- [end:typed]
