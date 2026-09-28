package com.pambrose.jev4k

import com.pambrose.jev4k.internal.HttpClientFactory
import com.pambrose.jev4k.internal.JevJson
import com.pambrose.jev4k.internal.MAX_JSON_DEPTH
import com.pambrose.jev4k.internal.MAX_RESPONSE_BYTES
import com.pambrose.jev4k.internal.OversizedResponseException
import com.pambrose.jev4k.internal.ResponseInfo
import com.pambrose.jev4k.internal.SystemOneRequest
import com.pambrose.jev4k.internal.USER_AGENT
import com.pambrose.jev4k.internal.follow
import com.pambrose.jev4k.internal.isConnectionError
import com.pambrose.jev4k.internal.isTimeout
import com.pambrose.jev4k.internal.limitTo
import com.pambrose.jev4k.internal.mapModels
import com.pambrose.jev4k.internal.mapSystemOne
import com.pambrose.jev4k.internal.nestsTooDeep
import com.pambrose.jev4k.internal.retryHint
import com.pambrose.jev4k.internal.toWire
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.retry
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.util.network.UnresolvedAddressException
import io.ktor.util.toMap
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmOverloads
import kotlin.time.Duration

/**
 * A Jev client over Ktor, on the platform's default engine (CIO on the JVM) unless one is supplied. Configure it
 * inline, or rely on `TYPESAFE_API_KEY`:
 *
 * ```
 * JevClient().use { jev ->
 *     val result = jev.ask(Triage, state = ticket)
 * }
 * ```
 *
 * Failed requests are retried per [JevConfig.retry]; errors surface as [JevException] subclasses.
 * Close the client when done.
 */
