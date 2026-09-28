package com.pambrose.jev4k

import com.pambrose.jev4k.internal.HttpClientFactory
import com.pambrose.jev4k.internal.JevJson
import com.pambrose.jev4k.internal.MAX_JSON_DEPTH
import com.pambrose.jev4k.internal.SystemOneRequest
import com.pambrose.jev4k.internal.isConnectionError
import com.pambrose.jev4k.internal.isTimeout
import com.pambrose.jev4k.internal.mapModels
import com.pambrose.jev4k.internal.mapSystemOne
import com.pambrose.jev4k.internal.nestsTooDeep
import com.pambrose.jev4k.internal.retryHint
import com.pambrose.jev4k.internal.toWire
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.util.network.UnresolvedAddressException
import io.ktor.util.toMap
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmOverloads

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
    ): JevResult {
        if (state is JsonNull) throw JevValidationException(listOf("state must not be null"))
        if (state.nestsTooDeep()) {
            throw JevValidationException(listOf("state is nested more than $MAX_JSON_DEPTH levels deep"))
        }
        val resolvedModel = model ?: config.defaultModel
        val request = SystemOneRequest(state, resolvedModel, questions.toWire())
        return send(HttpMethod.Post, SYSTEM_ONE_PATH, {
            contentType(ContentType.Application.Json)
            setBody(request)
        }) { body, requestId, endpoint -> mapSystemOne(body, resolvedModel, questions, requestId, endpoint) }
    }

    override suspend fun models(): List<ModelInfo> = send(HttpMethod.Get, MODELS_PATH, {}, ::mapModels)

    override fun close() = http.close()

    private suspend fun <T> send(
        method: HttpMethod,
        path: String,
        configure: HttpRequestBuilder.() -> Unit,
        parse: (body: JsonObject, requestId: String?, endpoint: String) -> T,
    ): T {
        check(http.isActive) { "JevClient is closed" }
        val endpoint = "${method.value} ${config.baseUrl}/$path"
        val response = execute(path, endpoint) {
            this.method = method
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
            throw apiException(status, text, response.headers.toMap(), requestId, endpoint, retryHint(response.headers))
        }
        return parse(parseObject(text, status, requestId, endpoint), requestId, endpoint)
    }

    /** Sends one request (with retries); failures to get any response become [JevConnectionException]s. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun execute(
        path: String,
        endpoint: String,
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
                isTimeout(e) -> JevTimeoutException(timeoutMessage(endpoint, e), e)
                e is UnresolvedAddressException -> JevConnectionException("Could not resolve the host for $endpoint", e)
                isConnectionError(e) -> JevConnectionException("Could not reach $endpoint: ${e.message}", e)
                else -> e
            }
        }

    /**
     * Quotes [JevConfig.timeout] only when it is the limit that fired. jev4k's own engine gets it as the request,
     * connect and socket timeout alike; a supplied engine gets only the request timeout and keeps its own connect
     * and socket timeouts, so those are named instead of quoted.
     */
    private fun timeoutMessage(
        endpoint: String,
        cause: Throwable,
    ): String =
        if (config.engine == null || cause is HttpRequestTimeoutException) {
            "Request to $endpoint timed out after ${config.timeout}"
        } else {
            val which = if (cause is ConnectTimeoutException) "connect" else "socket"
            "Request to $endpoint timed out (the supplied engine's $which timeout)"
        }

    private fun parseObject(
        text: String,
        status: Int,
        requestId: String?,
        endpoint: String,
    ): JsonObject {
        fun invalid(
            problem: String,
            cause: Throwable? = null,
        ) = JevResponseValidationException(problem, null, text, requestId, endpoint, status, cause = cause)

        // Checked before parsing: kotlinx.serialization parses by recursion, so a deep enough body overflows it.
        val tooDeep = text.nestsTooDeep()
        val body = try {
            if (tooDeep) null else JevJson.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw invalid("body is not JSON", e)
        }
        val problem = if (tooDeep) "body is nested more than $MAX_JSON_DEPTH levels deep" else "expected a JSON object"
        return body as? JsonObject ?: throw invalid(problem)
    }

    private companion object {
        const val SYSTEM_ONE_PATH = "v1/systemone"
        const val MODELS_PATH = "v1/models"
    }
}
