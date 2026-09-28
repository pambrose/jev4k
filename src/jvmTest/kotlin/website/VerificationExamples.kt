package website

import com.pambrose.jev4k.JevApi
import com.pambrose.jev4k.entry
import com.pambrose.jev4k.query
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// --8<-- [start:citation]
enum class Verdict { VERIFIED, CONTRADICTED, UNSUPPORTED, FABRICATED }

data class CitationCheck(
    val verdict: Verdict,
    val needsReview: Boolean,
)

suspend fun checkCitation(
    jev: JevApi,
    claim: String,
    quote: String,
    sections: List<String>,
): CitationCheck {
    // Deterministic first: a quote that isn't in the source is fabricated, no model needed.
    val section =
        sections.firstOrNull { it.contains(quote) }
            ?: return CitationCheck(Verdict.FABRICATED, needsReview = false)

    // Then judge the quote in context: a verbatim quote can still fail to support the claim.
    val state =
        buildJsonObject {
            put("claim", claim)
            put("section", section)
        }
    val relation =
        jev
            .query(state = state) {
                choice("relation", "How does the section relate to the claim?") {
                    "supports" means "The section states the claim or directly implies that it is true"
                    "contradicts" means "The section states the opposite of the claim or implies it is false"
                    "says_nothing" means "The section does not address what the claim asserts, either way"
                }
            }.choice("relation")

    val verdict =
        when (relation.choice) {
            "supports" -> Verdict.VERIFIED
            "contradicts" -> Verdict.CONTRADICTED
            else -> Verdict.UNSUPPORTED
        }
    return CitationCheck(verdict, needsReview = relation.confidence < 0.8)
}
// --8<-- [end:citation]

// --8<-- [start:extraction-gate]
// Every check is phrased so TRUE means "something is wrong", with explicit criteria for both answers.
class FieldCheck(
    val question: String,
    val whenTrue: String,
    val whenFalse: String,
)

val valueChecks =
    mapOf(
        "hallucinated" to
            FieldCheck(
                "Is the `extracted_field` unsupported by, or absent from, the source text?",
                "The `extracted_field` is not supported by, or is absent from, the source text",
                "The `extracted_field` is supported by the source text",
            ),
        "off_target" to
            FieldCheck(
                "Does the source text fail to genuinely report this field, so the value came from incidental text?",
                "The source does not genuinely provide this field; the value was pulled from incidental text",
                "The source genuinely reports this field",
            ),
        "format_violation" to
            FieldCheck(
                "Does the `extracted_field` violate the format implied by the field's name?",
                "The `extracted_field` violates the implied format",
                "The `extracted_field` satisfies the implied format",
            ),
    )

// An empty field has no content to check, only whether leaving it empty was right.
val absenceChecks =
    mapOf(
        "absence_wrong" to
            FieldCheck(
                "The `extracted_field` is empty. Does the source text contain the information this field describes?",
                "A value was wrongly omitted",
                "Returning nothing is correct",
            ),
    )

// Verify a cheap model's extraction field by field; escalate only when a check fires.
suspend fun needsEscalation(
    jev: JevApi,
    sourceText: String,
    extracted: Map<String, String>,
): Boolean {
    // Nothing extracted is nothing to vouch for, so it goes to the stronger model: the safe direction.
    if (extracted.isEmpty()) return true
    val result =
        jev.query(state = sourceText) {
            for ((field, value) in extracted) {
                val checks = if (value.isBlank()) absenceChecks else valueChecks
                for ((name, check) in checks) {
                    val instructions = entry("field" to field, "extracted_field" to value, "question" to check.question)
                    noul("$field::$name", instructions) {
                        whenTrue(check.whenTrue)
                        whenFalse(check.whenFalse)
                    }
                }
            }
        }
    // Max, not mean: one confident red flag is enough to escalate.
    return result.nouls.values.maxOf { it.noul } > 0.7
}
// --8<-- [end:extraction-gate]

// --8<-- [start:tool-trace]
// Check an agent's tool calls with narrow questions rather than one "is this trace correct?".
suspend fun toolCallProblems(
    jev: JevApi,
    traceJson: JsonObject,
): List<String> {
    val result =
        jev.query(state = traceJson) {
            noul("wrong_tool", "Is `trace.tool_calls[0].name` an inappropriate tool for `request.text`?")
            noul("schema_violation", "Do `trace.tool_calls[0].arguments` violate the tool's `parameters` schema?")
            noul("unit_mismatch", "Does `trace.tool_calls[0].arguments.unit` differ from `request.unit`?")
            noul("date_mismatch", "Does `trace.tool_calls[0].arguments.date` differ from `request.date`?")
        }
    return result.nouls.filterValues { it.isTrue(0.7) }.keys.toList()
}
// --8<-- [end:tool-trace]
