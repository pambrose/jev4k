package com.pambrose.jev4k

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToInt

/** A typed answer to one question. */
sealed interface Answer

/**
 * The answer to a Noul: [noul][NoulAnswer.noul] is the probability, from 0 to 1, that the answer is
 * yes. Nouls have no confidence.
 */
data class NoulAnswer(
    val noul: Double,
) : Answer {
    /** True when the probability of yes exceeds [threshold]. */
    fun isTrue(threshold: Double = 0.5): Boolean = noul > threshold

    /**
     * Splits the probability into no / uncertain / yes, e.g. to send the uncertain middle to a person.
     * The defaults are the TypeSafe cookbook's illustrative band; tune them on your own data.
     */
    fun band(
        no: Double = 0.30,
        yes: Double = 0.70,
    ): NoulBand =
        when {
            noul < no -> NoulBand.NO
            noul > yes -> NoulBand.YES
            else -> NoulBand.UNCERTAIN
        }
}

enum class NoulBand { NO, UNCERTAIN, YES }

/**
 * The answer to a Choice: the highest-probability option, the probability of every option, and a
 * [confidence][ChoiceAnswer.confidence] from 0 to 1 derived from how concentrated
 * [probabilities][ChoiceAnswer.probabilities] is. Confidence is not the winner's probability.
 */
data class ChoiceAnswer<K : Any>(
    val choice: K,
    val probabilities: Map<K, Double>,
    val confidence: Double,
) : Answer {
    /** The largest probability of any option. */
    val topProbability: Double get() = probabilities.values.maxOrNull() ?: 0.0

    fun probability(option: K): Double = probabilities[option] ?: 0.0

    /** Options from most to least likely. */
    fun ranked(): List<Pair<K, Double>> = probabilities.entries.sortedByDescending { it.value }.map { it.toPair() }
}

/**
 * The answer to a Score: [score][ScoreAnswer.score] is the probability-weighted level (`Σ level × p`),
 * so it can fall between levels. [probabilities][ScoreAnswer.probabilities] and
 * [legend][ScoreAnswer.legend] are keyed by level number; the legend holds the level entries that
 * were sent. [levelCount][ScoreAnswer.levelCount] is the number of levels asked about.
 */
data class ScoreAnswer(
    val score: Double,
    val probabilities: Map<Int, Double>,
    val legend: Map<Int, JsonElement>,
    val confidence: Double,
    val levelCount: Int,
) : Answer {
    /**
     * [score][ScoreAnswer.score] scaled to 0..1 by the top level number, so Scores with different level counts can be
     * weighted together.
     */
    val normalized: Double get() = if (levelCount > 1) score / (levelCount - 1) else 0.0

    /** [score][ScoreAnswer.score] rounded to the nearest level. */
    val nearestLevel: Int get() = score.roundToInt().coerceIn(0, maxOf(levelCount - 1, 0))

    /** The level with the highest probability, or [nearestLevel] when no probabilities were returned. */
    val mostLikelyLevel: Int get() = probabilities.maxByOrNull { it.value }?.key ?: nearestLevel

    /** The level's description when it was sent as plain text, otherwise null. */
    fun legendText(level: Int): String? = (legend[level] as? JsonPrimitive)?.takeIf { it.isString }?.content
}

/**
 * An answer of a type this version of jev4k doesn't know; [raw][UnknownAnswer.raw] is the answer
 * object as received.
 */
data class UnknownAnswer(
    val type: String?,
    val raw: JsonObject,
) : Answer

/** Token counts reported by the API; either may be absent. */
data class Usage(
    val inputTokens: Long?,
    val outputTokens: Long?,
)

/** One entry from `GET /v1/models`. */
data class ModelInfo(
    val name: String,
    val description: String?,
    val releaseDate: String?,
)

/** Raised by a question's decoder when the server's answer doesn't fit the question that was asked. */
internal class AnswerDecodingException(
    message: String,
) : RuntimeException(message)

internal val Answer.typeName: String
    get() = when (this) {
        is NoulAnswer -> "noul"
        is ChoiceAnswer<*> -> "choice"
        is ScoreAnswer -> "score"
        is UnknownAnswer -> type ?: "unknown"
    }

private fun mismatch(
    expected: String,
    answer: Answer,
): Nothing = throw AnswerDecodingException("expected a $expected answer but got ${answer.typeName}")

internal fun decodeNoul(answer: Answer): NoulAnswer = answer as? NoulAnswer ?: mismatch("noul", answer)

internal fun decodeScore(answer: Answer): ScoreAnswer = answer as? ScoreAnswer ?: mismatch("score", answer)

@Suppress("UNCHECKED_CAST")
internal fun decodeChoice(answer: Answer): ChoiceAnswer<String> =
    answer as? ChoiceAnswer<String> ?: mismatch("choice", answer)

/** The option key an enum constant is sent as. */
internal fun enumOptionKey(constant: Enum<*>): String = (constant as? JevOption)?.optionKey ?: constant.name

/** Maps a string-keyed choice onto enum constants, keeping the enum's declaration order. */
internal fun <E : Enum<E>> decodeEnumChoice(
    answer: Answer,
    byKey: Map<String, E>,
): ChoiceAnswer<E> {
    val raw = decodeChoice(answer)
    val choice = byKey[raw.choice]
        ?: throw AnswerDecodingException("unknown option '${raw.choice}'; expected one of ${byKey.keys}")
    val probabilities = byKey.mapNotNull { (key, constant) -> raw.probabilities[key]?.let { constant to it } }
    return ChoiceAnswer(choice, probabilities.toMap(), raw.confidence)
}
