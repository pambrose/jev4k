package website

import com.pambrose.jev4k.JevApi
import com.pambrose.jev4k.JevQuery
import com.pambrose.jev4k.ask
import com.pambrose.jev4k.entry
import com.pambrose.jev4k.query
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

suspend fun scoreBasic(jev: JevApi) {
    // --8<-- [start:basic]
    val result =
        jev.query(state = "The export button crashes the settings page in Safari. It works in Chrome.") {
            score("severity", "How severe is the reported issue?") {
                level("Cosmetic; no impact to functionality")                // level 0
                level("Broken or degraded feature, but a workaround exists") // level 1
                level("Blocking issue; no workaround exists")                // level 2
            }
        }
    println(result.score("severity").score) // e.g. 1.3: mostly level 1, some weight on level 2
    // --8<-- [end:basic]

    // --8<-- [start:reading]
    val severity = result.score("severity")

    severity.score           // 1.3 = 0 x 0.0 + 1 x 0.7 + 2 x 0.3
    severity.probabilities   // {0=0.0, 1=0.7, 2=0.3}
    severity.confidence      // 0.54: probability is split between two levels
    severity.levelCount      // 3
    severity.normalized      // 0.65 = score / (levelCount - 1), always 0..1
    severity.nearestLevel    // 1: the score rounded to a level
    severity.mostLikelyLevel // 1: the level with the highest probability
    severity.legendText(2)   // "Blocking issue; no workaround exists"
    // --8<-- [end:reading]
}

suspend fun scoreStructuredLevels(jev: JevApi) {
    // --8<-- [start:structured-levels]
    // Give each level a description plus a few example situations. Use the same field names on every level.
    val result =
        jev.query(state = "Export to PDF fails with a spinner that never finishes. CSV export still works.") {
            score("severity", "How severe is the reported issue?") {
                level(
                    entry(
                        "what" to "Cosmetic; no impact to functionality",
                        "examples" to listOf("typo in a label", "misaligned icon"),
                    ),
                )
                level(
                    entry(
                        "what" to "Broken or degraded feature, but a workaround exists",
                        "examples" to listOf("export fails in one browser but works in another"),
                    ),
                )
                level(
                    entry(
                        "what" to "Blocking issue; no workaround exists",
                        "examples" to listOf("cannot log in", "data loss"),
                    ),
                )
            }
        }
    // --8<-- [end:structured-levels]
    println(result.score("severity"))
}

// --8<-- [start:composite]
object TicketPriority : JevQuery() {
    val severity by score("How severe is the reported issue?") {
        level("Cosmetic; no impact to functionality")
        level("Broken or degraded feature, but a workaround exists")
        level("Blocking issue; no workaround exists")
    }
    val frustration by score("How frustrated is the customer?") {
        level("Calm, just stating facts")
        level("Frustrated but civil")
        level("Very angry, strong language or threatening to leave")
    }
    val reportQuality by score("How much does the report give an engineer to work with?") {
        level("No detail; just says something is broken")
        level("Names the feature but no steps or environment")
        level("Steps to reproduce or environment, but not both")
        level("Steps to reproduce and environment")
    }
}

// Weights live in code: change them, not the questions, when the ranking doesn't match your team's judgment.
suspend fun priority(
    jev: JevApi,
    ticket: String,
): Double {
    val result = jev.ask(TicketPriority, state = ticket)
    return 0.6 * result[TicketPriority.severity].normalized +
        0.3 * result[TicketPriority.frustration].normalized +
        0.1 * result[TicketPriority.reportQuality].normalized
}
// --8<-- [end:composite]

// --8<-- [start:levels-as-actions]
enum class LinkAction { LEAVE_UNLINKED, CURATOR_QUEUE, MERGE }

// When the ordered outcomes are actions, write one level per action and round to the nearest level.
suspend fun linkDecision(
    jev: JevApi,
    productA: String,
    productB: String,
): LinkAction {
    val state =
        buildJsonObject {
            put("entity_a", productA)
            put("entity_b", productB)
        }
    val result =
        jev.query(state = state) {
            score("link", "How do the two entity descriptions relate as products?") {
                level("They describe two different products.")
                level("They describe closely related products that may or may not be the same one.")
                level("They describe one and the same product.")
            }
        }
    return LinkAction.entries[result.score("link").nearestLevel]
}
// --8<-- [end:levels-as-actions]
