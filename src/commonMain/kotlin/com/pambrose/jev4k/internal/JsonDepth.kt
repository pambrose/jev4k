package com.pambrose.jev4k.internal

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * How deeply jev4k lets JSON nest, sent or received. kotlinx.serialization encodes, parses and prints JSON by
 * recursing once per level, several frames deep, so deeply nested JSON overflows the stack: a `StackOverflowError` on
 * the JVM, a `RangeError` on Node.js, a crash on Kotlin/Native. Windows is the tightest, with a 1 MB main-thread
 * stack: encoding 512 levels crashed the mingwX64 test binary. 128 (serde_json's default) leaves a wide margin
 * everywhere and is still far deeper than any real state or answer.
 */
internal const val MAX_JSON_DEPTH = 128

/** True when this element nests more than [MAX_JSON_DEPTH] objects or arrays deep. */
internal fun JsonElement.nestsTooDeep(): Boolean {
    // Iterative, with an explicit stack, so that checking can't overflow either. Only containers are pushed.
    val pending = ArrayDeque<Pair<JsonElement, Int>>()
    pending.addLast(this to 1)
    while (pending.isNotEmpty()) {
        val (element, depth) = pending.removeLast()
        val children = when (element) {
            is JsonObject -> element.values
            is JsonArray -> element
            else -> continue
        }
        if (depth > MAX_JSON_DEPTH) return true
        children.forEach { if (it is JsonObject || it is JsonArray) pending.addLast(it to depth + 1) }
    }
    return false
}

/** The same check on JSON text, before it is parsed: counts open `[` and `{` outside string literals. */
internal fun String.nestsTooDeep(): Boolean {
    var depth = 0
    var inString = false
    var escaped = false
    for (c in this) {
        when {
            escaped -> escaped = false
            inString && c == '\\' -> escaped = true
            inString && c == '"' -> inString = false
            inString -> Unit
            c == '"' -> inString = true
            c == '[' || c == '{' -> if (++depth > MAX_JSON_DEPTH) return true
            c == ']' || c == '}' -> depth--
        }
    }
    return false
}

/**
 * True when this element holds a number JSON can't represent, NaN or an infinity. kotlinx.serialization refuses to
 * encode one, so it is reported where the element is checked rather than when the request is written.
 */
internal fun JsonElement.hasNonFiniteNumber(): Boolean {
    val pending = ArrayDeque<JsonElement>()
    pending.addLast(this)
    while (pending.isNotEmpty()) {
        when (val element = pending.removeLast()) {
            is JsonObject -> pending.addAll(element.values)
            is JsonArray -> pending.addAll(element)
            is JsonPrimitive -> if (!element.isString && element.doubleOrNull?.isFinite() == false) return true
        }
    }
    return false
}

/**
 * What stops this element being sent, naming it [what] in the message, or null: nesting past [MAX_JSON_DEPTH], or a
 * number JSON can't represent. Every JSON value jev4k sends is checked here: the state, question entries and extra
 * body fields.
 */
internal fun JsonElement.sendProblem(what: String): String? =
    when {
        nestsTooDeep() -> "$what is nested more than $MAX_JSON_DEPTH levels deep"
        hasNonFiniteNumber() -> "$what holds NaN or an infinity, which JSON lacks"
        else -> null
    }
