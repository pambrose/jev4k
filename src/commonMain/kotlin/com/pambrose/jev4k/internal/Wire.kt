package com.pambrose.jev4k.internal

import com.pambrose.jev4k.ChoiceQuestion
import com.pambrose.jev4k.NoulQuestion
import com.pambrose.jev4k.QuestionSet
import com.pambrose.jev4k.Question
import com.pambrose.jev4k.ScoreQuestion
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * JSON for the wire. `encodeDefaults = false` omits unset optional fields (e.g. Noul criteria),
 * while explicit nulls, such as an undescribed Choice option, are still written.
 */
internal val JevJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

/** JSON for caller-supplied values (state, entries): fields equal to their defaults are kept so the model sees them. */
@PublishedApi
internal val ValueJson = Json { encodeDefaults = true }

/** Body of `POST /v1/systemone`. */
@Serializable
internal data class SystemOneRequest(
    val state: JsonElement,
    val model: String,
    val questions: Map<String, WireQuestion>,
)

/** A question as sent; the class discriminator writes the `"type"` field. */
@Serializable
internal sealed class WireQuestion {
    @Serializable
    @SerialName("noul")
    data class Noul(
        val instructions: JsonElement,
        val criteria: NoulCriteria? = null,
    ) : WireQuestion()

    @Serializable
    @SerialName("choice")
    data class Choice(
        val instructions: JsonElement,
        val criteria: Map<String, JsonElement>,
    ) : WireQuestion()

    @Serializable
    @SerialName("score")
    data class Score(
        val instructions: JsonElement,
        val criteria: List<JsonElement>,
    ) : WireQuestion()
}

@Serializable
internal data class NoulCriteria(
    @SerialName("true") val whenTrue: JsonElement? = null,
    @SerialName("false") val whenFalse: JsonElement? = null,
)

internal fun QuestionSet.toWire(): Map<String, WireQuestion> = associate { it.id to it.question.toWire() }

private fun Question.toWire(): WireQuestion =
    when (this) {
        is NoulQuestion -> WireQuestion.Noul(
            instructions = instructions,
            criteria = if (whenTrue == null && whenFalse == null) null else NoulCriteria(whenTrue, whenFalse),
        )

        is ChoiceQuestion -> WireQuestion.Choice(instructions, options)

        is ScoreQuestion -> WireQuestion.Score(instructions, levels)
    }
