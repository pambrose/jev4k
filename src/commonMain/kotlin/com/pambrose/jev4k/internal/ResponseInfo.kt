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
    fun invalid(
        detail: String,
        fieldPath: String? = null,
        cause: Throwable? = null,
    ) = JevResponseValidationException(detail, fieldPath, text, requestId, endpoint, status, headers.toMap(), cause)
}

/**
 * The body as a JSON object, or a [JevResponseValidationException] saying why it isn't one. Shared by the client and
 * `jevResult(String)`, so a test body is checked exactly as a real one is.
 */
internal fun ResponseInfo.parseObject(): JsonObject {
    // Checked before parsing: kotlinx.serialization parses by recursion, so a deep enough body overflows it.
    val tooDeep = text.nestsTooDeep()
    val body = try {
        if (tooDeep) null else JevJson.parseToJsonElement(text)
    } catch (e: SerializationException) {
        throw invalid("body is not JSON", cause = e)
    }
    val problem = if (tooDeep) "body is nested more than $MAX_JSON_DEPTH levels deep" else "expected a JSON object"
    return body as? JsonObject ?: throw invalid(problem)
}
