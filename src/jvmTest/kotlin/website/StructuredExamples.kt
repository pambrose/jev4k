package website

import com.pambrose.jev4k.JevApi
import com.pambrose.jev4k.entry
import com.pambrose.jev4k.jsonEntry
import com.pambrose.jev4k.jsonOf
import com.pambrose.jev4k.query
import com.pambrose.jev4k.rubric
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

suspend fun structuredInstructions(jev: JevApi) {
    // --8<-- [start:entry-instructions]
    val result =
        jev.query(state = "I ordered the standing desk two weeks ago and tracking still says label created.") {
            choice(
                "department",
                entry(
                    "question" to "Which team should handle this message?",
                    "focus" to "Classify the customer's primary request, not every topic mentioned.",
                ),
            ) {
                options("billing", "orders", "account")
            }
        }
    // --8<-- [end:entry-instructions]
    println(result.choice("department"))
}

suspend fun structuredRubric(jev: JevApi) {
    // --8<-- [start:rubric-options]
    // Contrastive descriptions sharpen the boundary between options that are easy to confuse.
    val result =
        jev.query(state = "I sent the shoes back a week ago. When do I get my money?") {
            choice("return_topic", "Which returns topic is the customer asking about?") {
                "return_policy" means
                    rubric(
                        what = "Whether and how an item can be returned",
                        notFor = "Progress of a return already sent",
                        examples = listOf("Can I return shoes I've worn once?", "How long do I have to return it?"),
                    )
                "return_status" means
                    rubric(
                        what = "Progress of a return already sent",
                        notFor = "Whether and how an item can be returned",
                        examples = listOf("Has my return arrived yet?", "When will my refund be paid?"),
                    )
            }
        }
    // --8<-- [end:rubric-options]
    println(result.choice("return_topic").choice)
}

suspend fun structuredField(jev: JevApi) {
    // --8<-- [start:field-object]
    // One `field` object describes the value being checked; each question refers to it by key.
    val invoiceNumber =
        entry("name" to "invoice_number", "type" to "string", "description" to "The identifier printed on the invoice.")
    val amountDue =
        entry("name" to "amount_due", "type" to "number", "unit" to "USD", "description" to "The total to be paid.")

    val state = buildJsonObject { put("source_text", "Invoice #4471 issued March 3, 2026 for $12,840.00, net 30.") }
    val result =
        jev.query(state = state) {
            noul(
                "invoice_number_ok",
                entry(
                    "field" to invoiceNumber,
                    "extracted_value" to "4471",
                    "question" to "Does `extracted_value` match the `field` as it appears in `source_text`?",
                ),
            )
            score("amount_band", entry("field" to amountDue, "question" to "How large is `field` in `source_text`?")) {
                levels("Under $1,000", "$1,000 to $10,000", "$10,000 to $100,000", "Over $100,000")
            }
        }
    // --8<-- [end:field-object]
    println(result.noul("invoice_number_ok"))
}

suspend fun structuredTaxonomy(jev: JevApi) {
    // --8<-- [start:taxonomy]
    // An option's description can be its whole subtree, so the model sees what lives under each branch.
    val result =
        jev.query(state = "32oz plastic bottle with a flip straw lid. Fits most bike cages.") {
            choice("department", "Which top-level department does this product belong to?") {
                "Sporting Goods" means
                    jsonOf(
                        mapOf(
                            "Cycling" to listOf("Bike Bottles & Cages", "Bike Lights", "Helmets"),
                            "Outdoor" to listOf("Tents", "Sleeping Bags", "Hydration Packs"),
                        ),
                    )
                "Home & Kitchen" means
                    jsonOf(
                        mapOf(
                            "Drinkware" to listOf("Water Bottles", "Travel Mugs"),
                            "Cookware" to listOf("Pots & Pans", "Bakeware"),
                        ),
                    )
            }
        }
    // --8<-- [end:taxonomy]
    println(result.choice("department").ranked())
}

suspend fun structuredNoulCriteria(jev: JevApi) {
    // --8<-- [start:noul-criteria]
    val result =
        jev.query(state = "Your Q3 bonus is ready. Reply with your login password so we can release the funds.") {
            noul(
                "requests_credentials",
                entry(
                    "question" to "Does the message ask the recipient to disclose a sensitive credential?",
                    "focus" to "Look for a request to send the credential itself, not to change or reset it.",
                ),
            ) {
                whenTrue(
                    rubric(
                        what = "Asks the recipient to reply with or send a password, PIN, or one-time code",
                        examples = listOf("Reply with your password", "Send us the 6-digit code"),
                    ),
                )
                whenFalse(
                    rubric(
                        what = "No sensitive credential is requested",
                        examples = listOf("Reset your password from the settings page"),
                    ),
                )
            }
        }
    // --8<-- [end:noul-criteria]
    println(result.noul("requests_credentials"))
}

// --8<-- [start:json-entry]
@Serializable
data class FieldSpec(
    val path: String,
    val type: String,
    val description: String,
    val required: Boolean = true,
)

suspend fun checkField(
    jev: JevApi,
    sourceText: String,
    spec: FieldSpec,
    value: String,
): Boolean {
    val result =
        jev.query(state = sourceText) {
            noul(
                "unsupported",
                entry(
                    "field_spec" to jsonEntry(spec), // any @Serializable value becomes structured JSON
                    "extracted_field" to value,
                    "main_question" to "Is the `extracted_field` unsupported by, or absent from, the source text?",
                ),
            )
        }
    return !result.noul("unsupported").isTrue(threshold = 0.7)
}
// --8<-- [end:json-entry]
