package com.pambrose.jev4k

import com.pambrose.jev4k.internal.retriesOn
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

/**
 * The client cases that need the JVM: the blocking wrapper, a real CIO engine against a local socket, and the JVM's
 * handling of a bare IllegalStateException, which on Linux and Windows is how the default engine reports a failed
 * connection.
 */
class ClientJvmTest : StringSpec() {
    private val payoutTicket = "Help! My payouts have been failing for 3 days."

    init {
        "the blocking wrapper mirrors the suspend API" {
            val jev = testJev { respondJson(TRIAGE_RESPONSE) }
            jev.client.blocking.ask(Triage, state = payoutTicket)[Triage.department].choice shouldBe Dept.TECHNICAL
            jev.client.blocking.query(state = Order("A-104")) { include(Triage) }[Triage.urgent].noul shouldBe 0.92
        }

        // Curl and WinHttp report a failed connection as a bare IllegalStateException, so Linux and Windows treat one
        // as a connection error. On the JVM it is still an ordinary failure: not retried, and not wrapped.
        "a bare IllegalStateException is neither retried nor wrapped" {
            RetryPolicy().retriesOn(IllegalStateException("bug")) shouldBe false

            var calls = 0
            val jev = testJev(retry = RetryPolicy()) {
                calls++
                throw IllegalStateException("bug")
            }
            shouldThrowExactly<IllegalStateException> { jev.client.ask(Triage, state = payoutTicket) }
            calls shouldBe 1
            jev.delays shouldBe emptyList()
        }

        // The engine-level check that HttpRequestRetry is installed before HttpTimeout: installed after it, one
        // expiry cancels the whole retry loop and the server only ever sees the first attempt. The timeout is
        // generous because the first attempt pays for CIO's cold start, and only the count matters here.
        "a slow response times out, and timeouts are retried (real CIO engine)" {
            SilentServer().use { server ->
                val delays = mutableListOf<Long>()
                JevClient {
                    apiKey = "test-key"
                    baseUrl = "http://127.0.0.1:${server.port}"
                    timeout = 1.seconds
                    env = { null }
                    retryDelay = { delays += it }
                    random = NoJitter
                }.use { client ->
                    shouldThrow<JevTimeoutException> { client.ask(Triage, state = payoutTicket) }
                }
                server.awaitRequests(3) shouldBe 3
                delays shouldBe listOf(500L, 1000L)
            }
        }
    }
}
