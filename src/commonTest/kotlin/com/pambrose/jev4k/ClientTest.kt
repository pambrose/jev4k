package com.pambrose.jev4k

import com.pambrose.jev4k.internal.JEV4K_VERSION
import com.pambrose.jev4k.internal.MAX_JSON_DEPTH
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
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
import io.ktor.client.engine.mock.respond
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.util.network.UnresolvedAddressException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.time.measureTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import io.ktor.http.fromHttpToGmtDate
import com.pambrose.jev4k.internal.MAX_RESPONSE_BYTES
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.plugins.HttpTimeoutConfig
import com.pambrose.jev4k.internal.limitTo

class ClientTest : StringSpec() {
    private fun QueryBuilder.documentedQuestions() {
        noul("is_urgent", "Does this convey urgency?")
        choice("department", "Which team should handle this?") { options("billing", "technical", "sales") }
        score("frustration", "How frustrated is the customer?") { levels("Calm", "Frustrated", "Very angry") }
    }

    init {
        "query posts the documented request with auth and JSON headers" {
            val jev = testJev { respondJson(DOCUMENTED_RESPONSE) }
            jev.client.query(state = PAYOUT_TICKET) { documentedQuestions() }

            val request = jev.requests.single()
            request.method shouldBe HttpMethod.Post
            request.url.toString() shouldBe "https://api.typesafe.ai/v1/systemone"
            request.headers[HttpHeaders.Authorization] shouldBe "Bearer test-key"
            request.headers[HttpHeaders.Accept] shouldBe "application/json"
            request.headers[HttpHeaders.UserAgent] shouldBe "jev4k/$JEV4K_VERSION"
            request.body.contentType.toString() shouldStartWith "application/json"
            val body = request.bodyJson().jsonObject
            body["state"] shouldBe json("\"$PAYOUT_TICKET\"")
            body["model"] shouldBe json("\"jev-latest\"")
            body.getValue("questions").jsonObject.keys shouldBe setOf("is_urgent", "department", "frustration")
        }

        "the response comes back typed, with the request id and reported model" {
            val jev = testJev {
                respondJson(DOCUMENTED_RESPONSE, extraHeaders = mapOf("x-typesafe-request-id" to "req-42"))
            }
            val r = jev.client.query(state = PAYOUT_TICKET) { documentedQuestions() }
            r.noul("is_urgent").noul shouldBe 0.92
            r.choice("department").choice shouldBe "technical"
            r.score("frustration").score shouldBe 1.6
            r.requestId shouldBe "req-42"
            r.model shouldBe "jev-1.13.0"
            r.usage shouldBe Usage(312, 48)
        }

        "ask sends a JevQuery and reads typed answers through its handles" {
            val jev = triageJev()
            val r = jev.client.ask(Triage, state = PAYOUT_TICKET)
            r[Triage.department].choice shouldBe Dept.TECHNICAL
            r[Triage.urgent].noul shouldBe 0.92
            r[Triage.frustration].normalized shouldBe 0.8
            jev.requests.single().bodyJson().jsonObject["questions"] shouldBe Triage.questions.toJson()
        }

        "a per-call model overrides the configured default" {
            val jev = testJev(configure = { defaultModel = "jev-1.13.0" }) { respondJson(TRIAGE_RESPONSE) }
            jev.client.ask(Triage, state = PAYOUT_TICKET)
            jev.client.ask(Triage, state = PAYOUT_TICKET, model = "jev-preview")
            jev.requests.map { it.bodyJson().jsonObject["model"] } shouldBe
                listOf(json("\"jev-1.13.0\""), json("\"jev-preview\""))
        }

        "a base URL with a path prefix and trailing slash is honored" {
            val jev = testJev(configure = { baseUrl = "https://proxy.example/jev/" }) { respondJson(TRIAGE_RESPONSE) }
            jev.client.ask(Triage, state = PAYOUT_TICKET)
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
            val state = buildJsonObject { put("ticket", PAYOUT_TICKET) }
            jev.client.ask(Triage, state = state)
            jev.requests.single().bodyJson().jsonObject["state"] shouldBe state
        }

        "extra configured headers are sent" {
            val jev = testJev(configure = { headers["X-Team"] = "support" }) { respondJson(TRIAGE_RESPONSE) }
            jev.client.ask(Triage, state = PAYOUT_TICKET)
            jev.requests.single().headers["X-Team"] shouldBe "support"
        }

        "invalid requests fail before anything is sent" {
            val jev = triageJev()
            shouldThrow<JevValidationException> { jev.client.evaluate(JsonNull, Triage.questions) }
            shouldThrow<JevValidationException> { jev.client.query(state = PAYOUT_TICKET) { } }
            jev.requests.shouldBeEmpty()
        }

        "models lists the available models, with the call's request id" {
            val jev = testJev {
                respondJson(
                    """{"models":[{"name":"jev-latest","description":"Latest stable","release_date":"2026-09-01"}]}""",
                    extraHeaders = mapOf(JevDefaults.REQUEST_ID_HEADER to "req-models"),
                )
            }
            val models = jev.client.models()
            models shouldBe listOf(ModelInfo("jev-latest", "Latest stable", "2026-09-01"))
            models.requestId shouldBe "req-models"
            jev.requests.single().method shouldBe HttpMethod.Get
            jev.requests.single().url.toString() shouldBe "https://api.typesafe.ai/v1/models"
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
                    val e = shouldThrow<JevApiException> { jev.client.ask(Triage, state = PAYOUT_TICKET) }
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
            val e = shouldThrow<JevInternalServerException> { jev.client.ask(Triage, state = PAYOUT_TICKET) }
            e.body shouldBe html
            e.bodyJson shouldBe null
        }

        "an empty error body leaves bodyJson null" {
            val jev = testJev { respondJson("", HttpStatusCode.BadGateway) }
            val e = shouldThrow<JevInternalServerException> { jev.client.ask(Triage, state = PAYOUT_TICKET) }
            e.bodyJson shouldBe null
        }

        "a rate-limit error carries the server's retry hint" {
            val jev = testJev { respondJson("{}", HttpStatusCode.TooManyRequests, mapOf("retry-after-ms" to "1500")) }
            val e = shouldThrow<JevRateLimitException> { jev.client.ask(Triage, state = PAYOUT_TICKET) }
            e.retryAfter shouldBe 1500.milliseconds
        }

        "a success body that isn't JSON at all is a response error that keeps the body" {
            val jev = testJev { respondJson("not json") }
            val e = shouldThrow<JevResponseValidationException> { jev.client.ask(Triage, state = PAYOUT_TICKET) }
            e.message shouldContain "body is not JSON"
            e.fieldPath shouldBe null
            e.status shouldBe 200
            e.body shouldBe "not json"
        }

        "a success body that is JSON but not an object is a separate response error" {
            val jev = testJev { respondJson("[1, 2]") }
            val e = shouldThrow<JevResponseValidationException> { jev.client.ask(Triage, state = PAYOUT_TICKET) }
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
            jev.client.ask(Triage, state = PAYOUT_TICKET)[Triage.department].choice shouldBe Dept.TECHNICAL
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
            jev.client.ask(Triage, state = PAYOUT_TICKET)
            jev.delays shouldBe listOf(2000L, 1000L)
        }

        "repeated overload errors are retried with exponential backoff, then surface" {
            val jev = testJev(retry = RetryPolicy()) { respondJson("{}", HttpStatusCode.fromValue(529)) }
            shouldThrow<JevOverloadedException> { jev.client.ask(Triage, state = PAYOUT_TICKET) }
            jev.requests shouldHaveSize 3
            jev.delays shouldBe listOf(500L, 1000L)
        }

        "client errors other than 408 and 429 are not retried" {
            val jev = testJev(retry = RetryPolicy()) { respondJson("{}", HttpStatusCode.UnprocessableEntity) }
            shouldThrow<JevUnprocessableEntityException> { jev.client.ask(Triage, state = PAYOUT_TICKET) }
            jev.requests shouldHaveSize 1
            jev.delays.shouldBeEmpty()
        }

        "connection errors are retried, then surface as JevConnectionException" {
            var calls = 0
            val jev = testJev(retry = RetryPolicy()) {
                calls++
                throw IOException("Connection refused")
            }
            val e = shouldThrow<JevConnectionException> { jev.client.ask(Triage, state = PAYOUT_TICKET) }
            e.cause.shouldBeInstanceOf<IOException>()
            calls shouldBe 3
        }

        "connection errors are not retried when the policy says so" {
            var calls = 0
            val jev = testJev(retry = RetryPolicy(retryOnConnectionError = false)) {
                calls++
                throw IOException("Connection refused")
            }
            shouldThrow<JevConnectionException> { jev.client.ask(Triage, state = PAYOUT_TICKET) }
            calls shouldBe 1
        }

        // MockEngine records a request only once its handler returns, so a cancelled attempt never reaches
        // requestHistory. Counting inside the handler is the only way to see the attempts.
        "a timeout is retried, and retryOnTimeout = false switches that off" {
            var retried = 0
            val jev = testJev(retry = RetryPolicy(), configure = { timeout = 50.milliseconds }) {
                retried++
                awaitCancellation()
            }
            shouldThrow<JevTimeoutException> { jev.client.ask(Triage, state = PAYOUT_TICKET) }
                .message shouldContain "timed out after 50ms"
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
            shouldThrow<JevTimeoutException> { noRetry.client.ask(Triage, state = PAYOUT_TICKET) }
            once shouldBe 1
            noRetry.delays.shouldBeEmpty()
        }

        // Pinned byte for byte: compact JSON in declaration order, a Content-Type with no charset parameter, and a
        // single Accept. It is what the Ktor ContentNegotiation plugin sent before jev4k encoded the body itself.
        "the request is sent as compact JSON text, application/json, with one Accept header" {
            val jev = triageJev()
            jev.client.ask(Triage, state = PAYOUT_TICKET)
            val request = jev.requests.single()
            val body = request.body.shouldBeInstanceOf<TextContent>()
            body.contentType.toString() shouldBe "application/json"
            body.text shouldBe
                """{"state":"Help! My payouts have been failing for 3 days.","model":"jev-latest","questions":""" +
                """{"urgent":{"type":"noul","instructions":"Does this convey urgency?","criteria":""" +
                """{"true":"Explicitly time-sensitive","false":"No urgency expressed"}},"department":""" +
                """{"type":"choice","instructions":"Which team should handle this?","criteria":""" +
                """{"billing":"Payments, invoicing, refunds","technical":"Bugs, outages, integrations",""" +
                """"sales":"Pricing, upgrades, new accounts"}},"frustration":{"type":"score",""" +
                """"instructions":"How frustrated is the customer?","criteria":["Calm","Frustrated","Very angry"]}}}"""
            request.headers.getAll(HttpHeaders.Accept) shouldBe listOf("application/json")
        }

        // An embedding app may hand the same Ktor engine to several clients; closing ours must not shut theirs down.
        "closing a client leaves a caller-supplied engine usable" {
            val engine = closeAfterSpec(MockEngine { respondJson(TRIAGE_RESPONSE) })
            val config: JevConfigBuilder.() -> Unit = {
                apiKey = "test-key"
                this.engine = engine
                env = { null }
            }

            JevClient(config).use { it.ask(Triage, state = PAYOUT_TICKET) }

            JevClient(config).use { second ->
                second.ask(Triage, state = PAYOUT_TICKET)[Triage.urgent].noul shouldBe 0.92
            }
        }

        "RetryPolicy.NONE sends one request and waits for nothing" {
            val jev = testJev(retry = RetryPolicy.NONE) { respondJson("{}", HttpStatusCode.ServiceUnavailable) }
            shouldThrow<JevInternalServerException> { jev.client.ask(Triage, state = PAYOUT_TICKET) }
            jev.requests shouldHaveSize 1
            jev.delays.shouldBeEmpty()
        }

        "a custom maxRetries changes the number of attempts and the backoff sequence" {
            val jev = testJev(retry = RetryPolicy(maxRetries = 4)) {
                respondJson("{}", HttpStatusCode.ServiceUnavailable)
            }
            shouldThrow<JevInternalServerException> { jev.client.ask(Triage, state = PAYOUT_TICKET) }
            jev.requests shouldHaveSize 5
            jev.delays shouldBe listOf(500L, 1000L, 2000L, 4000L)
        }

        "a status left out of retryStatuses is not retried" {
            val jev = testJev(retry = RetryPolicy(retryStatuses = setOf(429))) {
                respondJson("{}", HttpStatusCode.ServiceUnavailable)
            }
            shouldThrow<JevInternalServerException> { jev.client.ask(Triage, state = PAYOUT_TICKET) }
            jev.requests shouldHaveSize 1
        }

        "408 is retried like 429 and 5xx" {
            val jev = testJev(retry = RetryPolicy()) { respondJson("{}", HttpStatusCode.RequestTimeout) }
            shouldThrow<JevApiException> { jev.client.ask(Triage, state = PAYOUT_TICKET) }
            jev.requests shouldHaveSize 3
            jev.delays shouldBe listOf(500L, 1000L)
        }

        "a host that doesn't resolve is a connection error naming the host" {
            var calls = 0
            val jev = testJev(retry = RetryPolicy()) {
                calls++
                throw UnresolvedAddressException()
            }
            val e = shouldThrow<JevConnectionException> { jev.client.ask(Triage, state = PAYOUT_TICKET) }
            e.message shouldContain "Could not resolve the host"
            calls shouldBe 3
        }

        // A supplied engine keeps its own connect and socket timeouts (jev4k sets only the request timeout), so the
        // message names the one that fired rather than quoting the configured timeout.
        "a supplied engine's connect and socket timeouts surface as JevTimeoutException, named" {
            val cases = listOf(
                ConnectTimeoutException("too slow") to "the supplied engine's connect timeout",
                SocketTimeoutException("too slow") to "the supplied engine's socket timeout",
            )
            for ((cause, named) in cases) {
                withClue(cause::class.simpleName.orEmpty()) {
                    val jev = testJev { throw cause }
                    val e = shouldThrow<JevTimeoutException> { jev.client.ask(Triage, state = PAYOUT_TICKET) }
                    e.message shouldContain named
                    e.message shouldNotContain "timed out after"
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
                val call = scope.async { jev.client.ask(Triage, state = PAYOUT_TICKET) }
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
            val e = shouldThrow<JevInternalServerException> { jev.client.ask(Triage, state = PAYOUT_TICKET) }
            e.endpoint shouldBe "POST https://api.typesafe.ai/v1/systemone"
            // Sent as X-Trace, read back lowercased, as every engine now reports it.
            e.headers["x-trace"] shouldBe listOf("abc")
            e.message shouldContain "HTTP 502 from POST https://api.typesafe.ai/v1/systemone"
            // The body is kept whole, and only the copy in the message is trimmed, since that is what is logged.
            e.body!!.length shouldBe long.length + 2
            e.message!!.length shouldBeLessThan e.body.length
        }

        "a rate-limit error has a null retryAfter when the server sent no usable hint" {
            for (headers in listOf(emptyMap(), mapOf("retry-after-ms" to "NaN"), mapOf("Retry-After" to "-1"))) {
                withClue(headers.toString()) {
                    val jev = testJev { respondJson("{}", HttpStatusCode.TooManyRequests, headers) }
                    val e = shouldThrow<JevRateLimitException> { jev.client.ask(Triage, state = PAYOUT_TICKET) }
                    e.retryAfter shouldBe null
                }
            }
        }

        "a Retry-After in seconds reaches the exception as a Duration" {
            val jev = testJev { respondJson("{}", HttpStatusCode.TooManyRequests, mapOf("Retry-After" to "2")) }
            shouldThrow<JevRateLimitException> { jev.client.ask(Triage, state = PAYOUT_TICKET) }
                .retryAfter shouldBe 2.seconds
        }

        // header(), which appends, would leave two Authorization or User-Agent values on the request.
        "a configured header replaces the built-in one of the same name" {
            val jev = testJev(configure = { headers[HttpHeaders.UserAgent] = "my-app/1.0" }) {
                respondJson(TRIAGE_RESPONSE)
            }
            jev.client.ask(Triage, state = PAYOUT_TICKET)
            jev.requests.single().headers.getAll(HttpHeaders.UserAgent) shouldBe listOf("my-app/1.0")
        }

        "a state jev4k can't encode fails as a validation error, not a serialization error" {
            val jev = triageJev()
            shouldThrow<JevValidationException> { jev.client.ask(Triage, state = jsonOf(Double.NaN)) }
            // A hand-built JsonElement skips jsonOf; evaluate's own check of the state catches it instead.
            val nonFinite = buildJsonObject { put("score", JsonPrimitive(Double.POSITIVE_INFINITY)) }
            shouldThrow<JevValidationException> { jev.client.ask(Triage, state = nonFinite) }
                .problems.single() shouldBe "state holds NaN or an infinity, which JSON lacks"
            jev.requests.shouldBeEmpty()
        }

        "a trimmed API key is what reaches the Authorization header" {
            val jev = testJev(configure = { apiKey = "test-key\n" }) { respondJson(TRIAGE_RESPONSE) }
            jev.client.ask(Triage, state = PAYOUT_TICKET)
            jev.requests.single().headers[HttpHeaders.Authorization] shouldBe "Bearer test-key"
        }

        "a blank per-call model falls back to the configured default" {
            val jev = triageJev()
            jev.client.ask(Triage, state = PAYOUT_TICKET, model = "  ")
            jev.requests.single().bodyJson().jsonObject["model"] shouldBe JsonPrimitive("jev-latest")
        }

        // bodyAsText() parses the Content-Type to pick a charset, so a malformed one used to escape as a raw Ktor
        // exception; the body is now read as UTF-8 bytes whatever the header says.
        "a malformed Content-Type doesn't stop the body being read" {
            val badType = headersOf(HttpHeaders.ContentType, "json")
            val ok = testJev { respond(TRIAGE_RESPONSE, HttpStatusCode.OK, badType) }
            ok.client.ask(Triage, state = PAYOUT_TICKET)[Triage.urgent].noul shouldBe 0.92

            val failed = testJev { respond("""{"error":"down"}""", HttpStatusCode.InternalServerError, badType) }
            shouldThrow<JevInternalServerException> { failed.client.ask(Triage, state = PAYOUT_TICKET) }
                .body shouldBe """{"error":"down"}"""
        }

        // Off the JVM, bodyAsText()'s decoder throws on bytes that aren't UTF-8; every platform now replaces them.
        "a body that isn't valid UTF-8 is still a JevException" {
            val invalid = byteArrayOf('{'.code.toByte(), 0xFF.toByte(), '}'.code.toByte())
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            val ok = testJev { respond(invalid, HttpStatusCode.OK, json) }
            shouldThrow<JevResponseValidationException> { ok.client.ask(Triage, state = PAYOUT_TICKET) }
                .message shouldContain "body is not JSON"

            val failed = testJev { respond(invalid, HttpStatusCode.BadGateway, json) }
            shouldThrow<JevInternalServerException> { failed.client.ask(Triage, state = PAYOUT_TICKET) }
        }

        "a leading byte-order mark is ignored" {
            val jev = testJev { respondJson("\uFEFF" + TRIAGE_RESPONSE.trimIndent()) }
            jev.client.ask(Triage, state = PAYOUT_TICKET)[Triage.urgent].noul shouldBe 0.92
        }

        // Ktor fails a call on a closed client with a bare CancellationException, which looked like the caller had
        // been cancelled; the client now says what happened, before sending anything.
        "a closed client fails fast with IllegalStateException" {
            val jev = triageJev()
            jev.client.close()
            shouldThrow<IllegalStateException> { jev.client.models() }.message shouldBe "JevClient is closed"
            jev.requests.shouldBeEmpty()
        }

        // A sibling's failure cancels the call. Ktor unwraps that cancellation to its cause, the sibling's
        // exception, which used to come back as a JevConnectionException; the caller must see its cancellation.
        "a call cancelled because a sibling failed stays a cancellation and isn't retried" {
            val started = CompletableDeferred<Unit>()
            val jev = testJev(retry = RetryPolicy()) {
                started.complete(Unit)
                awaitCancellation()
            }
            var ended: Throwable? = null
            shouldThrow<IOException> {
                coroutineScope {
                    launch {
                        ended = runCatching { jev.client.ask(Triage, state = PAYOUT_TICKET) }.exceptionOrNull()
                        ended?.let { throw it }
                    }
                    launch {
                        started.await()
                        throw IOException("disk full")
                    }
                }
            }
            ended.shouldBeInstanceOf<CancellationException>()
            jev.delays.shouldBeEmpty()
        }

        // kotlinx.serialization recurses once per level, so a few thousand levels overflow the stack on every
        // platform. The limit is checked before sending; a state right at it is sent, which also shows that every
        // platform can encode that deep.
        "a state nested too deeply fails before anything is sent" {
            val jev = triageJev()
            shouldThrow<JevValidationException> { jev.client.ask(Triage, state = nestedArrays(MAX_JSON_DEPTH + 1)) }
                .message shouldContain "state is nested more than $MAX_JSON_DEPTH levels deep"
            jev.requests.shouldBeEmpty()

            jev.client.ask(Triage, state = nestedArrays(MAX_JSON_DEPTH))
            jev.requests shouldHaveSize 1
        }

        "a response nested too deeply is a response error, not a stack overflow" {
            // The body object is the first level, so MAX_JSON_DEPTH arrays inside it is one level too many.
            val tooDeep = testJev { respondJson("""{"answers":{},"extra":${nestedArrayText(MAX_JSON_DEPTH)}}""") }
            shouldThrow<JevResponseValidationException> { tooDeep.client.ask(Triage, state = PAYOUT_TICKET) }
                .message shouldContain "nested more than $MAX_JSON_DEPTH levels deep"

            val atLimit = testJev { respondJson("""{"answers":{},"extra":${nestedArrayText(MAX_JSON_DEPTH - 1)}}""") }
            atLimit.client.ask(Triage, state = PAYOUT_TICKET).answers shouldBe emptyMap()

            val error = testJev { respondJson(nestedArrayText(MAX_JSON_DEPTH + 1), HttpStatusCode.BadRequest) }
            val e = shouldThrow<JevBadRequestException> { error.client.ask(Triage, state = PAYOUT_TICKET) }
            e.bodyJson shouldBe null
            e.body shouldStartWith "[[["
        }

        // Every other test records retry delays instead of sleeping. This one keeps the real wait, to show the client
        // does back off, and that a cancelled call stops waiting at once.
        "the default retry delay really waits, and cancelling a call ends its wait" {
            var calls = 0
            val flaky = closeAfterSpec(
                MockEngine {
                    calls++
                    val status = if (calls == 1) HttpStatusCode.ServiceUnavailable else HttpStatusCode.OK
                    respondJson(if (calls == 1) "{}" else TRIAGE_RESPONSE, status)
                },
            )
            val backsOff = closeAfterSpec(
                JevClient {
                    apiKey = "test-key"
                    env = { null }
                    engine = flaky
                    retry = RetryPolicy(initialBackoff = 200.milliseconds, jitter = 0.0)
                },
            )
            val took = TimeSource.Monotonic.measureTime { backsOff.ask(Triage, state = PAYOUT_TICKET) }
            withClue("took $took") { (took >= 200.milliseconds) shouldBe true }
            calls shouldBe 2

            val firstAttempt = CompletableDeferred<Unit>()
            val slow = closeAfterSpec(
                MockEngine {
                    firstAttempt.complete(Unit)
                    respondJson("{}", HttpStatusCode.ServiceUnavailable, mapOf("retry-after-ms" to "10000"))
                },
            )
            val waits = closeAfterSpec(
                JevClient {
                    apiKey = "test-key"
                    env = { null }
                    engine = slow
                },
            )
            coroutineScope {
                val call = async { waits.ask(Triage, state = PAYOUT_TICKET) }
                firstAttempt.await()
                val stopped = TimeSource.Monotonic.measureTime { call.cancelAndJoin() }
                withClue("stopped after $stopped") { (stopped < 5.seconds) shouldBe true }
                call.isCancelled shouldBe true
            }
        }

        // The API takes a string, an object or an array. A number would otherwise go out as ask(q, state = order.id).
        "a number or boolean state is rejected before anything is sent" {
            val jev = triageJev()
            shouldThrow<JevValidationException> { jev.client.evaluate(JsonPrimitive(42), Triage.questions) }
                .problems.single() shouldBe "state must be a string, a JSON object or a JSON array, not a number"
            shouldThrow<JevValidationException> { jev.client.ask(Triage, state = true) }
                .problems.single() shouldContain "not a boolean"
            jev.requests.shouldBeEmpty()
        }

        "a malformed success body is reported with its real status and headers" {
            val body = """{"answers":{"urgent":{"noul":"high"}}}"""
            val jev = testJev { respondJson(body, HttpStatusCode.Created, mapOf("X-Trace" to "t")) }
            val e = shouldThrow<JevResponseValidationException> { jev.client.ask(Triage, state = PAYOUT_TICKET) }
            e.status shouldBe 201
            e.headers["x-trace"] shouldBe listOf("t")
            e.body shouldBe body
        }

        "a Retry-After date reaches the exception as the time left, by the client's clock" {
            val now = "Wed, 21 Oct 2026 07:28:00 GMT".fromHttpToGmtDate().timestamp
            val hint = mapOf("Retry-After" to "Wed, 21 Oct 2026 07:28:45 GMT")
            val jev = testJev(configure = { this.now = { now } }) {
                respondJson("{}", HttpStatusCode.TooManyRequests, hint)
            }
            shouldThrow<JevRateLimitException> { jev.client.ask(Triage, state = PAYOUT_TICKET) }
                .retryAfter shouldBe 45.seconds
        }

        // Following one would send the client's headers to another host, and on Node the POST body too.
        "a redirect is reported, not followed" {
            val elsewhere = headersOf(HttpHeaders.Location, "https://elsewhere.example/v1/models")
            val jev = testJev { respond("", HttpStatusCode.Found, elsewhere) }
            shouldThrow<JevApiException> { jev.client.models() }.status shouldBe 302
            jev.requests shouldHaveSize 1
        }

        "a configured Accept is the only Accept sent" {
            val custom = testJev(configure = { headers[HttpHeaders.Accept] = "application/vnd.gateway+json" }) {
                respondJson(TRIAGE_RESPONSE)
            }
            custom.client.ask(Triage, state = PAYOUT_TICKET)
            custom.requests.single().headers.getAll(HttpHeaders.Accept) shouldBe listOf("application/vnd.gateway+json")

            val plain = triageJev()
            plain.client.ask(Triage, state = PAYOUT_TICKET)
            plain.requests.single().headers.getAll(HttpHeaders.Accept) shouldBe listOf("application/json")
        }

        "a response declaring a body over the size limit is refused before the body is read" {
            val huge = headersOf(HttpHeaders.ContentLength, "${MAX_RESPONSE_BYTES + 1}")
            val ok = testJev { respond("{}", HttpStatusCode.OK, huge) }
            val e = shouldThrow<JevResponseValidationException> { ok.client.ask(Triage, state = PAYOUT_TICKET) }
            e.message shouldContain "body of ${MAX_RESPONSE_BYTES + 1} bytes not read"
            e.body shouldBe null

            val failed = testJev { respond("{}", HttpStatusCode.BadGateway, huge) }
            val error = shouldThrow<JevInternalServerException> { failed.client.ask(Triage, state = PAYOUT_TICKET) }
            error.status shouldBe 502
            error.body shouldBe null
            error.message shouldContain "not read, over the $MAX_RESPONSE_BYTES-byte limit"
        }

        // A supplied engine keeps its own connect and socket timeouts: CIO, for one, prefers the ones on a request
        // over its own, so jev4k sets only the request timeout there.
        "a supplied engine gets only the request timeout, the client's or the call's" {
            val jev = testJev(configure = { timeout = 7.seconds }) { respondJson(TRIAGE_RESPONSE) }
            jev.client.ask(Triage, state = PAYOUT_TICKET)
            jev.client.withOptions(JevCallOptions { timeout = 3.seconds }).ask(Triage, state = PAYOUT_TICKET)
            val (plain, perCall) = jev.requests.map { it.getCapabilityOrNull(HttpTimeoutCapability) }
            plain?.requestTimeoutMillis shouldBe 7_000L
            plain?.connectTimeoutMillis shouldBe null
            plain?.socketTimeoutMillis shouldBe null
            perCall?.requestTimeoutMillis shouldBe 3_000L
            perCall?.connectTimeoutMillis shouldBe null
        }

        "jev4k's own engine gets the timeout for connecting and reading as well" {
            val own = HttpTimeoutConfig().apply { limitTo(4.seconds, ownEngine = true) }
            own.requestTimeoutMillis shouldBe 4_000L
            own.connectTimeoutMillis shouldBe 4_000L
            own.socketTimeoutMillis shouldBe 4_000L
        }
    }
}
