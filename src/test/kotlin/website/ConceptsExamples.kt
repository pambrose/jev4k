package website

import com.pambrose.jev4k.JevApi
import com.pambrose.jev4k.query

suspend fun conceptsOneRequest(jev: JevApi) {
    // --8<-- [start:one-request]
    val result =
        jev.query(state = "The export button crashes the settings page in Safari. It works in Chrome.") {
            // Every question below is answered in parallel, in one request, against the same state.
            noul("is_bug", "Does the message report something that is broken?")
            noul("has_workaround", "Does the message mention a way to get the task done anyway?")
            choice("area", "Which part of the product is affected?") {
                options("settings", "billing", "reports", "other")
            }
            score("severity", "How severe is the reported issue?") {
                level("Cosmetic; no impact to functionality")
                level("Broken or degraded feature, but a workaround exists")
                level("Blocking issue; no workaround exists")
            }
        }
    // --8<-- [end:one-request]

    // --8<-- [start:answer-shapes]
    val isBug = result.noul("is_bug") // NoulAnswer
    println(isBug.noul)               // probability of yes, 0..1

    val area = result.choice("area") // ChoiceAnswer<String>
    println(area.choice)             // the most likely option, e.g. "settings"
    println(area.probabilities)      // every option -> its probability, summing to 1
    println(area.confidence)         // how concentrated those probabilities are, 0..1

    val severity = result.score("severity") // ScoreAnswer
    println(severity.score)                 // probability-weighted level, e.g. 1.3 (between levels 1 and 2)
    println(severity.probabilities)         // level number -> probability
    println(severity.confidence)
    // --8<-- [end:answer-shapes]
}
