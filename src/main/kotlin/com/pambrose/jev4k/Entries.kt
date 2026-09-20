package com.pambrose.jev4k

import com.pambrose.jev4k.internal.ValueJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer

/**
 * Builds a structured JSON entry for instructions, option descriptions, or levels, e.g.
 * `entry("question" to "Which team?", "focus" to "The primary request")`. Keys keep their order,
 * and the model sees both the keys and the values.
 */
fun entry(vararg fields: Pair<String, Any?>): JsonObject =
    buildJsonObject { for ((key, value) in fields) put(key, jsonOf(value)) }

/**
 * A contrastive option or level description, `{"what", "not_for", "examples"}`, the shape the
 * TypeSafe docs use to sharpen boundaries between similar options.
 */
fun rubric(
    what: String,
    notFor: String? = null,
    examples: List<String> = emptyList(),
): JsonObject =
    buildJsonObject {
        put("what", what)
        if (notFor != null) put("not_for", notFor)
        if (examples.isNotEmpty()) put("examples", JsonArray(examples.map(::JsonPrimitive)))
    }

/**
 * Converts plain Kotlin values (null, strings, numbers, booleans, enums, maps with string keys,
 * iterables, arrays, and JSON elements) to JSON. Use [jsonEntry] for `@Serializable` values.
 */
fun jsonOf(value: Any?): JsonElement =
    when (value) {
        null -> JsonNull

        is JsonElement -> value

        is String -> JsonPrimitive(value)

        // JSON has no NaN or Infinity. Caught here, the caller gets a JevValidationException naming the field;
        // left alone, it would surface as a kotlinx encoding failure from inside the HTTP call.
        is Double if !value.isFinite() -> nonFinite(value)

        is Float if !value.isFinite() -> nonFinite(value)

        is Number -> JsonPrimitive(value)

        is Boolean -> JsonPrimitive(value)

        is Enum<*> -> JsonPrimitive(value.name)

        is Map<*, *> -> JsonObject(
            value.entries.associate { (key, item) ->
                (key as? String ?: unsupported(key, "map key")) to jsonOf(item)
            },
        )

        is Iterable<*> -> JsonArray(value.map(::jsonOf))

        is Array<*> -> JsonArray(value.map(::jsonOf))

        else -> unsupported(value, "value")
    }

private fun unsupported(
    value: Any?,
    what: String,
): Nothing =
    throw JevValidationException(
        listOf(
            "Can't convert a $what of type ${value?.let { it::class.simpleName }} to JSON; use a String, " +
                "Number, Boolean, Map, List, JsonElement, or jsonEntry() for @Serializable values",
        ),
    )

private fun nonFinite(value: Number): Nothing =
    throw JevValidationException(listOf("JSON has no representation for $value; send a string or omit the field"))

/** Encodes a `@Serializable` value as JSON, keeping fields that equal their defaults. */
@JvmSynthetic
inline fun <reified T> jsonEntry(value: T): JsonElement = encodeValue(value, serializerOrReport<T>())

@PublishedApi
internal inline fun <reified T> serializerOrReport(): KSerializer<T> =
    try {
        serializer<T>()
    } catch (e: SerializationException) {
        throw JevValidationException(
            listOf(
                "Can't encode ${T::class.simpleName} as JSON (${e.message}); mark it @Serializable, or pass a " +
                    "JsonElement (buildJsonObject) or entry(...)",
            ),
            e,
        )
    }

@PublishedApi
internal fun <T> encodeValue(
    value: T,
    serializer: KSerializer<T>,
): JsonElement =
    try {
        ValueJson.encodeToJsonElement(serializer, value)
    } catch (e: SerializationException) {
        // A non-finite Double, or a serializer of the caller's own that refuses the value. Without this the
        // failure escapes as a kotlinx exception, and every jev4k failure is supposed to be a JevException.
        throw JevValidationException(
            listOf("Can't encode ${value?.let { it::class.simpleName }} as JSON: ${e.message}"),
            e,
        )
    }
