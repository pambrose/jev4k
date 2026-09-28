package com.pambrose.jev4k

import com.pambrose.jev4k.internal.JEV4K_VERSION
import com.pambrose.jev4k.internal.MAX_JSON_DEPTH
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.awaitCancellation
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CallOptionsTest : StringSpec() {
    private val ticket = JsonPrimitive(PAYOUT_TICKET)

    init {
        "a call's timeout replaces the client's, and the timeout error quotes it" {
            val jev = testJev(configure = { timeout = 10.seconds }) { awaitCancellation() }
            val fast = JevCallOptions { timeout = 50.milliseconds }
            shouldThrow<JevTimeoutException> { jev.client.withOptions(fast).ask(Triage, state = PAYOUT_TICKET) }
                .message shouldContain "timed out after 50ms"
        }

        "a call's timeout applies to each of its retries" {
            var attempts = 0
            val jev = testJev {
                attempts++
                awaitCancellation()
            }
            val options = JevCallOptions {
                timeout = 50.milliseconds
                retry = RetryPolicy()
            }
            shouldThrow<JevTimeoutException> { jev.client.evaluate(ticket, Triage.questions, null, options) }
            attempts shouldBe 3
            jev.delays shouldBe listOf(500L, 1000L)
        }

        "a call's retry policy replaces the client's, in both directions" {
            val retrying = testJev(retry = RetryPolicy()) { respondJson("{}", HttpStatusCode.ServiceUnavailable) }
            val once = JevCallOptions { retry = RetryPolicy.NONE }
            shouldThrow<JevInternalServerException> { retrying.client.evaluate(ticket, Triage.questions, null, once) }
            retrying.requests shouldHaveSize 1
            retrying.delays.shouldBeEmpty()

            val single = testJev { respondJson("{}", HttpStatusCode.ServiceUnavailable) }
            val twice = JevCallOptions { retry = RetryPolicy(maxRetries = 2) }
            shouldThrow<JevInternalServerException> { single.client.evaluate(ticket, Triage.questions, null, twice) }
            single.requests shouldHaveSize 3
            single.delays shouldBe listOf(500L, 1000L)
        }

        // DefaultRequest merges its headers with a request's own, so a header named in both would go out twice.
        "a call's headers replace the client's and the built-in ones, and each is sent once" {
            val jev = testJev(configure = { headers["X-Team"] = "support" }) { respondJson(TRIAGE_RESPONSE) }
            val options = JevCallOptions {
                headers["X-Team"] = "billing"
                headers["X-Trace-Id"] = "t-1"
                headers[HttpHeaders.Authorization] = "Bearer other-key"
            }
            jev.client.withOptions(options).ask(Triage, state = PAYOUT_TICKET)
            val sent = jev.requests.single().headers
            sent.getAll("X-Team") shouldBe listOf("billing")
            sent.getAll("X-Trace-Id") shouldBe listOf("t-1")
            sent.getAll(HttpHeaders.Authorization) shouldBe listOf("Bearer other-key")
            sent.getAll(HttpHeaders.UserAgent) shouldBe listOf("jev4k/$JEV4K_VERSION")
        }

        "extraBody fields are added to the request body, nulls included" {
            val jev = triageJev()
            val options = JevCallOptions {
                extraBody["beam_width"] = JsonPrimitive(4)
                extraBody["tag"] = JsonNull
            }
            jev.client.evaluate(ticket, Triage.questions, null, options)
            val body = jev.requests.single().bodyJson().jsonObject
            body.keys shouldBe setOf("state", "model", "questions", "beam_width", "tag")
            body["beam_width"] shouldBe JsonPrimitive(4)
            body["tag"] shouldBe JsonNull
            body["model"] shouldBe JsonPrimitive(JevDefaults.MODEL)
        }

        "models takes options too, and sends no body for extraBody" {
            val jev = testJev { respondJson("""{"models":[]}""") }
            val options = JevCallOptions {
                headers["X-Trace-Id"] = "t-2"
                extraBody["seed"] = JsonPrimitive(1)
            }
            jev.client.withOptions(options).models().shouldBeEmpty()
            jev.requests.single().headers["X-Trace-Id"] shouldBe "t-2"
            jev.requests.single().body.contentLength shouldBe 0
        }

        "options are checked when they are built, every problem at once, without echoing a header value" {
            val e = shouldThrow<JevConfigException> {
                JevCallOptions {
                    timeout = 500.microseconds
                    headers["X-Secret"] = "abc\ndef"
                    extraBody["state"] = JsonPrimitive("elsewhere")
                    extraBody["questions"] = JsonNull
                    extraBody["deep"] = nestedArrays(MAX_JSON_DEPTH + 1)
                    extraBody["nan"] = JsonPrimitive(Double.NaN)
                }
            }
            e.message shouldContain "extraBody 'nan' holds NaN or an infinity"
            e.message shouldContain "timeout must be at least 1 millisecond"
            e.message shouldContain "header 'X-Secret' has a control character"
            e.message shouldContain "extraBody can't set 'state'"
            e.message shouldContain "extraBody can't set 'questions'"
            e.message shouldContain "extraBody 'deep' is nested more than $MAX_JSON_DEPTH levels deep"
            e.message shouldNotContain "abc"
        }

        "withOptions merges the options of a call, and of a nested withOptions, over its own" {
            val jev = triageJev()
            val outer = jev.client.withOptions(
                JevCallOptions {
                    headers["X-A"] = "outer"
                    headers["X-B"] = "outer"
                    headers["X-C"] = "outer"
                },
            )
            val inner = outer.withOptions(
                JevCallOptions {
                    headers["X-B"] = "inner"
                    headers["X-C"] = "inner"
                },
            )
            inner.evaluate(ticket, Triage.questions, null, JevCallOptions { headers["X-C"] = "call" })
            val sent = jev.requests.single().headers
            sent["X-A"] shouldBe "outer"
            sent["X-B"] shouldBe "inner"
            sent["X-C"] shouldBe "call"
        }

        "a JevApi that doesn't override the options members ignores them" {
            val fake = object : JevApi {
                override suspend fun evaluate(
                    state: JsonElement,
                    questions: QuestionSet,
                    model: String?,
                ): JevResult = jevResult(TRIAGE_RESPONSE, questions)

                override suspend fun models(): ModelList = ModelList(listOf(ModelInfo("jev-latest", null, null)))
            }
            val fast = fake.withOptions(JevCallOptions { timeout = 1.seconds })
            fast.ask(Triage, state = PAYOUT_TICKET)[Triage.urgent].noul shouldBe 0.92
            fast.models().single().name shouldBe "jev-latest"
        }

        "the builder's maps and the policy's statuses are copied, so a later change to them changes nothing" {
            val statuses = mutableSetOf(503)
            val builder = JevCallOptionsBuilder().apply {
                headers["X-A"] = "1"
                extraBody["seed"] = JsonPrimitive(1)
                retry = RetryPolicy(retryStatuses = statuses)
            }
            val options = builder.build()
            builder.headers["X-A"] = "2"
            builder.extraBody["seed"] = JsonPrimitive(2)
            statuses += 500
            options.headers shouldBe mapOf("X-A" to "1")
            options.extraBody shouldBe mapOf("seed" to JsonPrimitive(1))
            options.retry?.retryStatuses shouldBe setOf(503)
        }

        "timeoutMillis sets the timeout, for Java" {
            JevCallOptions { timeoutMillis = 1500 }.timeout shouldBe 1500.milliseconds
            JevCallOptionsBuilder().timeoutMillis shouldBe null
        }

        "toString names the headers and body fields without their values" {
            val options = JevCallOptions {
                headers[HttpHeaders.Authorization] = "Bearer secret"
                extraBody["seed"] = JsonPrimitive("hidden")
            }
            options.toString() shouldContain "Authorization"
            options.toString() shouldContain "seed"
            options.toString() shouldNotContain "secret"
            options.toString() shouldNotContain "hidden"
        }
    }
}
