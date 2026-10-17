package com.pambrose.jev4k

import com.pambrose.jev4k.internal.platformGetenv
import io.kotest.core.TestConfiguration
import io.kotest.core.spec.AutoCloseable as KotestAutoCloseable
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.random.Random

/** Parses a JSON literal so tests can compare structure rather than formatting. */
internal fun json(text: String): JsonElement = Json.parseToJsonElement(text)

/** A number wrapped in [depth] nested arrays, e.g. `[[1]]` for 2. */
internal fun nestedArrays(depth: Int): JsonElement {
    var element: JsonElement = JsonPrimitive(1)
    repeat(depth) { element = JsonArray(listOf(element)) }
    return element
}

/** A number wrapped in [depth] nested objects, e.g. `{"a":{"a":1}}` for 2. */
internal fun nestedObjects(depth: Int): JsonElement {
    var element: JsonElement = JsonPrimitive(1)
    repeat(depth) { element = JsonObject(mapOf("a" to element)) }
    return element
}

/** [nestedArrays] as JSON text, which can be sent without building the tree. */
internal fun nestedArrayText(depth: Int): String = "[".repeat(depth) + "1" + "]".repeat(depth)

/** A client wired to a [MockEngine], recording every retry delay instead of sleeping. */
internal class TestJev(
    val client: JevClient,
    val engine: MockEngine,
    val delays: List<Long>,
) {
    val requests: List<HttpRequestData> get() = engine.requestHistory
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
 * Closes [closeable] when the spec ends. Kotest's own `autoClose` takes Kotest's `AutoCloseable`, which is
 * `java.lang.AutoCloseable` on the JVM but a separate interface everywhere else, so a Ktor engine or a
 * [JevClient] is wrapped in one.
 */
internal fun <T : AutoCloseable> TestConfiguration.closeAfterSpec(closeable: T): T {
    autoClose(
        object : KotestAutoCloseable {
            override fun close() = closeable.close()
        },
    )
    return closeable
}

/** What every test client shares: a fake key, no environment, and each retry delay recorded in [delays], not slept. */
internal fun JevConfigBuilder.testDefaults(delays: MutableList<Long> = mutableListOf()) {
    apiKey = "test-key"
    env = { null }
    retryDelay = { delays += it }
    random = NoJitter
}

/**
 * Every client here wraps a fresh Ktor engine. Both are closed when the spec ends rather than at process exit —
 * the client never closes an engine it was handed.
 */
internal fun TestConfiguration.testJev(
    retry: RetryPolicy = RetryPolicy.NONE,
    configure: JevConfigBuilder.() -> Unit = {},
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): TestJev {
    val delays = mutableListOf<Long>()
    val engine = MockEngine(handler)
    val client = JevClient {
        testDefaults(delays)
        this.retry = retry
        this.engine = engine
        configure()
    }
    return TestJev(closeAfterSpec(client), closeAfterSpec(engine), delays)
}

/** A client whose every request gets the documented triage response. */
internal fun TestConfiguration.triageJev(): TestJev = testJev { respondJson(TRIAGE_RESPONSE) }

/** True when a live run was asked for with `JEV4K_LIVE=1`, as `make live-tests` does. */
internal fun liveOptIn(): Boolean = platformGetenv("JEV4K_LIVE") == "1"

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

/** The ticket most client tests send as their state. */
internal const val PAYOUT_TICKET = "Help! My payouts have been failing for 3 days."

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

/** A PNG signature and a few bytes: jev4k checks only the signature, and no test sends these to the real API. */
internal val PNG_BYTES =
    byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52)

/** A JPEG signature and a few bytes, as [PNG_BYTES] is for PNG. */
internal val JPEG_BYTES =
    byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10, 0x4A, 0x46)

/**
 * A reference this process generated, rewritten as another process would have generated it: every character of the
 * tag after `jev4k-image-` is changed.
 */
internal fun foreignReference(generated: String): String =
    generated.replaceRange(12, 20, generated.substring(12, 20).map { if (it == '0') '1' else '0' }.joinToString(""))

/** A Choice defined by example pictures, TypeSafe's "define options by example" pattern. */
object Gestures : JevQuery() {
    val thumb = attach(JevImage.of(PNG_BYTES, "<thumb>"))
    val palm = attach(JevImage.of(JPEG_BYTES, "<palm>"))
    val gesture by choice("Which gesture is the hand in `camera` making?") {
        "thumbs_up" means thumb
        "open_palm" means entry("description" to "A hand held up, palm to the camera.", "examples" to listOf(palm))
        "none" means "No hand, or a hand that matches none of the examples."
    }
}

/** One part of a multipart/form-data body: its headers and its bytes. */
internal class Part(
    val headers: Map<String, String>,
    val body: ByteArray,
) {
    val name: String
        get() =
            headers.getValue(HttpHeaders.ContentDisposition)
                .substringAfter("name=")
                .substringBefore(";")
                .removeSurrounding("\"")

    val text: String get() = body.decodeToString()
}

/** The parts of this request's multipart body. */
internal suspend fun HttpRequestData.parts(): List<Part> {
    val boundary =
        requireNotNull(body.contentType?.parameter("boundary")) { "not a multipart body: ${body.contentType}" }
    return multipartParts(body.toByteArray(), boundary)
}

/** Splits a multipart [bytes] body on [boundary]: enough for the bodies jev4k writes, whose parts tests choose. */
internal fun multipartParts(
    bytes: ByteArray,
    boundary: String,
): List<Part> {
    val delimiter = "--$boundary".encodeToByteArray()
    val starts =
        generateSequence(bytes.indexOf(delimiter, 0).takeIf { it >= 0 }) { previous ->
            bytes.indexOf(delimiter, previous + delimiter.size).takeIf { it >= 0 }
        }.toList()
    return starts.zipWithNext { from, to ->
        // "--boundary\r\n", the headers, a blank line, the body, and "\r\n" before the next delimiter.
        val part = bytes.copyOfRange(from + delimiter.size + 2, to - 2)
        val split = part.indexOf("\r\n\r\n".encodeToByteArray(), 0)
        Part(
            headers = part.copyOfRange(0, split).decodeToString().lines().associate {
                it.substringBefore(": ") to it.substringAfter(": ")
            },
            body = part.copyOfRange(split + 4, part.size),
        )
    }
}

private fun ByteArray.indexOf(
    needle: ByteArray,
    from: Int,
): Int = (from..size - needle.size).firstOrNull { start -> needle.indices.all { this[start + it] == needle[it] } } ?: -1

/** One camera frame of a [History], the docs' "what changed" pattern. */
@Serializable
data class Frame(
    val secondsAgo: Int,
    val frame: JevImage,
)

@Serializable
data class History(
    val frames: List<Frame>,
)
