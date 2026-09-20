package com.pambrose.jev4k

import com.pambrose.jev4k.internal.HttpClientFactory
import com.pambrose.jev4k.internal.JevJson
import com.pambrose.jev4k.internal.SystemOneRequest
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
import io.ktor.util.toMap
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.nio.channels.UnresolvedAddressException

/**
 * A Jev client over Ktor (CIO). Configure it inline, or rely on `TYPESAFE_API_KEY`:
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

    /** The same API, blocking the calling thread. Don't call it from inside a coroutine. */
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
    private suspend fun execute(
        path: String,
        endpoint: String,
        block: HttpRequestBuilder.() -> Unit,
    ): HttpResponse =
        try {
            http.request(path, block)
        } catch (e: IOException) {
            throw if (isTimeout(e)) {
                JevTimeoutException("Request to $endpoint timed out after ${config.timeout}", e)
            } else {
                JevConnectionException("Could not reach $endpoint: ${e.message}", e)
            }
        } catch (e: UnresolvedAddressException) {
            throw JevConnectionException("Could not resolve the host for $endpoint", e)
        } catch (e: SerializationException) {
            // The body is written during the call, so a state jev4k couldn't encode (a hand-built JsonElement
            // holding a non-finite number, say) fails here rather than at jsonOf/jsonEntry time.
            throw JevValidationException(listOf("Could not encode the request body: ${e.message}"), e)
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

/** [JevApi] as blocking calls, for scripts, `main`, and tests. */
class BlockingJev internal constructor(
    @PublishedApi internal val api: JevApi,
) {
    @JvmOverloads
    fun evaluate(
        state: JsonElement,
        questions: QuestionSet,
        model: String? = null,
    ): JevResult = runBlocking { api.evaluate(state, questions, model) }

    fun models(): List<ModelInfo> = runBlocking { api.models() }

    @JvmOverloads
    fun query(
        state: String,
        model: String? = null,
        block: QueryBuilder.() -> Unit,
    ): JevResult = runBlocking { api.query(state, model, block) }

    @JvmOverloads
    fun query(
        state: JsonElement,
        model: String? = null,
        block: QueryBuilder.() -> Unit,
    ): JevResult = runBlocking { api.query(state, model, block) }

    @JvmSynthetic
    inline fun <reified T : Any> query(
        state: T,
        model: String? = null,
        noinline block: QueryBuilder.() -> Unit,
    ): JevResult = runBlocking { api.query(state, model, block) }

    @JvmOverloads
    fun ask(
        query: JevQuery,
        state: String,
        model: String? = null,
    ): JevResult = runBlocking { api.ask(query, state, model) }

    @JvmOverloads
    fun ask(
        query: JevQuery,
        state: JsonElement,
        model: String? = null,
    ): JevResult = runBlocking { api.ask(query, state, model) }

    @JvmSynthetic
    inline fun <reified T : Any> ask(
        query: JevQuery,
        state: T,
        model: String? = null,
    ): JevResult = runBlocking { api.ask(query, state, model) }
}
