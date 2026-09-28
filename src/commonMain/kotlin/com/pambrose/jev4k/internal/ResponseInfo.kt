package com.pambrose.jev4k.internal

import com.pambrose.jev4k.JevResponseValidationException
import io.ktor.http.Headers
import io.ktor.util.toMap

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
