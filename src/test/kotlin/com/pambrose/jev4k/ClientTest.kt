package com.pambrose.jev4k

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.TestConfiguration
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.nio.channels.UnresolvedAddressException
import java.util.concurrent.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ClientTest : StringSpec() {
    private val payoutTicket = "Help! My payouts have been failing for 3 days."

    /** A client whose every request gets the documented triage response. */
    private fun TestConfiguration.triageJev() = testJev { respondJson(TRIAGE_RESPONSE) }

    private fun QueryBuilder.documentedQuestions() {
        noul("is_urgent", "Does this convey urgency?")
        choice("department", "Which team should handle this?") { options("billing", "technical", "sales") }
        score("frustration", "How frustrated is the customer?") { levels("Calm", "Frustrated", "Very angry") }
    }

    init {
        "query posts the documented request with auth and JSON headers" {
            val jev = testJev { respondJson(DOCUMENTED_RESPONSE) }
            jev.client.query(state = payoutTicket) { documentedQuestions() }

            val request = jev.requests.single()
            request.method shouldBe HttpMethod.Post
            request.url.toString() shouldBe "https://api.typesafe.ai/v1/systemone"
            request.headers[HttpHeaders.Authorization] shouldBe "Bearer test-key"
            request.headers[HttpHeaders.Accept] shouldBe "application/json"
            request.headers[HttpHeaders.UserAgent] shouldStartWith "jev4k/"
            request.body.contentType.toString() shouldStartWith "application/json"
            val body = request.bodyJson().jsonObject
            body["state"] shouldBe json("\"$payoutTicket\"")
            body["model"] shouldBe json("\"jev-latest\"")
            body.getValue("questions").jsonObject.keys shouldBe setOf("is_urgent", "department", "frustration")
        }

        "the response comes back typed, with the request id and reported model" {
            val jev = testJev {
                respondJson(DOCUMENTED_RESPONSE, extraHeaders = mapOf("x-typesafe-request-id" to "req-42"))
            }
            val r = jev.client.query(state = payoutTicket) { documentedQuestions() }
            r.noul("is_urgent").noul shouldBe 0.92
            r.choice("department").choice shouldBe "technical"
            r.score("frustration").score shouldBe 1.6
            r.requestId shouldBe "req-42"
            r.model shouldBe "jev-1.13.0"
            r.usage shouldBe Usage(312, 48)
        }

        "ask sends a JevQuery and reads typed answers through its handles" {
            val jev = triageJev()
            val r = jev.client.ask(Triage, state = payoutTicket)
            r[Triage.department].choice shouldBe Dept.TECHNICAL
            r[Triage.urgent].noul shouldBe 0.92
            r[Triage.frustration].normalized shouldBe 0.8
            jev.requests.single().bodyJson().jsonObject["questions"] shouldBe Triage.questions.toJson()
        }

        "a per-call model overrides the configured default" {
            val jev = testJev(configure = { defaultModel = "jev-1.13.0" }) { respondJson(TRIAGE_RESPONSE) }
            jev.client.ask(Triage, state = payoutTicket)
            jev.client.ask(Triage, state = payoutTicket, model = "jev-preview")
            jev.requests.map { it.bodyJson().jsonObject["model"] } shouldBe
                listOf(json("\"jev-1.13.0\""), json("\"jev-preview\""))
        }

        "a base URL with a path prefix and trailing slash is honored" {
            val jev = testJev(configure = { baseUrl = "https://proxy.example/jev/" }) { respondJson(TRIAGE_RESPONSE) }
            jev.client.ask(Triage, state = payoutTicket)
            jev.requests.single().url.toString() shouldBe "https://proxy.example/jev/v1/systemone"
        }

        "a @Serializable state is encoded with its default-valued fields" {
            val jev = triageJev()
            jev.client.ask(Triage, state = Order("A-104"))
            jev.requests.single().bodyJson().jsonObject["state"] shouldBe
                json("""{"id":"A-104","status":"open","items":[]}""")
        }

        "a JsonElement state is sent as-is" {
            val jev = triageJev()
            val state = buildJsonObject { put("ticket", payoutTicket) }
            jev.client.ask(Triage, state = state)
            jev.requests.single().bodyJson().jsonObject["state"] shouldBe state
        }

        "extra configured headers are sent" {
            val jev = testJev(configure = { headers["X-Team"] = "support" }) { respondJson(TRIAGE_RESPONSE) }
            jev.client.ask(Triage, state = payoutTicket)
            jev.requests.single().headers["X-Team"] shouldBe "support"
        }

        "invalid requests fail before anything is sent" {
            val jev = triageJev()
            shouldThrow<JevValidationException> { jev.client.evaluate(JsonNull, Triage.questions) }
            shouldThrow<JevValidationException> { jev.client.query(state = payoutTicket) { } }
            jev.requests.shouldBeEmpty()
        }

        "models lists the available models" {
            val jev = testJev {
                respondJson(
                    """{"models":[{"name":"jev-latest","description":"Latest stable","release_date":"2026-09-01"}]}""",
                )
            }
            jev.client.models() shouldBe listOf(ModelInfo("jev-latest", "Latest stable", "2026-09-01"))
            jev.requests.single().method shouldBe HttpMethod.Get
            jev.requests.single().url.toString() shouldBe "https://api.typesafe.ai/v1/models"
        }

        "the blocking wrapper mirrors the suspend API" {
            val jev = triageJev()
            jev.client.blocking.ask(Triage, state = payoutTicket)[Triage.department].choice shouldBe Dept.TECHNICAL
            jev.client.blocking.query(state = Order("A-104")) { include(Triage) }[Triage.urgent].noul shouldBe 0.92
        }

        "HTTP errors map to typed exceptions that keep the body and request id" {
            val cases = mapOf(
                400 to JevBadRequestException::class,
                401 to JevAuthenticationException::class,
                403 to JevPermissionDeniedException::class,
                404 to JevNotFoundException::class,
                408 to JevApiException::class,
                409 to JevApiException::class,
                422 to JevUnprocessableEntityException::class,
                429 to JevRateLimitException::class,
                500 to JevInternalServerException::class,
                503 to JevInternalServerException::class,
                529 to JevOverloadedException::class,
            )
            for ((status, type) in cases) {
                withClue("HTTP $status") {
                    val jev = testJev {
                        respondJson(
                            """{"detail":"status $status"}""",
                            HttpStatusCode.fromValue(status),
                            mapOf("x-typesafe-request-id" to "req-$status"),
                        )
                    }
                    val e = shouldThrow<JevApiException> { jev.client.ask(Triage, state = payoutTicket) }
                    e::class shouldBe type
                    e.status shouldBe status
                    e.requestId shouldBe "req-$status"
                    e.body shouldBe """{"detail":"status $status"}"""
                    e.bodyJson shouldBe json("""{"detail":"status $status"}""")
                    e.message shouldContain "$status"
                    e.message shouldContain "req-$status"
                    e.message shouldNotContain "test-key"
                }
            }
        }

        // bodyJson parses lazily and catches only SerializationException, so a gateway's HTML error page must
        // come back as a null bodyJson rather than throwing out of the property.
        "an error body that isn't JSON is kept raw, with a null bodyJson" {
            val html = "<html><body>502 Bad Gateway</body></html>"
            val jev = testJev { respondJson(html, HttpStatusCode.BadGateway) }
            val e = shouldThrow<JevInternalServerException> { jev.client.ask(Triage, state = payoutTicket) }
            e.body shouldBe html
            e.bodyJson shouldBe null
        }

        "an empty error body leaves bodyJson null" {
            val jev = testJev { respondJson("", HttpStatusCode.BadGateway) }
            val e = shouldThrow<JevInternalServerException> { jev.client.ask(Triage, state = payoutTicket) }
            e.bodyJson shouldBe null
        }

        "a rate-limit error carries the server's retry hint" {
            val jev = testJev { respondJson("{}", HttpStatusCode.TooManyRequests, mapOf("retry-after-ms" to "1500")) }
            val e = shouldThrow<JevRateLimitException> { jev.client.ask(Triage, state = payoutTicket) }
            e.retryAfter shouldBe 1500.milliseconds
        }

        "a success body that isn't JSON at all is a response error that keeps the body" {
            val jev = testJev { respondJson("not json") }
            val e = shouldThrow<JevResponseValidationException> { jev.client.ask(Triage, state = payoutTicket) }
            e.message shouldContain "body is not JSON"
            e.fieldPath shouldBe null
            e.status shouldBe 200
            e.body shouldBe "not json"
        }

        "a success body that is JSON but not an object is a separate response error" {
            val jev = testJev { respondJson("[1, 2]") }
            val e = shouldThrow<JevResponseValidationException> { jev.client.ask(Triage, state = payoutTicket) }
            e.message shouldContain "expected a JSON object"
            e.fieldPath shouldBe null
            e.body shouldBe "[1, 2]"
        }

        "a 429 is retried after the retry-after-ms hint" {
            var calls = 0
            val jev = testJev(retry = RetryPolicy()) {
                if (++calls == 1)
                    respondJson("{}", HttpStatusCode.TooManyRequests, mapOf("retry-after-ms" to "250"))
                else
                    respondJson(TRIAGE_RESPONSE)
            }
            jev.client.ask(Triage, state = payoutTicket)[Triage.department].choice shouldBe Dept.TECHNICAL
            jev.requests shouldHaveSize 2
            jev.delays shouldBe listOf(250L)
        }

        "a Retry-After header in seconds is honored, and a hint over the cap falls back to backoff" {
            var calls = 0
            val jev = testJev(retry = RetryPolicy()) {
                when (++calls) {
                    1 -> respondJson("{}", HttpStatusCode.ServiceUnavailable, mapOf(HttpHeaders.RetryAfter to "2"))
                    2 -> respondJson("{}", HttpStatusCode.ServiceUnavailable, mapOf(HttpHeaders.RetryAfter to "120"))
                    else -> respondJson(TRIAGE_RESPONSE)
                }
            }
            jev.client.ask(Triage, state = payoutTicket)
            jev.delays shouldBe listOf(2000L, 1000L)
        }

        "repeated overload errors are retried with exponential backoff, then surface" {
            val jev = testJev(retry = RetryPolicy()) { respondJson("{}", HttpStatusCode.fromValue(529)) }
            shouldThrow<JevOverloadedException> { jev.client.ask(Triage, state = payoutTicket) }
            jev.requests shouldHaveSize 3
            jev.delays shouldBe listOf(500L, 1000L)
        }

        "client errors other than 408 and 429 are not retried" {
            val jev = testJev(retry = RetryPolicy()) { respondJson("{}", HttpStatusCode.UnprocessableEntity) }
            shouldThrow<JevUnprocessableEntityException> { jev.client.ask(Triage, state = payoutTicket) }
            jev.requests shouldHaveSize 1
            jev.delays.shouldBeEmpty()
        }

        "connection errors are retried, then surface as JevConnectionException" {
            var calls = 0
            val jev = testJev(retry = RetryPolicy()) {
                calls++
                throw ConnectException("Connection refused")
            }
            val e = shouldThrow<JevConnectionException> { jev.client.ask(Triage, state = payoutTicket) }
            e.cause.shouldBeInstanceOf<ConnectException>()
            calls shouldBe 3
        }

        "connection errors are not retried when the policy says so" {
            var calls = 0
            val jev = testJev(retry = RetryPolicy(retryOnConnectionError = false)) {
                calls++
                throw ConnectException("Connection refused")
            }
            shouldThrow<JevConnectionException> { jev.client.ask(Triage, state = payoutTicket) }
            calls shouldBe 1
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

        // MockEngine records a request only once its handler returns, so a cancelled attempt never reaches
        // requestHistory. Counting inside the handler is the only way to see the attempts.
        "a timeout is retried, and retryOnTimeout = false switches that off" {
            var retried = 0
            val jev = testJev(retry = RetryPolicy(), configure = { timeout = 50.milliseconds }) {
                retried++
                awaitCancellation()
            }
            shouldThrow<JevTimeoutException> { jev.client.ask(Triage, state = payoutTicket) }
            retried shouldBe 3
            jev.delays shouldBe listOf(500L, 1000L)

            var once = 0
            val noRetry = testJev(
                retry = RetryPolicy(retryOnTimeout = false),
                configure = { timeout = 50.milliseconds },
            ) {
                once++
                awaitCancellation()
            }
            shouldThrow<JevTimeoutException> { noRetry.client.ask(Triage, state = payoutTicket) }
            once shouldBe 1
            noRetry.delays.shouldBeEmpty()
        }

        "the request body is sent as JSON text" {
            val jev = triageJev()
            jev.client.ask(Triage, state = payoutTicket)
            val body = jev.requests.single().body.shouldBeInstanceOf<TextContent>()
            body.contentType.withoutParameters() shouldBe ContentType.Application.Json
            json(body.text).jsonObject.keys shouldBe setOf("state", "model", "questions")
        }

        // An embedding app may hand the same Ktor engine to several clients; closing ours must not shut theirs down.
        "closing a client leaves a caller-supplied engine usable" {
            val engine = autoClose(MockEngine { respondJson(TRIAGE_RESPONSE) })
            val config: JevConfigBuilder.() -> Unit = {
                apiKey = "test-key"
                this.engine = engine
                env = { null }
            }

            JevClient(config).use { it.ask(Triage, state = payoutTicket) }

            JevClient(config).use { second ->
                second.ask(Triage, state = payoutTicket)[Triage.urgent].noul shouldBe 0.92
            }
        }

        "RetryPolicy.NONE sends one request and waits for nothing" {
            val jev = testJev(retry = RetryPolicy.NONE) { respondJson("{}", HttpStatusCode.ServiceUnavailable) }
            shouldThrow<JevInternalServerException> { jev.client.ask(Triage, state = payoutTicket) }
            jev.requests shouldHaveSize 1
            jev.delays.shouldBeEmpty()
        }

        "a custom maxRetries changes the number of attempts and the backoff sequence" {
            val jev = testJev(retry = RetryPolicy(maxRetries = 4)) {
                respondJson("{}", HttpStatusCode.ServiceUnavailable)
            }
            shouldThrow<JevInternalServerException> { jev.client.ask(Triage, state = payoutTicket) }
            jev.requests shouldHaveSize 5
            jev.delays shouldBe listOf(500L, 1000L, 2000L, 4000L)
        }

        "a status left out of retryStatuses is not retried" {
            val jev = testJev(retry = RetryPolicy(retryStatuses = setOf(429))) {
                respondJson("{}", HttpStatusCode.ServiceUnavailable)
            }
            shouldThrow<JevInternalServerException> { jev.client.ask(Triage, state = payoutTicket) }
            jev.requests shouldHaveSize 1
        }

        "408 is retried like 429 and 5xx" {
            val jev = testJev(retry = RetryPolicy()) { respondJson("{}", HttpStatusCode.RequestTimeout) }
            shouldThrow<JevApiException> { jev.client.ask(Triage, state = payoutTicket) }
            jev.requests shouldHaveSize 3
            jev.delays shouldBe listOf(500L, 1000L)
        }

        "a host that doesn't resolve is a connection error naming the host" {
            var calls = 0
            val jev = testJev(retry = RetryPolicy()) {
                calls++
                throw UnresolvedAddressException()
            }
            val e = shouldThrow<JevConnectionException> { jev.client.ask(Triage, state = payoutTicket) }
            e.message shouldContain "Could not resolve the host"
            calls shouldBe 3
        }

        "connect and socket timeouts surface as JevTimeoutException" {
            for (cause in listOf(ConnectTimeoutException("too slow"), SocketTimeoutException("too slow"))) {
                withClue(cause::class.simpleName.orEmpty()) {
                    val jev = testJev { throw cause }
                    val e = shouldThrow<JevTimeoutException> { jev.client.ask(Triage, state = payoutTicket) }
                    e.message shouldContain "timed out after"
                }
            }
        }

        // A genuine cancellation is not a failure to retry: the call must abandon its attempt and stay cancelled.
        "a cancelled call is not retried and stays cancelled" {
            var calls = 0
            val jev = testJev(retry = RetryPolicy()) {
                calls++
                awaitCancellation()
            }
            val scope = CoroutineScope(Dispatchers.Default)
            try {
                val call = scope.async { jev.client.ask(Triage, state = payoutTicket) }
                eventually(5.seconds) { calls shouldBe 1 }
                call.cancel()
                shouldThrow<CancellationException> { call.await() }
                calls shouldBe 1
                jev.delays.shouldBeEmpty()
            } finally {
                scope.cancel()
            }
        }

        "an API error carries the endpoint, the headers, and a truncated body in its message" {
            val long = "x".repeat(900)
            val jev = testJev {
                respondJson("\"$long\"", HttpStatusCode.BadGateway, mapOf("X-Trace" to "abc"))
            }
            val e = shouldThrow<JevInternalServerException> { jev.client.ask(Triage, state = payoutTicket) }
            e.endpoint shouldBe "POST https://api.typesafe.ai/v1/systemone"
            e.headers["X-Trace"] shouldBe listOf("abc")
            e.message shouldContain "HTTP 502 from POST https://api.typesafe.ai/v1/systemone"
            // The body is kept whole, and only the copy in the message is trimmed, since that is what is logged.
            e.body!!.length shouldBe long.length + 2
            e.message!!.length shouldBeLessThan e.body.length
        }

        "a rate-limit error has a null retryAfter when the server sent no usable hint" {
            for (headers in listOf(emptyMap(), mapOf("retry-after-ms" to "NaN"), mapOf("Retry-After" to "-1"))) {
                withClue(headers.toString()) {
                    val jev = testJev { respondJson("{}", HttpStatusCode.TooManyRequests, headers) }
                    val e = shouldThrow<JevRateLimitException> { jev.client.ask(Triage, state = payoutTicket) }
                    e.retryAfter shouldBe null
                }
            }
        }

        "a Retry-After in seconds reaches the exception as a Duration" {
            val jev = testJev { respondJson("{}", HttpStatusCode.TooManyRequests, mapOf("Retry-After" to "2")) }
            shouldThrow<JevRateLimitException> { jev.client.ask(Triage, state = payoutTicket) }
                .retryAfter shouldBe 2.seconds
        }

        // header(), which appends, would leave two Authorization or User-Agent values on the request.
        "a configured header replaces the built-in one of the same name" {
            val jev = testJev(configure = { headers[HttpHeaders.UserAgent] = "my-app/1.0" }) {
                respondJson(TRIAGE_RESPONSE)
            }
            jev.client.ask(Triage, state = payoutTicket)
            jev.requests.single().headers.getAll(HttpHeaders.UserAgent) shouldBe listOf("my-app/1.0")
        }

        "a state jev4k can't encode fails as a validation error, not a serialization error" {
            val jev = triageJev()
            shouldThrow<JevValidationException> { jev.client.ask(Triage, state = jsonOf(Double.NaN)) }
            // A hand-built JsonElement skips jsonOf, so this one fails while the body is being written.
            val nonFinite = buildJsonObject { put("score", JsonPrimitive(Double.POSITIVE_INFINITY)) }
            shouldThrow<JevValidationException> { jev.client.ask(Triage, state = nonFinite) }
            jev.requests.shouldBeEmpty()
        }
    }
}
