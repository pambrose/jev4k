package com.pambrose.jev4k

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.HttpStatusCode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Every `Throwable` is `Serializable` on the JVM, and frameworks rely on it: RMI, JMS, and session or cache
 * replication all serialize exceptions. A field that isn't serializable makes that throw `NotSerializableException`.
 */
class ErrorsJvmTest : StringSpec() {
    @Suppress("UNCHECKED_CAST")
    private fun <T : Throwable> roundTrip(error: T): T {
        val bytes = ByteArrayOutputStream().also { out -> ObjectOutputStream(out).use { it.writeObject(error) } }
        return ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() as T }
    }

    init {
        "a rate-limit error survives Java serialization, with its JSON body and retry hint" {
            val error = jevApiException(
                429,
                body = """{"detail":"slow down"}""",
                requestId = "req-1",
                headers = mapOf("Retry-After" to listOf("2")),
            ).shouldBeInstanceOf<JevRateLimitException>()
            // Read before serializing, as a logger would, so nothing parsed lazily can be left in the object.
            error.bodyJson shouldNotBe null

            val copy = roundTrip(error)
            copy.status shouldBe 429
            copy.body shouldBe error.body
            copy.bodyJson shouldBe error.bodyJson
            copy.requestId shouldBe "req-1"
            copy.headers shouldBe mapOf("retry-after" to listOf("2"))
            copy.retryAfter shouldBe 2.seconds
            copy.message shouldBe error.message
        }

        "an error the client built from a real response survives it too" {
            val jev = testJev {
                respondJson("""{"detail":"busy"}""", HttpStatusCode.TooManyRequests, mapOf("retry-after-ms" to "750"))
            }
            val error = shouldThrow<JevRateLimitException> { jev.client.ask(Triage, state = PAYOUT_TICKET) }
            val copy = roundTrip(error)
            copy.retryAfter shouldBe 750.milliseconds
            copy.headers["retry-after-ms"] shouldBe listOf("750")
            copy.endpoint shouldBe error.endpoint
        }

        "a response validation error keeps its field path" {
            val error = shouldThrow<JevResponseValidationException> {
                jevResult("""{"answers":[]}""", Triage.questions)
            }
            roundTrip(error).fieldPath shouldBe error.fieldPath
        }
    }
}
