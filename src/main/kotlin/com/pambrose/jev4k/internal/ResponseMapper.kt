package com.pambrose.jev4k.internal

import com.pambrose.jev4k.Answer
import com.pambrose.jev4k.ChoiceAnswer
import com.pambrose.jev4k.ChoiceQuestion
import com.pambrose.jev4k.JevResponseValidationException
import com.pambrose.jev4k.JevResult
import com.pambrose.jev4k.ModelInfo
import com.pambrose.jev4k.NoulAnswer
import com.pambrose.jev4k.QuestionSet
import com.pambrose.jev4k.Question
import com.pambrose.jev4k.ScoreAnswer
import com.pambrose.jev4k.ScoreQuestion
import com.pambrose.jev4k.UnknownAnswer
import com.pambrose.jev4k.Usage
import com.pambrose.jev4k.typeName
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Turns a `/v1/systemone` body into a [JevResult]. Known answers missing required fields fail here,
 * with a field path; answers that are absent fail later, when read.
 */
internal fun mapSystemOne(
    body: JsonObject,
    requestedModel: String,
    questions: QuestionSet,
    requestId: String?,
    endpoint: String,
): JevResult {
    val reader = BodyReader(body, requestId, endpoint)
    val answersJson = reader.optionalObject(body, "answers", "answers") ?: JsonObject(emptyMap())
    // Declared questions first, in request order, then anything extra the server sent.
    val ids = questions.ids.filter { it in answersJson } + answersJson.keys.filter { questions[it] == null }
    val answers = ids.associateWith { reader.answer(it, answersJson.getValue(it), questions[it]?.question) }

    // Metadata degrades where an answer would fail: a quirk in the token counters or the model name must not
    // throw away answers that parsed cleanly. Both are documented as optional.
    val usage = body["usage"] as? JsonObject
    return JevResult(
        model = (body["model"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: requestedModel,
        requestedModel = requestedModel,
        usage = Usage(
            inputTokens = usage?.get("input_tokens").asLongOrNull(),
            outputTokens = usage?.get("output_tokens").asLongOrNull(),
        ),
        requestId = requestId,
        questions = questions,
        answers = answers,
        raw = body,
        endpoint = endpoint,
    )
}

/** Turns a `/v1/models` body into [ModelInfo]s. */
internal fun mapModels(
    body: JsonObject,
    requestId: String?,
    endpoint: String,
): List<ModelInfo> {
    val reader = BodyReader(body, requestId, endpoint)
    val models = body["models"] as? JsonArray ?: reader.fail("models", "expected an array")
    return models.mapIndexed { i, element ->
        val path = "models[$i]"
        val model = element as? JsonObject ?: reader.fail(path, "expected an object")
        ModelInfo(
            name = reader.optionalString(model, "name", "$path.name") ?: reader.fail("$path.name", "missing"),
            description = reader.optionalString(model, "description", "$path.description"),
            releaseDate = reader.optionalString(model, "release_date", "$path.release_date"),
        )
    }
}

private class BodyReader(
    val body: JsonObject,
    val requestId: String?,
    val endpoint: String,
) {
    fun fail(
        path: String,
        detail: String,
    ): Nothing = throw JevResponseValidationException(detail, path, body.toString(), requestId, endpoint)

    fun answer(
        id: String,
        element: JsonElement,
        question: Question?,
    ): Answer {
        val path = "answers.$id"
        val obj = element as? JsonObject ?: fail(path, "expected an object")
        return when (val type = optionalString(obj, "type", "$path.type") ?: question?.typeName) {
            "noul" -> {
                NoulAnswer(requiredDouble(obj, "noul", path))
            }

            "choice" -> {
                ChoiceAnswer(
                    choice = optionalString(obj, "choice", "$path.choice") ?: fail("$path.choice", "missing"),
                    probabilities = inDeclaredOrder(doubles(obj, path), (question as? ChoiceQuestion)?.options?.keys),
                    confidence = requiredDouble(obj, "confidence", path),
                )
            }

            "score" -> {
                val probabilities = byLevel(doubles(obj, path), "$path.probabilities")
                val legend = byLevel(optionalObject(obj, "legend", "$path.legend").orEmpty(), "$path.legend")
                val levels = (question as? ScoreQuestion)?.levels
                ScoreAnswer(
                    score = requiredDouble(obj, "score", path),
                    probabilities = probabilities,
                    legend =
                        legend.ifEmpty {
                            levels?.withIndex()?.associate { (index, level) -> index to level }.orEmpty()
                        },
                    confidence = requiredDouble(obj, "confidence", path),
                    levelCount = levels?.size ?: maxOf(legend.size, probabilities.size),
                )
            }

            else -> {
                UnknownAnswer(type, obj)
            }
        }
    }

    fun optionalObject(
        parent: JsonObject,
        key: String,
        path: String,
    ): JsonObject? =
        when (val value = parent[key]) {
            null, JsonNull -> null
            is JsonObject -> value
            else -> fail(path, "expected an object")
        }

    fun optionalString(
        parent: JsonObject,
        key: String,
        path: String,
    ): String? =
        when (val value = parent[key]) {
            null, JsonNull -> null
            is JsonPrimitive if value.isString -> value.content
            else -> fail(path, "expected a string")
        }

    private fun number(
        value: JsonElement?,
        path: String,
    ): Double = (value as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull ?: fail(path, "expected a number")

    private fun requiredDouble(
        obj: JsonObject,
        key: String,
        path: String,
    ): Double =
        obj[key]?.takeUnless { it == JsonNull }?.let { number(it, "$path.$key") } ?: fail("$path.$key", "missing")

    private fun doubles(
        obj: JsonObject,
        path: String,
    ): Map<String, Double> =
        optionalObject(obj, "probabilities", "$path.probabilities").orEmpty()
            .mapValues { (key, value) -> number(value, "$path.probabilities.$key") }

    private fun <V> byLevel(
        map: Map<String, V>,
        path: String,
    ): Map<Int, V> =
        map.entries
            .map { (key, value) -> (key.toIntOrNull() ?: fail("$path.$key", "expected an integer level")) to value }
            .sortedBy { it.first }
            .toMap()

    private fun inDeclaredOrder(
        probabilities: Map<String, Double>,
        declared: Set<String>?,
    ): Map<String, Double> {
        if (declared == null) return probabilities
        return buildMap {
            declared.forEach { key -> probabilities[key]?.let { put(key, it) } }
            probabilities.forEach { (key, value) -> putIfAbsent(key, value) }
        }
    }
}

/** A token count, or null for anything that isn't a plain integer. Used only for optional metadata. */
private fun JsonElement?.asLongOrNull(): Long? = (this as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
