package com.pambrose.jev4k

import com.pambrose.jev4k.internal.delayMillis
import com.pambrose.jev4k.internal.isConnectionError
import com.pambrose.jev4k.internal.retriesOn
import com.pambrose.jev4k.internal.retryHint
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.ClientEngineClosedException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.SendCountExceedException
import io.ktor.http.Headers
import io.ktor.http.headersOf
import io.ktor.util.network.UnresolvedAddressException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.io.IOException
import io.ktor.http.fromHttpToGmtDate

/**
 * The retry rules on their own, without a client or a socket. The delay arithmetic and the header parsing are
 * where the edge cases live, and they are pure functions, so they are pinned directly.
 */
class RetryTest : StringSpec() {
    private val policy = RetryPolicy()

    init {
        "retry-after-ms is read as milliseconds and wins over Retry-After" {
            retryHint(headersOf("retry-after-ms", "250")) shouldBe 250.milliseconds
            retryHint(headersOf("retry-after-ms", " 1500 ")) shouldBe 1500.milliseconds
            val both = headersOf("retry-after-ms" to listOf("250"), "Retry-After" to listOf("2"))
            retryHint(both) shouldBe 250.milliseconds
        }

        "Retry-After is read as seconds, including a fractional one" {
            retryHint(headersOf("Retry-After", "2")) shouldBe 2.seconds
            retryHint(headersOf("Retry-After", "0.5")) shouldBe 500.milliseconds
        }

        // Duration rejects NaN outright, so an unparseable hint has to be filtered before it is converted;
        // otherwise the IllegalArgumentException escapes instead of the HTTP error the caller is waiting for.
        "a hint that isn't a positive finite number is ignored" {
            val junk = listOf(
                "NaN",
                "Infinity",
                "-Infinity",
                "abc",
                "",
                "  ",
                "0",
                "-5",
            )
            for (value in junk) {
                withClue("retry-after-ms: '$value'") { retryHint(headersOf("retry-after-ms", value)) shouldBe null }
                withClue("Retry-After: '$value'") { retryHint(headersOf("Retry-After", value)) shouldBe null }
            }
        }

        "an unusable retry-after-ms falls through to Retry-After" {
            retryHint(headersOf("retry-after-ms" to listOf("NaN"), "Retry-After" to listOf("2"))) shouldBe 2.seconds
        }

        "no headers at all means no hint" {
            retryHint(Headers.Empty) shouldBe null
        }

        "without a hint the delay doubles from 500 ms to the 5 s cap" {
            val delays = (1..5).map { policy.delayMillis(null, it, NoJitter) }
            delays shouldBe listOf(500L, 1000L, 2000L, 4000L, 5000L)
        }

        "jitter subtracts up to a quarter of the delay" {
            policy.delayMillis(null, 1, AlwaysOne) shouldBe 375L
            policy.delayMillis(null, 2, AlwaysOne) shouldBe 750L
        }

        "a usable hint replaces the backoff, and one over the cap doesn't" {
            val within = headersOf("retry-after-ms", "60000")
            val over = headersOf("retry-after-ms", "60001")
            policy.delayMillis(within, 1, NoJitter) shouldBe 60_000L
            policy.delayMillis(over, 1, NoJitter) shouldBe 500L
        }

        "hints are ignored when the policy says so" {
            val headers = headersOf("retry-after-ms", "250")
            RetryPolicy(respectRetryAfter = false).delayMillis(headers, 1, NoJitter) shouldBe 500L
        }

        "timeouts and connection failures are retried, and each can be switched off" {
            val cases = listOf(
                ConnectTimeoutException("too slow"),
                SocketTimeoutException("too slow"),
                IOException("refused"),
                UnresolvedAddressException(),
            )
            for (cause in cases) {
                withClue(cause::class.simpleName.orEmpty()) { policy.retriesOn(cause) shouldBe true }
            }
            RetryPolicy(retryOnTimeout = false).retriesOn(ConnectTimeoutException("too slow")) shouldBe false
            RetryPolicy(retryOnConnectionError = false).retriesOn(IOException("refused")) shouldBe false
        }

        // Ktor can hand a timeout back wrapped in CancellationExceptions. Any other cancellation, including one
        // whose cause is a sibling coroutine's failure, must never be retried.
        "a cancellation is retried only when it wraps a timeout" {
            policy.retriesOn(CancellationException("cancelled")) shouldBe false
            val wrapped = CancellationException("cancelled", ConnectTimeoutException("too slow"))
            policy.retriesOn(wrapped) shouldBe true
            RetryPolicy(retryOnTimeout = false).retriesOn(wrapped) shouldBe false
            policy.retriesOn(CancellationException("cancelled", IOException("disk full"))) shouldBe false
        }

        // Not an IllegalStateException: a bare one is how the Curl and WinHttp engines report a failed connection,
        // so on Linux and Windows it is retried.
        "anything else is not retried" {
            policy.retriesOn(IllegalArgumentException("bug")) shouldBe false
        }

        // The bare-ISE rule for Curl and WinHttp matches the exact class, so none of Ktor's own
        // IllegalStateException subclasses, nor a cancellation (one of them on Kotlin/Native), is a connection error.
        "Ktor's IllegalStateException subclasses and cancellations are not connection errors" {
            val cases = listOf(
                ClientEngineClosedException(),
                SendCountExceedException("too many sends"),
                CancellationException("cancelled"),
            )
            for (cause in cases) {
                withClue(cause::class.simpleName.orEmpty()) {
                    isConnectionError(cause) shouldBe false
                    policy.retriesOn(cause) shouldBe false
                }
            }
        }

        // Ktor's check that a saved body is as long as its Content-Length. ClientJvmTest pins the wording against a
        // real server; this pins that every platform treats it as a dropped connection.
        "a body cut short of its Content-Length is a connection error" {
            val truncated = IllegalStateException("Content-Length mismatch: expected 100 bytes, but received 11 bytes")
            isConnectionError(truncated) shouldBe true
            policy.retriesOn(truncated) shouldBe true
        }

        // The other form RFC 9110 allows. The Python SDK reads it, so jev4k does too.
        "a Retry-After HTTP-date is the time left until it, and a past or malformed date is no hint" {
            val now = "Wed, 21 Oct 2026 07:28:00 GMT".fromHttpToGmtDate().timestamp
            retryHint(headersOf("Retry-After", "Wed, 21 Oct 2026 07:28:30 GMT"), now) shouldBe 30.seconds
            retryHint(headersOf("Retry-After", "Wed, 21 Oct 2026 07:28:00 GMT"), now) shouldBe null
            retryHint(headersOf("Retry-After", "Wed, 21 Oct 2026 07:27:00 GMT"), now) shouldBe null
            retryHint(headersOf("Retry-After", "Wed, 21 Octember 2026"), now) shouldBe null
        }

        "a date hint is capped by maxRetryAfter like a numeric one" {
            val now = "Wed, 21 Oct 2026 07:28:00 GMT".fromHttpToGmtDate().timestamp
            val soon = headersOf("Retry-After", "Wed, 21 Oct 2026 07:28:30 GMT")
            val late = headersOf("Retry-After", "Wed, 21 Oct 2026 07:30:00 GMT")
            RetryPolicy().delayMillis(soon, 1, NoJitter, now) shouldBe 30_000L
            RetryPolicy().delayMillis(late, 1, NoJitter, now) shouldBe 500L
        }
    }
}
