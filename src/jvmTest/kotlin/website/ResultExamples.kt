package website

import com.pambrose.jev4k.JevApi
import com.pambrose.jev4k.JevResponseValidationException
import com.pambrose.jev4k.JevResult
import com.pambrose.jev4k.ask
import kotlinx.serialization.json.jsonObject

suspend fun resultTyped(
    jev: JevApi,
    ticket: String,
) {
    // --8<-- [start:typed]
    val result = jev.ask(Triage, state = ticket)

    result[Triage.urgent].noul       // Double
    result[Triage.team].choice       // Team
    result[Triage.frustration].score // Double
    // --8<-- [end:typed]
}

fun resultById(result: JevResult) {
    // --8<-- [start:by-id]
    result.noul("urgent").noul
    result.choice("team").choice           // String: the option key
    result.enumChoice<Team>("team").choice // Team
    result.score("frustration").score
    // --8<-- [end:by-id]
}

fun resultCollections(result: JevResult) {
    // --8<-- [start:collections]
    result.nouls.forEach { (id, answer) -> println("$id: ${answer.noul}") }
    result.choices.forEach { (id, answer) -> println("$id: ${answer.choice} (${answer.confidence})") }
    result.scores.forEach { (id, answer) -> println("$id: ${answer.score}") }

    result.answers       // every answer by id, in the order the questions were asked
    result.questions.ids // the ids that were asked
    // --8<-- [end:collections]
}

fun resultMetadata(result: JevResult) {
    // --8<-- [start:metadata]
    println("model=${result.model}")              // the version that answered, as reported by the API
    println("requested=${result.requestedModel}") // what was sent, e.g. "jev-latest"
    println("tokens in=${result.usage.inputTokens} out=${result.usage.outputTokens}")
    println("request id=${result.requestId}") // quote this when contacting TypeSafe support
    // --8<-- [end:metadata]
}

fun resultHelpers(result: JevResult) {
    // --8<-- [start:helpers]
    val urgent = result[Triage.urgent]
    urgent.isTrue()                // noul > 0.5
    urgent.isTrue(threshold = 0.8) // noul > 0.8
    urgent.band()                  // NoulBand.NO (< 0.30), UNCERTAIN, or YES (> 0.70)

    val team = result[Triage.team]
    team.topProbability          // the largest option probability
    team.probability(Team.SALES) // any option's probability, 0.0 if absent
    team.ranked()                // [(TECHNICAL, 0.85), (BILLING, 0.08), (SALES, 0.07)]

    val frustration = result[Triage.frustration]
    frustration.normalized      // score / (levels - 1): 0..1 whatever the level count
    frustration.nearestLevel    // score rounded to a level
    frustration.mostLikelyLevel // the level with the highest probability
    frustration.legendText(2)   // the description of level 2
    // --8<-- [end:helpers]
}

fun resultRaw(result: JevResult) {
    // --8<-- [start:raw]
    // The response body exactly as received, e.g. for logging or fields jev4k doesn't model yet.
    val rawAnswers = result.raw["answers"]?.jsonObject
    // --8<-- [end:raw]
    println(rawAnswers)
}

fun resultMissing(result: JevResult) {
    // --8<-- [start:missing]
    try {
        result[Triage.team]
    } catch (e: JevResponseValidationException) {
        // The server didn't return a usable answer for this question.
        println("bad answer at ${e.fieldPath}, request ${e.requestId}")
    }
    // --8<-- [end:missing]
}
