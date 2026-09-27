package com.pambrose.jev4k

import com.pambrose.jev4k.internal.HttpClientFactory
import com.pambrose.jev4k.internal.JevJson
import com.pambrose.jev4k.internal.SystemOneRequest
import com.pambrose.jev4k.internal.isConnectionError
import com.pambrose.jev4k.internal.isTimeout
import com.pambrose.jev4k.internal.mapModels
import com.pambrose.jev4k.internal.mapSystemOne
import com.pambrose.jev4k.internal.retryHint
import com.pambrose.jev4k.internal.toWire
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
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
        val endpoint = "${method.value} ${config.baseUrl}/$path"
        val response = execute(path, endpoint) {
            this.method = method
            configure()
        }

        val requestId = response.headers[JevDefaults.REQUEST_ID_HEADER]
        val text = response.bodyAsText()
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
            // how the Curl and WinHttp engines report a failed connection.
            throw e
        } catch (e: Throwable) {
            // Throwable, not Exception: the Js engine reports a failed fetch as a kotlin.Error. Anything that isn't
            // a timeout or a connection failure is rethrown unchanged.
            throw when {
                isTimeout(e) -> JevTimeoutException("Request to $endpoint timed out after ${config.timeout}", e)
                e is UnresolvedAddressException -> JevConnectionException("Could not resolve the host for $endpoint", e)
                isConnectionError(e) -> JevConnectionException("Could not reach $endpoint: ${e.message}", e)
                else -> e
            }
        }

    private fun parseObject(
        text: String,
        status: Int,
        requestId: String?,
        endpoint: String,
    ): JsonObject {
        val body = try {
            JevJson.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw JevResponseValidationException("body is not JSON", null, text, requestId, endpoint, status, cause = e)
        }
        return body as? JsonObject
            ?: throw JevResponseValidationException("expected a JSON object", null, text, requestId, endpoint, status)
    }

    private companion object {
        const val SYSTEM_ONE_PATH = "v1/systemone"
        const val MODELS_PATH = "v1/models"
    }
}
