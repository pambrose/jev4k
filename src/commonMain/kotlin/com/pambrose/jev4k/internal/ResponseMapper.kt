package com.pambrose.jev4k.internal

import com.pambrose.jev4k.Answer
import com.pambrose.jev4k.ChoiceAnswer
import com.pambrose.jev4k.ChoiceQuestion
import com.pambrose.jev4k.JevResult
import com.pambrose.jev4k.ModelInfo
import com.pambrose.jev4k.ModelList
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
 * with a field path; answers that are absent, or null, fail later, when read.
 */
internal fun mapSystemOne(
    body: JsonObject,
    requestedModel: String,
    questions: QuestionSet,
    response: ResponseInfo,
): JevResult {
    val reader = BodyReader(response)
    // A null answer is treated as absent, as null is everywhere else, so it fails only when it is read and doesn't
    // take the other answers down with it.
    val answersJson = reader.optionalObject(body, "answers", "answers").orEmpty().filterValues { it != JsonNull }
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
        questions = questions,
        answers = answers,
        raw = body,
        response = response,
    )
}

/** Turns a `/v1/models` body into a [ModelList]. */
internal fun mapModels(
    body: JsonObject,
    response: ResponseInfo,
): ModelList {
    val reader = BodyReader(response)
    val models = body["models"] as? JsonArray ?: reader.fail("models", "expected an array")
    val infos = models.mapIndexed { i, element ->
        val path = "models[$i]"
        val model = element as? JsonObject ?: reader.fail(path, "expected an object")
        ModelInfo(
            name = reader.optionalString(model, "name", "$path.name") ?: reader.fail("$path.name", "missing"),
            description = reader.optionalString(model, "description", "$path.description"),
            releaseDate = reader.optionalString(model, "release_date", "$path.release_date"),
        )
    }
    return ModelList(infos, response.requestId)
}

private class BodyReader(
    val response: ResponseInfo,
) {
    fun fail(
        path: String,
        detail: String,
    ): Nothing = response.fail(path, detail)

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
                val levels = (question as? ScoreQuestion)?.levels
                val probabilities = byLevel(doubles(obj, path), "$path.probabilities", levels?.size)
                val legend =
                    byLevel(optionalObject(obj, "legend", "$path.legend").orEmpty(), "$path.legend", levels?.size)
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

    // An unquoted NaN parses, and so does a number too big for a Double, so both are refused here rather than left
    // to surprise band(), isTrue() or nearestLevel later.
    private fun number(
        value: JsonElement?,
        path: String,
    ): Double {
        val primitive = (value as? JsonPrimitive)?.takeUnless { it.isString }
        val number = primitive?.doubleOrNull ?: fail(path, "expected a number")
        return number.takeIf { it.isFinite() } ?: fail(path, "expected a finite number")
    }

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

    /** Keys a map by level number; one outside `0 until levelCount`, when the question's levels are known, fails. */
    private fun <V> byLevel(
        map: Map<String, V>,
        path: String,
        levelCount: Int?,
    ): Map<Int, V> =
        map.entries
            .map { (key, value) ->
                val level = key.toIntOrNull() ?: fail("$path.$key", "expected an integer level")
                if (level < 0 || (levelCount != null && level >= levelCount)) {
                    val range = levelCount?.let { "0..${it - 1}" } ?: "0 or more"
                    fail("$path.$key", "level $level is outside the question's levels ($range)")
                }
                level to value
            }.sortedBy { it.first }
            .toMap()

    private fun inDeclaredOrder(
        probabilities: Map<String, Double>,
        declared: Set<String>?,
    ): Map<String, Double> {
        if (declared == null) return probabilities
        return buildMap {
            declared.forEach { key -> probabilities[key]?.let { put(key, it) } }
            probabilities.forEach { (key, value) -> if (key !in this) put(key, value) }
        }
    }
}

/** A token count, or null for anything that isn't a plain integer. Used only for optional metadata. */
private fun JsonElement?.asLongOrNull(): Long? = (this as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
