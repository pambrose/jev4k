package com.pambrose.jev4k.internal

import com.pambrose.jev4k.JevResponseValidationException
import io.ktor.http.Headers
import io.ktor.util.toMap
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject

/**
 * The response a body came from, as a [JevResponseValidationException] reports it: the body's text as received (not
 * the parsed JSON written out again), the status and the headers.
 */
internal class ResponseInfo(
    val text: String,
    val status: Int,
    val headers: Headers,
    val requestId: String?,
    val endpoint: String,
) {
    /** Throws the [JevResponseValidationException] for a problem at [fieldPath], or with the body as a whole. */
    fun fail(
        fieldPath: String?,
        detail: String,
        cause: Throwable? = null,
    ): Nothing {
        val headerMap = headers.toMap()
        throw JevResponseValidationException(detail, fieldPath, text, requestId, endpoint, status, headerMap, cause)
    }
}

/**
 * The body as a JSON object, or a [JevResponseValidationException] saying why it isn't one. Shared by the client and
 * `jevResult(String)`, so a test body is checked exactly as a real one is.
 */
internal fun ResponseInfo.parseObject(): JsonObject {
    // Checked before parsing: kotlinx.serialization parses by recursion, so a deep enough body overflows it.
    if (text.nestsTooDeep()) fail(null, "body is nested more than $MAX_JSON_DEPTH levels deep")
    val body = try {
        JevJson.parseToJsonElement(text)
    } catch (e: SerializationException) {
        fail(null, "body is not JSON", e)
    }
    return body as? JsonObject ?: fail(null, "expected a JSON object")
}
