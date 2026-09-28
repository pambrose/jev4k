package com.pambrose.jev4k.internal

import com.pambrose.jev4k.JevDefaults
import com.pambrose.jev4k.RetryPolicy
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal fun isTimeout(cause: Throwable): Boolean =
    cause is HttpRequestTimeoutException || cause is ConnectTimeoutException || cause is SocketTimeoutException

/** A failure to reach the server at all, or to receive its whole response, as opposed to an HTTP error response. */
internal fun isConnectionError(cause: Throwable): Boolean =
    cause is IOException ||
        cause is UnresolvedAddressException ||
        isTruncatedBody(cause) ||
        isPlatformConnectionError(cause)

/**
 * Ktor's own check, on the JVM and native targets, that a saved response body is as long as its `Content-Length`
 * says: the connection dropped partway through the body. Matched on the exact class and Ktor's wording, which
 * RetryTest pins, so a Ktor upgrade that rewords it fails a test instead of passing unnoticed.
 */
private fun isTruncatedBody(cause: Throwable): Boolean =
    cause::class == IllegalStateException::class && cause.message?.startsWith("Content-Length mismatch") == true

/**
 * Ktor can deliver a timeout wrapped in (possibly nested) [CancellationException]s, as its own
 * `retryOnException` also assumes; find what's underneath. A cancellation's cause isn't always a timeout: when a
 * sibling coroutine fails, the caller is cancelled with the sibling's exception as the cause.
 */
internal fun Throwable.unwrapCancellation(): Throwable =
    generateSequence(this) { (it as? CancellationException)?.cause }.last()

/**
 * Ktor's own rule, with jev4k's switches: a timeout is retried even when it arrives wrapped in a cancellation, but
 * no other cancellation is, so a caller cancelled because a sibling failed is never retried.
 */
internal fun RetryPolicy.retriesOn(cause: Throwable): Boolean =
    when {
        isTimeout(cause.unwrapCancellation()) -> retryOnTimeout
        cause is CancellationException -> false
        isConnectionError(cause) -> retryOnConnectionError
        else -> false
    }

/**
 * A header value as a positive, finite number of units, or null. `Duration` rejects NaN, and an
 * `Infinity` or a negative hint is no more usable, so anything but a real delay is treated as absent:
 * `Retry-After` as an HTTP-date falls here too, and the caller backs off instead.
 */
private fun String?.asDelayAmount(): Double? = this?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }

/** The server's retry hint: `retry-after-ms` (milliseconds) first, then `Retry-After` (seconds). */
internal fun retryHint(headers: Headers): Duration? =
    headers[JevDefaults.RETRY_AFTER_MS_HEADER].asDelayAmount()?.milliseconds
        ?: headers[HttpHeaders.RetryAfter].asDelayAmount()?.seconds

/**
 * Delay before retry number [retryNumber] (1-based): the server's hint when it is within
 * [RetryPolicy.maxRetryAfter], otherwise exponential backoff with jitter.
 */
internal fun RetryPolicy.delayMillis(
    headers: Headers?,
    retryNumber: Int,
    random: Random,
): Long {
    val hint = headers?.takeIf { respectRetryAfter }?.let(::retryHint)?.takeIf { it <= maxRetryAfter }
    return (hint ?: backoff(retryNumber, random.nextDouble())).inWholeMilliseconds
}
