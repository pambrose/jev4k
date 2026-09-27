package website

import com.pambrose.jev4k.JevApi
import com.pambrose.jev4k.ask
import com.pambrose.jev4k.query
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

suspend fun stateString(jev: JevApi) {
    // --8<-- [start:string]
    // Plain text: a message, a document, a transcript.
    val result = jev.ask(Triage, state = "My card was charged twice for order A-104.")
    // --8<-- [end:string]
    println(result[Triage.team])
}

suspend fun stateJson(jev: JevApi) {
    // --8<-- [start:json]
    // Named parts keep related context together and let questions point at a specific part.
    val state =
        buildJsonObject {
            putJsonObject("ticket") {
                put("subject", "Duplicate charge")
                putJsonArray("messages") {
                    addJsonObject {
                        put("from", "customer")
                        put("text", "I was charged twice for order A-104. Please refund the duplicate.")
                    }
                    addJsonObject {
                        put("from", "support")
                        put("text", "We are checking the charges.")
                    }
                }
            }
            putJsonObject("order") {
                put("id", "A-104")
                putJsonArray("charges") {
                    add("49.00 USD captured")
                    add("49.00 USD captured")
                }
            }
            put("refund_policy", "Duplicate charges are eligible for a refund.")
        }

    val result =
        jev.query(state = state) {
            noul("refund_requested", "Does `ticket.messages[0].text` request a refund?")
            noul("policy_supports_refund", "Does `refund_policy` support the refund requested, given `order.charges`?")
        }
    // --8<-- [end:json]
    println(result.nouls)
}

// --8<-- [start:serializable]
@Serializable
data class Order(
    val id: String,
    val status: String = "open", // fields equal to their default are still sent
    val items: List<String> = emptyList(),
)

@Serializable
data class RefundCase(
    val message: String,
    val order: Order,
    val policy: String,
)

suspend fun refundSupported(
    jev: JevApi,
    case: RefundCase,
): Boolean {
    val result =
        jev.query(state = case) {
            noul("supported", "Does `policy` support the refund requested in `message` for `order`?")
        }
    return result.noul("supported").isTrue(threshold = 0.8)
}
// --8<-- [end:serializable]

// --8<-- [start:focused]
// Send only what the questions need. Unrelated detail lowers accuracy.
suspend fun triageFromTicket(
    jev: JevApi,
    ticket: SupportTicket,
): Team {
    val focused = "${ticket.subject}\n\n${ticket.body}" // leave out account data the question doesn't use
    return jev.ask(Triage, state = focused)[Triage.team].choice
}
// --8<-- [end:focused]
