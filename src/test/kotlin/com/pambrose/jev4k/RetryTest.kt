package com.pambrose.jev4k

import com.pambrose.jev4k.internal.delayMillis
import com.pambrose.jev4k.internal.retriesOn
import com.pambrose.jev4k.internal.retryHint
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.http.Headers
import io.ktor.http.headersOf
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.channels.UnresolvedAddressException
import java.util.concurrent.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
                // The other form RFC 9110 allows for Retry-After. jev4k backs off rather than parsing it.
                "Wed, 21 Oct 2026 07:28:00 GMT",
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

        // Ktor can hand a timeout back wrapped in CancellationExceptions; a real cancellation has no such cause
        // and must never be retried.
        "a cancellation is retried only when it wraps a timeout" {
            policy.retriesOn(CancellationException("cancelled")) shouldBe false
            val wrapped = CancellationException("cancelled").apply { initCause(ConnectTimeoutException("too slow")) }
            policy.retriesOn(wrapped) shouldBe true
            RetryPolicy(retryOnTimeout = false).retriesOn(wrapped) shouldBe false
        }

        "anything else is not retried" {
            policy.retriesOn(IllegalStateException("bug")) shouldBe false
        }
    }
}
