package com.pambrose.jev4k

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.TestConfiguration
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Parses a JSON literal so tests can compare structure rather than formatting. */
internal fun json(text: String): JsonElement = Json.parseToJsonElement(text)

/** A client wired to a [MockEngine], recording every retry delay instead of sleeping. */
internal class TestJev(
    val client: JevClient,
    val engine: MockEngine,
    val delays: List<Long>,
) {
    val requests: List<HttpRequestData> get() = engine.requestHistory
}

/**
 * A local HTTP server that accepts connections, counts the requests it receives, and never answers,
 * so a real engine's request timeout fires.
 */
internal class SilentServer : AutoCloseable {
    // Bound to 127.0.0.1 explicitly: a test that dials that literal would miss a loopback resolved to ::1.
    private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val clients = CopyOnWriteArrayList<Socket>()
    private val requests = AtomicInteger()

    val port: Int get() = socket.localPort

    init {
        thread(isDaemon = true) {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                clients += client
                thread(isDaemon = true) {
                    runCatching {
                        client.getInputStream().bufferedReader().lineSequence().forEach {
                            if (it.startsWith("POST ")) requests.incrementAndGet()
                        }
                    }
                }
            }
        }
    }

    /**
     * Waits for [expected] requests to arrive, then returns how many did. On timeout Kotest reports the last
     * value it saw, so a failure says how many requests actually landed.
     */
    suspend fun awaitRequests(
        expected: Int,
        within: Duration = 10.seconds,
    ): Int =
        eventually(within) {
            requests.get().also { it shouldBe expected }
        }

    override fun close() {
        socket.close()
        clients.forEach { runCatching { it.close() } }
    }
}

/** A [Random] whose `nextDouble()` is always 0.0, so retry jitter is predictable. */
internal object NoJitter : Random() {
    override fun nextBits(bitCount: Int): Int = 0
}

/** The opposite: `nextDouble()` just under 1.0, so jitter subtracts its full share. */
internal object AlwaysOne : Random() {
    override fun nextBits(bitCount: Int): Int = if (bitCount == 0) 0 else -1 ushr (32 - bitCount)
}

/**
 * Every client here wraps a fresh Ktor engine. Both are registered with Kotest's [autoClose], so they are shut
 * down when the spec ends rather than at process exit — the client never closes an engine it was handed.
 */
internal fun TestConfiguration.testJev(
    retry: RetryPolicy = RetryPolicy.NONE,
    configure: JevConfigBuilder.() -> Unit = {},
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): TestJev {
    val delays = mutableListOf<Long>()
    val engine = MockEngine(handler)
    val client = JevClient {
        apiKey = "test-key"
        this.retry = retry
        this.engine = engine
        env = { null }
        retryDelay = { delays += it }
        random = NoJitter
        configure()
    }
    return TestJev(autoClose(client), autoClose(engine), delays)
}

internal fun MockRequestHandleScope.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
    extraHeaders: Map<String, String> = emptyMap(),
): HttpResponseData =
    respond(
        content = body.trimIndent(),
        status = status,
        headers = headers {
            append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            extraHeaders.forEach { (name, value) -> append(name, value) }
        },
    )

internal suspend fun HttpRequestData.bodyJson(): JsonElement = json(body.toByteArray().decodeToString())

/** The documented three-answer response, for questions `is_urgent`, `department`, and `frustration`. */
internal const val DOCUMENTED_RESPONSE = """
{
  "model": "jev-1.13.0",
  "answers": {
    "is_urgent": { "type": "noul", "noul": 0.92 },
    "department": {
      "type": "choice",
      "choice": "technical",
      "probabilities": { "billing": 0.08, "technical": 0.85, "sales": 0.07 },
      "confidence": 0.82
    },
    "frustration": {
      "type": "score",
      "score": 1.6,
      "legend": { "0": "Calm", "1": "Frustrated", "2": "Very angry" },
      "probabilities": { "0": 0.05, "1": 0.3, "2": 0.65 },
      "confidence": 0.78
    }
  },
  "usage": { "input_tokens": 312, "output_tokens": 48 }
}
"""

/** [DOCUMENTED_RESPONSE] with the ids used by [Triage]. */
internal val TRIAGE_RESPONSE = DOCUMENTED_RESPONSE.replace("\"is_urgent\"", "\"urgent\"")

enum class Dept(
    override val description: String,
) : JevOption {
    BILLING("Payments, invoicing, refunds"),
    TECHNICAL("Bugs, outages, integrations"),
    SALES("Pricing, upgrades, new accounts"),
    ;

    override val optionKey: String get() = name.lowercase()
}

object Triage : JevQuery() {
    val urgent by noul("Does this convey urgency?") {
        whenTrue("Explicitly time-sensitive")
        whenFalse("No urgency expressed")
    }
    val department by choice<Dept>("Which team should handle this?")
    val frustration by score("How frustrated is the customer?") {
        levels("Calm", "Frustrated", "Very angry")
    }
}

@Serializable
data class Ticket(
    val subject: String,
    val message: String,
)

@Serializable
data class Order(
    val id: String,
    val status: String = "open",
    val items: List<String> = emptyList(),
)

/** A state whose only field can hold a value JSON can't express. */
@Serializable
data class Reading(
    val temperature: Double,
)
