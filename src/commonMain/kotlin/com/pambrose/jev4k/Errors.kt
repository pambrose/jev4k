package com.pambrose.jev4k

import com.pambrose.jev4k.internal.nestsTooDeep
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/** Base class for every error raised by jev4k. */
sealed class JevException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** The client could not be configured, e.g. no API key was supplied. */
class JevConfigException(
    message: String,
) : JevException(message)

/**
 * A request was rejected locally, before anything was sent.
 * [problems][JevValidationException.problems] lists every issue found.
 */
class JevValidationException(
    val problems: List<String>,
    cause: Throwable? = null,
) : JevException("Invalid Jev request: ${problems.joinToString("; ")}", cause)

/**
 * The API answered with an error, or with a body jev4k couldn't use. [body][JevApiException.body] is
 * the raw response body, [requestId][JevApiException.requestId] the `x-typesafe-request-id` header,
 * and [endpoint][JevApiException.endpoint] the method and URL called.
 *
 * On the JVM it is `Serializable`, as every `Throwable` is, provided any `headers` map passed in is.
 */
open class JevApiException(
    val status: Int,
    val body: String?,
    headers: Map<String, List<String>>,
    val requestId: String?,
    val endpoint: String,
    message: String,
    cause: Throwable? = null,
) : JevException(message, cause) {
    internal constructor(response: ErrorResponse) :
        this(response.status, response.body, response.headers, response.requestId, response.endpoint, response.message)

    /**
     * The response headers, with every name lowercased, so `headers["retry-after"]` finds the header however the
     * server spelled it and whichever engine read it (CIO keeps the server's spelling, fetch lowercases). Values of
     * names that differ only in case are merged, in order.
     */
    val headers: Map<String, List<String>> = headers.lowercaseNames()

    /**
     * [body][JevApiException.body] parsed as JSON, or null if it isn't JSON or nests implausibly deep. It is parsed on
     * each read, so the exception holds nothing that isn't `Serializable`.
     */
    val bodyJson: JsonElement?
        get() = body?.takeUnless { it.nestsTooDeep() }?.let {
            try {
                Json.parseToJsonElement(it)
            } catch (_: SerializationException) {
                null
            }
        }
}

private fun Map<String, List<String>>.lowercaseNames(): Map<String, List<String>> =
    entries.groupBy({ it.key.lowercase() }, { it.value }).mapValues { (_, values) -> values.flatten() }

/** A non-2xx response, as handed to the status-specific exception constructors. */
internal class ErrorResponse(
    val status: Int,
    val body: String?,
    val headers: Map<String, List<String>>,
    val requestId: String?,
    val endpoint: String,
    val message: String,
)

/** 400: the request was malformed. */
class JevBadRequestException internal constructor(
    response: ErrorResponse,
) : JevApiException(response)

/** 401: the API key is missing or invalid. */
class JevAuthenticationException internal constructor(
    response: ErrorResponse,
) : JevApiException(response)

/** 403: the API key may not do this. */
class JevPermissionDeniedException internal constructor(
    response: ErrorResponse,
) : JevApiException(response)

/** 404: unknown endpoint or resource. */
class JevNotFoundException internal constructor(
    response: ErrorResponse,
) : JevApiException(response)

/** 422: the request failed server-side validation; [body][JevApiException.body] names the offending field. */
class JevUnprocessableEntityException internal constructor(
    response: ErrorResponse,
) : JevApiException(response)

/** 429: rate limited. [retryAfter][JevRateLimitException.retryAfter] is the server's hint, when it sent one. */
class JevRateLimitException internal constructor(
    retryAfter: Duration?,
    response: ErrorResponse,
) : JevApiException(response) {
    // Nanoseconds, not a Duration: a boxed Duration isn't Serializable on the JVM. inWholeNanoseconds saturates at
    // Long.MAX_VALUE, which reads back as INFINITE; any hint that long is one to ignore anyway.
    private val retryAfterNanos: Long? = retryAfter?.inWholeNanoseconds

    /** How long the server asked callers to wait, from `retry-after-ms` or `Retry-After`, or null if it didn't say. */
    val retryAfter: Duration?
        get() = retryAfterNanos?.let { if (it == Long.MAX_VALUE) Duration.INFINITE else it.nanoseconds }

    /**
     * [retryAfter][JevRateLimitException.retryAfter] in whole milliseconds, for Java, which can't call a getter
     * that returns a `Duration`.
     */
    val retryAfterMillis: Long?
        get() = retryAfter?.inWholeMilliseconds
}

/** 5xx: a server-side failure. */
open class JevInternalServerException internal constructor(
    response: ErrorResponse,
) : JevApiException(response)

/** 529: TypeSafe is temporarily overloaded. */
class JevOverloadedException internal constructor(
    response: ErrorResponse,
) : JevInternalServerException(response)

/** No complete HTTP response was received (DNS, TLS, a refused or dropped connection, a truncated body). */
open class JevConnectionException(
    message: String,
    cause: Throwable? = null,
) : JevException(message, cause)

/**
 * A request attempt timed out: the configured `timeout`, or, with a caller-supplied engine, that engine's own
 * connect or socket timeout, which the message then names.
 */
class JevTimeoutException(
    message: String,
    cause: Throwable? = null,
) : JevConnectionException(message, cause)

/**
 * Builds the exception for a non-2xx response, after retries are exhausted. [bodyNote] explains a body that wasn't
 * read, in the message's place for one.
 */
internal fun apiException(
    status: Int,
    body: String?,
    headers: Map<String, List<String>>,
    requestId: String?,
    endpoint: String,
    retryAfter: Duration?,
    bodyNote: String? = null,
): JevApiException {
    val message = buildString {
        append("HTTP $status from $endpoint")
        requestId?.let { append(" (request id $it)") }
        if (!body.isNullOrBlank()) append(": ${body.take(MAX_BODY_IN_MESSAGE)}") else bodyNote?.let { append(": $it") }
    }
    val response = ErrorResponse(status, body, headers, requestId, endpoint, message)
    return when (status) {
        400 -> JevBadRequestException(response)
        401 -> JevAuthenticationException(response)
        403 -> JevPermissionDeniedException(response)
        404 -> JevNotFoundException(response)
        422 -> JevUnprocessableEntityException(response)
        429 -> JevRateLimitException(retryAfter, response)
        529 -> JevOverloadedException(response)
        in 500..599 -> JevInternalServerException(response)
        else -> JevApiException(response)
    }
}

private const val MAX_BODY_IN_MESSAGE = 500

/**
 * A successful response was missing data or didn't match the questions asked;
 * [fieldPath][JevResponseValidationException.fieldPath] locates the problem.
 */
class JevResponseValidationException(
    detail: String,
    val fieldPath: String?,
    body: String?,
    requestId: String?,
    endpoint: String,
    status: Int = 200,
    headers: Map<String, List<String>> = emptyMap(),
    cause: Throwable? = null,
) : JevApiException(
    status = status,
    body = body,
    headers = headers,
    requestId = requestId,
    endpoint = endpoint,
    message = validationMessage(endpoint, fieldPath, detail, requestId),
    cause = cause,
)

private fun validationMessage(
    endpoint: String,
    fieldPath: String?,
    detail: String,
    requestId: String?,
): String =
    buildString {
        append("Invalid response from $endpoint")
        fieldPath?.let { append(" at $it") }
        append(": $detail")
        requestId?.let { append(" (request id $it)") }
    }
