package com.pambrose.jev4k

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.serialization.json.jsonObject
import kotlin.time.Duration.Companion.seconds
import io.kotest.matchers.string.shouldContain

/** Application code that depends only on [JevApi], so it can be unit-tested with a mock. */
private suspend fun routeTicket(
    jev: JevApi,
    ticket: Ticket,
): String {
    val r = jev.ask(Triage, state = ticket)
    return if (r[Triage.urgent].isTrue(0.9)) "escalate" else r[Triage.department].choice.name
}

/** Builds its question, and so its handle, on every call, as inline queries do. */
private suspend fun urgency(
    jev: JevApi,
    text: String,
): Double {
    lateinit var urgent: QuestionRef<NoulAnswer>
    val result = jev.query(text) { urgent = noul("urgent", "Does this message convey urgency?") }
    return result[urgent].noul
}

class ConsumerTest : StringSpec() {
    // Built with the public jevResult factory, so this is an idiom a consumer of the published jar can copy.
    private fun cannedResult(noul: Double) =
        jevResult(TRIAGE_RESPONSE.replace("0.92", noul.toString()), Triage.questions)

    /** A [JevApi] that answers every request with the documented response, at the urgency you ask for. */
    private fun jevAnswering(noul: Double) =
        mockk<JevApi> {
            coEvery { evaluate(any(), any(), any()) } returns cannedResult(noul)
        }

    init {
        "code that uses JevApi can be tested with a MockK mock instead of HTTP" {
            val jev = jevAnswering(noul = 0.3)

            val ticket = Ticket("Payouts failing", "My payouts have been failing for 3 days.")
            routeTicket(jev, ticket) shouldBe "TECHNICAL"

            coVerify(exactly = 1) { jev.evaluate(jsonEntry(ticket), Triage.questions, null) }
            // The other half of the idiom: routeTicket made that one call and nothing else.
            confirmVerified(jev)
        }

        "the mock can drive the escalation branch" {
            routeTicket(jevAnswering(noul = 0.95), Ticket("Down", "Everything is down!")) shouldBe "escalate"
        }

        "jevResult replays a recorded response and validates it the same way the client does" {
            val r = jevResult(json(TRIAGE_RESPONSE).jsonObject, Triage.questions, model = "m", requestId = "req-7")
            r.requestId shouldBe "req-7"
            r.requestedModel shouldBe "m"
            r[Triage.urgent].noul shouldBe 0.92

            shouldThrow<JevResponseValidationException> {
                jevResult("""{"answers":{"urgent":{"type":"noul"}}}""", Triage.questions)
            }.fieldPath shouldBe "answers.urgent.noul"
        }

        "jevApiException builds the same typed errors the client raises, for testing error handling" {
            val jev = mockk<JevApi> {
                coEvery { evaluate(any(), any(), any()) } throws
                    jevApiException(429, body = """{"detail":"slow down"}""", retryAfter = 2.seconds)
            }
            val e = shouldThrow<JevRateLimitException> {
                routeTicket(jev, Ticket("Down", "Everything is down!"))
            }
            e.status shouldBe 429
            e.retryAfter shouldBe 2.seconds
            e.bodyJson shouldBe json("""{"detail":"slow down"}""")
        }

        // Code that builds its questions on each call sends a new QuestionSet, with new handles, every time.
        "a mock answers questions built per call by building the result from the set it was sent" {
            val body = """{"answers":{"urgent":{"type":"noul","noul":0.8}}}"""
            val perCall = mockk<JevApi> {
                coEvery { evaluate(any(), any(), any()) } answers { jevResult(body, secondArg()) }
            }
            urgency(perCall, "The payouts failed again.") shouldBe 0.8

            val fixed = mockk<JevApi> {
                coEvery { evaluate(any(), any(), any()) } returns jevResult(body, Triage.questions)
            }
            shouldThrow<IllegalArgumentException> { urgency(fixed, "The payouts failed again.") }
                .message shouldContain "through a different handle"
        }

        "jevResult checks the body as the client does, so a garbled response can be simulated" {
            shouldThrow<JevResponseValidationException> { jevResult("not json", Triage.questions) }
                .message shouldContain "body is not JSON"
            shouldThrow<JevResponseValidationException> { jevResult("[]", Triage.questions) }
                .message shouldContain "expected a JSON object"
        }
    }
}
