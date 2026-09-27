package com.pambrose.jev4k

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ConfigTest : StringSpec() {
    private fun config(
        env: Map<String, String> = emptyMap(),
        block: JevConfigBuilder.() -> Unit = {},
    ): JevConfig =
        JevConfigBuilder().apply {
            this.env = { env[it] }
            block()
        }.build()

    init {
        "explicit settings win over environment variables" {
            val cfg = config(
                env = mapOf(
                    "TYPESAFE_API_KEY" to "env-key",
                    "TYPESAFE_BASE_URL" to "https://env.example",
                    "TYPESAFE_DEFAULT_MODEL" to "env-model",
                ),
            ) {
                apiKey = "explicit-key"
                baseUrl = "https://explicit.example"
                defaultModel = "explicit-model"
            }
            cfg.apiKey shouldBe "explicit-key"
            cfg.baseUrl shouldBe "https://explicit.example"
            cfg.defaultModel shouldBe "explicit-model"
        }

        "environment variables are used when nothing is set explicitly" {
            val cfg = config(
                env = mapOf(
                    "TYPESAFE_API_KEY" to "env-key",
                    "TYPESAFE_BASE_URL" to "https://env.example",
                    "TYPESAFE_DEFAULT_MODEL" to "env-model",
                ),
            )
            cfg.apiKey shouldBe "env-key"
            cfg.baseUrl shouldBe "https://env.example"
            cfg.defaultModel shouldBe "env-model"
        }

        "defaults apply when neither explicit settings nor environment variables are present" {
            val cfg = config { apiKey = "k" }
            cfg.baseUrl shouldBe "https://api.typesafe.ai"
            cfg.defaultModel shouldBe "jev-latest"
            cfg.timeout shouldBe 10.seconds
            cfg.retry shouldBe RetryPolicy()
        }

        "blank environment variables are ignored" {
            val cfg = config(
                env = mapOf("TYPESAFE_API_KEY" to "k", "TYPESAFE_BASE_URL" to "  ", "TYPESAFE_DEFAULT_MODEL" to ""),
            )
            cfg.baseUrl shouldBe "https://api.typesafe.ai"
            cfg.defaultModel shouldBe "jev-latest"
        }

        "a missing API key fails with a message naming TYPESAFE_API_KEY" {
            val e = shouldThrow<JevConfigException> { config(env = mapOf("TYPESAFE_API_KEY" to " ")) }
            e.message shouldContain "TYPESAFE_API_KEY"
        }

        // A blank explicit value is a value nobody meant to set, so it defers exactly like a blank env var.
        "blank explicit settings fall through to the environment and the defaults" {
            val cfg = config(env = mapOf("TYPESAFE_API_KEY" to "env-key")) {
                apiKey = " "
                baseUrl = ""
                defaultModel = "  "
            }
            cfg.apiKey shouldBe "env-key"
            cfg.baseUrl shouldBe "https://api.typesafe.ai"
            cfg.defaultModel shouldBe "jev-latest"
        }

        // Ktor reads a scheme-less value as a relative path, so the failure would otherwise arrive much later,
        // as a connection error naming an endpoint the caller never typed.
        "a base URL without a scheme is rejected" {
            for (url in listOf("api.typesafe.ai", "//api.typesafe.ai", "ftp://api.typesafe.ai")) {
                val e = shouldThrow<JevConfigException> {
                    config {
                        apiKey = "k"
                        baseUrl = url
                    }
                }
                e.message shouldContain "http://"
            }
        }

        "a timeout that rounds down to zero milliseconds is rejected" {
            for (bad in listOf(Duration.ZERO, (-1).seconds, 500.microseconds)) {
                val e = shouldThrow<JevConfigException> {
                    config {
                        apiKey = "k"
                        timeout = bad
                    }
                }
                e.message shouldContain "timeout"
            }
            config {
                apiKey = "k"
                timeout = 1.milliseconds
            }.timeout shouldBe 1.milliseconds
        }

        "a configuration with several problems reports them all at once" {
            val e = shouldThrow<JevConfigException> {
                config {
                    apiKey = "k"
                    baseUrl = "api.typesafe.ai"
                    timeout = Duration.ZERO
                }
            }
            e.message shouldContain "timeout"
            e.message shouldContain "baseUrl"
        }

        "a trailing slash on the base URL is trimmed" {
            config {
                apiKey = "k"
                baseUrl = "https://proxy.example/jev/"
            }.baseUrl shouldBe "https://proxy.example/jev"
        }

        "toString redacts the API key" {
            val text = config { apiKey = "super-secret-key" }.toString()
            text shouldNotContain "super-secret-key"
            text shouldContain "baseUrl=https://api.typesafe.ai"
        }

        "retry policy defaults match the official SDKs" {
            val policy = RetryPolicy()
            policy.maxRetries shouldBe 2
            policy.initialBackoff shouldBe 500.milliseconds
            policy.maxBackoff shouldBe 5.seconds
            policy.jitter shouldBe 0.25
            policy.retryStatuses shouldBe setOf(408, 429) + (500..599)
            policy.maxRetryAfter shouldBe 60.seconds
        }

        "backoff doubles from the initial delay and is capped at the maximum" {
            val policy = RetryPolicy()
            (1..6).map { policy.backoff(it, random = 0.0) } shouldBe
                listOf(500.milliseconds, 1.seconds, 2.seconds, 4.seconds, 5.seconds, 5.seconds)
        }

        "jitter subtracts up to its fraction of the delay" {
            RetryPolicy().backoff(2, random = 1.0) shouldBe 750.milliseconds
        }

        "the backoff exponent is clamped at both ends" {
            val policy = RetryPolicy()
            policy.backoff(0, random = 0.0) shouldBe 500.milliseconds
            policy.backoff(100, random = 0.0) shouldBe 5.seconds
        }

        "invalid retry policies are rejected" {
            shouldThrow<JevConfigException> { RetryPolicy(maxRetries = -1) }
            shouldThrow<JevConfigException> { RetryPolicy(jitter = 1.5) }
            shouldThrow<JevConfigException> { RetryPolicy(jitter = -0.1) }
            shouldThrow<JevConfigException> { RetryPolicy(initialBackoff = (-1).seconds) }
            shouldThrow<JevConfigException> { RetryPolicy(maxRetryAfter = (-1).seconds) }
            shouldThrow<JevConfigException> { RetryPolicy(initialBackoff = 10.seconds, maxBackoff = 1.seconds) }
        }

        // Every problem with a policy is reported at once, the way question validation does it.
        "an invalid policy reports every problem it found" {
            val e = shouldThrow<JevConfigException> { RetryPolicy(maxRetries = -1, jitter = 2.0) }
            e.message shouldContain "maxRetries"
            e.message shouldContain "jitter"
        }

        // What NONE does to a live request is pinned in ClientTest; this only fixes the constant itself.
        "RetryPolicy.NONE is zero retries" {
            RetryPolicy.NONE.maxRetries shouldBe 0
        }
    }
}