class JevClient(
    val config: JevConfig,
) : JevApi,
    AutoCloseable {
    @JvmOverloads
    constructor(block: JevConfigBuilder.() -> Unit = {}) : this(JevConfigBuilder().apply(block).build())

    private val http = HttpClientFactory.create(config)

    /** The same API, blocking the calling thread (JVM only). Don't call it from inside a coroutine. */
    val blocking: BlockingJev = BlockingJev(this)

    override suspend fun evaluate(
        state: JsonElement,
        questions: QuestionSet,
        model: String?,
    ): JevResult = evaluate(state, questions, model, NoCallOptions)

    override suspend fun evaluate(
        state: JsonElement,
        questions: QuestionSet,
        model: String?,
        options: JevCallOptions,
    ): JevResult {
        stateProblem(state)?.let { throw JevValidationException(listOf(it)) }
        // A blank model is treated as unset, as the builder treats a blank defaultModel.
        val resolvedModel = model?.trim()?.takeIf { it.isNotEmpty() } ?: config.defaultModel
        val request = SystemOneRequest(state, resolvedModel, questions.toWire())
        return send(HttpMethod.Post, SYSTEM_ONE_PATH, options, {
            contentType(ContentType.Application.Json)
            // Encoded here, inside the call, so a state that can't be encoded still becomes a JevValidationException.
            if (options.extraBody.isEmpty()) {
                setBody(request)
            } else {
                val fields = JevJson.encodeToJsonElement(SystemOneRequest.serializer(), request).jsonObject
                setBody(JsonObject(fields + options.extraBody))
            }
        }) { body, response -> mapSystemOne(body, resolvedModel, questions, response) }
    }

    /** What is wrong with [state], if anything. The API takes a string, an object or an array, as the SDKs type it. */
    private fun stateProblem(state: JsonElement): String? {
        val bare = (state as? JsonPrimitive)?.takeUnless { it.isString || it is JsonNull }
        val kind = if (bare?.booleanOrNull != null) "a boolean" else "a number"
        return when {
            state is JsonNull -> "state must not be null"
            bare != null -> "state must be a string, a JSON object or a JSON array, not $kind"
            state.nestsTooDeep() -> "state is nested more than $MAX_JSON_DEPTH levels deep"
            else -> null
        }
    }

    override suspend fun models(): ModelList = models(NoCallOptions)

    override suspend fun models(options: JevCallOptions): ModelList =
        send(HttpMethod.Get, MODELS_PATH, options, {}, ::mapModels)

    override fun close() = http.close()

    private suspend fun <T> send(
        method: HttpMethod,
        path: String,
        options: JevCallOptions,
        configure: HttpRequestBuilder.() -> Unit,
        parse: (body: JsonObject, response: ResponseInfo) -> T,
    ): T {
        check(http.isActive) { "JevClient is closed" }
        val endpoint = "${method.value} ${config.baseUrl}/$path"
        val limit = options.timeout ?: config.timeout
        val response = execute(path, endpoint, limit) {
            this.method = method
            setHeaders(options)
            options.timeout?.let { timeout { limitTo(it, ownEngine = config.engine == null) } }
            options.retry?.let { policy -> retry { follow(policy, config) } }
            configure()
        }

        val requestId = response.headers[JevDefaults.REQUEST_ID_HEADER]
        // Raw bytes, not bodyAsText(): that parses the Content-Type to pick a charset, so a malformed header would
        // throw, and off the JVM its decoder throws on bytes that aren't valid UTF-8. JSON is UTF-8 (RFC 8259), and
        // decodeToString() replaces bad bytes instead of throwing. A leading byte-order mark is dropped, as the Js
        // engine's decoder already does, so every platform reads the same text.
        val text = response.readRawBytes().decodeToString().removePrefix("\uFEFF")
        val status = response.status.value
        if (!response.status.isSuccess()) {
            val hint = retryHint(response.headers, config.now())
            throw apiException(status, text, response.headers.toMap(), requestId, endpoint, hint)
        }
        val info = ResponseInfo(text, status, response.headers, requestId, endpoint)
        return parse(parseObject(info), info)
    }

    /**
     * Every header, set rather than appended, in order of precedence: the built-in ones, then the client's, then the
     * call's, so each replaces an earlier one of the same name instead of sending two values.
     */
    private fun HttpRequestBuilder.setHeaders(options: JevCallOptions) {
        headers[HttpHeaders.Authorization] = "Bearer ${config.apiKey}"
        accept(ContentType.Application.Json)
        headers[HttpHeaders.UserAgent] = USER_AGENT
        config.headers.forEach { (name, value) -> headers[name] = value }
        options.headers.forEach { (name, value) -> headers[name] = value }
    }

    /** Sends one request (with retries); failures to get any response become [JevConnectionException]s. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun execute(
        path: String,
        endpoint: String,
        timeout: Duration,
        block: HttpRequestBuilder.() -> Unit,
    ): HttpResponse =
        try {
            http.request(path, block)
        } catch (e: SerializationException) {
            // The body is written during the call, so a state jev4k couldn't encode (a hand-built JsonElement
            // holding a non-finite number, say) fails here rather than at jsonOf/jsonEntry time.
            throw JevValidationException(listOf("Could not encode the request body: ${e.message}"), e)
        } catch (e: CancellationException) {
            // Checked first: on Kotlin/Native a CancellationException is also an IllegalStateException, which is
            // how the Curl and WinHttp engines report a failed connection. A cancelled caller gets its own
            // cancellation; one that isn't cancelled can only have lost the client, closed as the call began.
            currentCoroutineContext().ensureActive()
            if (!http.isActive) throw IllegalStateException("JevClient was closed while a request was starting", e)
            throw e
        } catch (e: Throwable) {
            // A caller cancelled mid-request (because a sibling coroutine failed, say) gets its own
            // CancellationException: Ktor unwraps the cancellation to its cause, which here is the sibling's
            // failure, not this request's.
            currentCoroutineContext().ensureActive()
            // Throwable, not Exception: the Js engine reports a failed fetch as a kotlin.Error. Anything that isn't
            // a timeout or a connection failure is rethrown unchanged.
            throw when {
                e is OversizedResponseException -> oversized(e, endpoint)
                isTimeout(e) -> JevTimeoutException(timeoutMessage(endpoint, timeout, e), e)
                e is UnresolvedAddressException -> JevConnectionException("Could not resolve the host for $endpoint", e)
                isConnectionError(e) -> JevConnectionException("Could not reach $endpoint: ${e.message}", e)
                else -> e
            }
        }

    /**
     * A response refused for its declared size, reported as a response error for a success status, or as the status's
     * usual exception, with no body, for any other.
     */
    private fun oversized(
        e: OversizedResponseException,
        endpoint: String,
    ): JevApiException {
        val requestId = e.headers[JevDefaults.REQUEST_ID_HEADER]
        val headers = e.headers.toMap()
        val note = "body of ${e.contentLength} bytes not read, over the $MAX_RESPONSE_BYTES-byte limit"
        return if (e.status in 200..299) {
            JevResponseValidationException(note, null, null, requestId, endpoint, e.status, headers, e)
        } else {
            apiException(e.status, null, headers, requestId, endpoint, retryHint(e.headers, config.now()), note)
        }
    }

    /**
     * Quotes the [timeout] in force (the call's, or [JevConfig.timeout]) only when it is the limit that fired. jev4k's
     * own engine gets it as the request, connect and socket timeout alike; a supplied engine gets only the request
     * timeout and keeps its own connect and socket timeouts, so those are named instead of quoted.
     */
    private fun timeoutMessage(
        endpoint: String,
        timeout: Duration,
        cause: Throwable,
    ): String =
        if (config.engine == null || cause is HttpRequestTimeoutException) {
            "Request to $endpoint timed out after $timeout"
        } else {
            val which = if (cause is ConnectTimeoutException) "connect" else "socket"
            "Request to $endpoint timed out (the supplied engine's $which timeout)"
        }

    private fun parseObject(response: ResponseInfo): JsonObject {
        // Checked before parsing: kotlinx.serialization parses by recursion, so a deep enough body overflows it.
        val tooDeep = response.text.nestsTooDeep()
        val body = try {
            if (tooDeep) null else JevJson.parseToJsonElement(response.text)
        } catch (e: SerializationException) {
            throw response.invalid("body is not JSON", cause = e)
        }
        val problem = if (tooDeep) "body is nested more than $MAX_JSON_DEPTH levels deep" else "expected a JSON object"
        return body as? JsonObject ?: throw response.invalid(problem)
    }

    private companion object {
        const val SYSTEM_ONE_PATH = "v1/systemone"
        const val MODELS_PATH = "v1/models"
    }
}
