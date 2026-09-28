package com.pambrose.jev4k

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ErrorsTest : StringSpec() {
    private fun rateLimited(
        headers: Map<String, List<String>> = emptyMap(),
        retryAfter: Duration? = null,
    ): JevRateLimitException =
        jevApiException(429, headers = headers, retryAfter = retryAfter).shouldBeInstanceOf<JevRateLimitException>()

    init {
        // CIO keeps the server's spelling of a header name, and fetch lowercases it, so a mixed-case lookup would
        // work on one platform and not another.
        "header names are lowercased, and the values of names differing only in case are merged in order" {
            val e = jevApiException(
                500,
                headers = mapOf("X-Trace" to listOf("a"), "x-trace" to listOf("b"), "Retry-After" to listOf("1")),
            )
            e.headers shouldBe mapOf("x-trace" to listOf("a", "b"), "retry-after" to listOf("1"))
        }

        "jevApiException reads the rate-limit hint from the headers, as the client does, unless one is given" {
            rateLimited(mapOf("retry-after-ms" to listOf("1500"))).retryAfter shouldBe 1500.milliseconds
            rateLimited(mapOf("Retry-After" to listOf("2"))).retryAfter shouldBe 2.seconds
            rateLimited(mapOf("Retry-After" to listOf("2")), retryAfter = 5.seconds).retryAfter shouldBe 5.seconds
            rateLimited().retryAfter shouldBe null
        }

        "retryAfterMillis is the hint in whole milliseconds, for Java" {
            rateLimited(retryAfter = 1500.milliseconds).retryAfterMillis shouldBe 1500
            rateLimited().retryAfterMillis shouldBe null
        }

        "the hint keeps sub-millisecond precision, and an infinite hint stays infinite" {
            rateLimited(retryAfter = 1500.microseconds).retryAfter shouldBe 1500.microseconds
            rateLimited(retryAfter = Duration.INFINITE).retryAfter shouldBe Duration.INFINITE
        }

        "a ModelList equals any list of the same models, whatever its request id" {
            val models = listOf(ModelInfo("jev-latest", "Latest stable", "2026-09-01"))
            val listed = ModelList(models, requestId = "req-1")
            listed shouldBe models
            (models == listed) shouldBe true
            listed shouldBe ModelList(models, requestId = "req-2")
            listed.hashCode() shouldBe models.hashCode()
            listed.toString() shouldBe models.toString()
        }
    }
}
