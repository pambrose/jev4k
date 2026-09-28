package com.pambrose.jev4k

import com.pambrose.jev4k.internal.retriesOn
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.cio.ParserException
import java.security.cert.CertificateException
import kotlin.time.Duration.Companion.seconds
import io.kotest.matchers.string.shouldContain

/**
 * The client cases that need the JVM: the blocking wrapper, a real CIO engine against a local socket, and the JVM's
 * handling of a bare IllegalStateException, which on Linux and Windows is how the default engine reports a failed
 * connection.
 */
class ClientJvmTest : StringSpec() {
    /** A client on the real CIO engine, pointed at a server on this machine. */
    private fun localClient(
        port: Int,
        scheme: String = "http",
        delays: MutableList<Long> = mutableListOf(),
        configure: JevConfigBuilder.() -> Unit = {},
    ) = JevClient {
        testDefaults(delays)
        baseUrl = "$scheme://127.0.0.1:$port"
        configure()
    }

    init {
        "the blocking wrapper mirrors the suspend API" {
            val jev = triageJev()
            jev.client.blocking.ask(Triage, state = PAYOUT_TICKET)[Triage.department].choice shouldBe Dept.TECHNICAL
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
            shouldThrowExactly<IllegalStateException> { jev.client.ask(Triage, state = PAYOUT_TICKET) }
            calls shouldBe 1
            jev.delays shouldBe emptyList()
        }

        // The engine-level check that HttpRequestRetry is installed before HttpTimeout: installed after it, one
        // expiry cancels the whole retry loop and the server only ever sees the first attempt. CIO is warmed up
        // first, against another server, so a cold start on a loaded runner can't eat the first attempt's second
        // before its request is written, and the count stays exact.
        "a slow response times out, and timeouts are retried (real CIO engine)" {
            RawServer(MODELS_RESPONSE).use { warm ->
                localClient(warm.port).use { it.models() }
            }
            SilentServer().use { server ->
                val delays = mutableListOf<Long>()
                localClient(server.port, delays = delays) { timeout = 1.seconds }.use { client ->
                    shouldThrow<JevTimeoutException> { client.ask(Triage, state = PAYOUT_TICKET) }
                }
                server.awaitRequests(3) shouldBe 3
                delays shouldBe listOf(500L, 1000L)
            }
        }

        // The JDK doesn't trust a self-signed certificate, and CIO's TLS handshake reports that as a raw
        // CertificateException, which isn't an IOException. It used to escape as is; it is a connection error now.
        "a server certificate the JDK doesn't trust is a JevConnectionException (real CIO engine)" {
            RawServer(MODELS_RESPONSE, RawServer.selfSignedTlsSocket()).use { server ->
                localClient(server.port, scheme = "https") { retry = RetryPolicy.NONE }.use { client ->
                    val e = shouldThrow<JevConnectionException> { client.models() }
                    e.causes().any { it is CertificateException } shouldBe true
                }
                server.requests shouldBe 0
            }
        }

        // Ktor checks that a saved body is as long as its Content-Length and throws a bare IllegalStateException
        // when it isn't. isConnectionError matches Ktor's wording, so this also pins it: a Ktor upgrade that rewords
        // the message fails here.
        "a body cut short of its Content-Length is retried, then a JevConnectionException (real CIO engine)" {
            val truncated = "${MODELS_HEAD_200}Content-Length: 100\r\n\r\n{\"models\":[".encodeToByteArray()
            RawServer(truncated).use { server ->
                val delays = mutableListOf<Long>()
                localClient(server.port, delays = delays).use { client ->
                    val e = shouldThrow<JevConnectionException> { client.models() }
                    e.causes().any { it.message.orEmpty().startsWith("Content-Length mismatch") } shouldBe true
                }
                server.requests shouldBe 3
                delays shouldBe listOf(500L, 1000L)
            }
        }

        // Without the check, CIO would read 100 MB into memory, or here, fail on the truncated body.
        "a declared body over the size limit is refused before CIO reads it (real CIO engine)" {
            val oversized = "${MODELS_HEAD_200}Content-Length: 104857600\r\n\r\n{}".encodeToByteArray()
            RawServer(oversized).use { server ->
                localClient(server.port).use { client ->
                    shouldThrow<JevResponseValidationException> { client.models() }
                        .message shouldContain "body of 104857600 bytes not read"
                }
                server.requests shouldBe 1
            }
        }

        "a response CIO can't parse is a JevConnectionException (real CIO engine)" {
            RawServer("NOT HTTP AT ALL\r\n\r\n".encodeToByteArray()).use { server ->
                localClient(server.port) { retry = RetryPolicy.NONE }.use { client ->
                    val e = shouldThrow<JevConnectionException> { client.models() }
                    e.causes().any { it is ParserException } shouldBe true
                }
            }
        }
    }

    private fun Throwable.causes(): Sequence<Throwable> = generateSequence(this) { it.cause }

    private companion object {
        const val MODELS_HEAD_200 = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n"
        val MODELS_RESPONSE = "${MODELS_HEAD_200}Content-Length: 13\r\n\r\n{\"models\":[]}".encodeToByteArray()
    }
}
