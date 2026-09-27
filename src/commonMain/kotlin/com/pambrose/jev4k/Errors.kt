package com.pambrose.jev4k

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.time.Duration

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
 */
open class JevApiException(
    val status: Int,
    val body: String?,
    val headers: Map<String, List<String>>,
    val requestId: String?,
    val endpoint: String,
    message: String,
    cause: Throwable? = null,
) : JevException(message, cause) {
    internal constructor(response: ErrorResponse) :
        this(response.status, response.body, response.headers, response.requestId, response.endpoint, response.message)

    /** [body][JevApiException.body] parsed as JSON, or null if it isn't JSON. */
    val bodyJson: JsonElement? by lazy {
        body?.let {
            try {
                Json.parseToJsonElement(it)
            } catch (_: SerializationException) {
                null
            }
        }
    }
}

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
    val retryAfter: Duration?,
    response: ErrorResponse,
) : JevApiException(response)

/** 5xx: a server-side failure. */
open class JevInternalServerException internal constructor(
    response: ErrorResponse,
) : JevApiException(response)

/** 529: TypeSafe is temporarily overloaded. */
class JevOverloadedException internal constructor(
    response: ErrorResponse,
) : JevInternalServerException(response)

/** No HTTP response was received (DNS, TLS, refused or dropped connection). */
open class JevConnectionException(
    message: String,
    cause: Throwable? = null,
) : JevException(message, cause)

/** A request attempt exceeded the configured timeout. */
class JevTimeoutException(
    message: String,
    cause: Throwable? = null,
) : JevConnectionException(message, cause)

/** Builds the exception for a non-2xx response, after retries are exhausted. */
internal fun apiException(
    status: Int,
    body: String?,
    headers: Map<String, List<String>>,
    requestId: String?,
    endpoint: String,
    retryAfter: Duration?,
): JevApiException {
    val message = buildString {
        append("HTTP $status from $endpoint")
        requestId?.let { append(" (request id $it)") }
        if (!body.isNullOrBlank()) append(": ${body.take(MAX_BODY_IN_MESSAGE)}")
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
