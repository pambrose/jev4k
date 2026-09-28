package com.pambrose.jev4k.internal

import com.pambrose.jev4k.JevConfig
import com.pambrose.jev4k.RetryPolicy
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestRetryConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.statement.HttpReceivePipeline
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.util.pipeline.PipelinePhase
import kotlinx.coroutines.cancel
import kotlin.time.Duration

/** The `User-Agent` jev4k sends unless a caller names its own. */
internal const val USER_AGENT = "jev4k/$JEV4K_VERSION"

/** The largest response body jev4k reads, by its declared `Content-Length`: far beyond any real Jev response. */
internal const val MAX_RESPONSE_BYTES = 16L * 1024 * 1024

/** A response refused before its body was read, because its declared length is over [MAX_RESPONSE_BYTES]. */
internal class OversizedResponseException(
    val status: HttpStatusCode,
    val headers: Headers,
    val contentLength: Long,
) : RuntimeException("Response body of $contentLength bytes is over the $MAX_RESPONSE_BYTES-byte limit")

/** Builds the Ktor [HttpClient] for a [JevConfig]: the platform's default engine unless one is injected. */
internal object HttpClientFactory {
    private val LimitBodySize = PipelinePhase("LimitBodySize")

    fun create(config: JevConfig): HttpClient {
        val engine = config.engine
        val client = if (engine != null) {
            HttpClient(engine) { configure(config) }
        } else {
            HttpClient(defaultEngine) { configure(config) }
        }
        return client.apply { limitBodySize() }
    }

    /**
     * Refuses a response whose declared `Content-Length` is over [MAX_RESPONSE_BYTES] before Ktor reads its body
     * into memory. That happens in the receive pipeline's `Before` phase (the SaveBody plugin), so this check runs in
     * a phase of its own ahead of it. A body sent without a length (chunked) isn't checked; the timeout bounds it.
     */
    private fun HttpClient.limitBodySize() {
        receivePipeline.insertPhaseBefore(HttpReceivePipeline.Before, LimitBodySize)
        receivePipeline.intercept(LimitBodySize) { response ->
            val length = response.contentLength()
            if (length != null && length > MAX_RESPONSE_BYTES) {
                response.cancel()
                throw OversizedResponseException(response.status, response.headers, length)
            }
        }
    }

    private fun HttpClientConfig<*>.configure(config: JevConfig) {
        // Errors are mapped from the raw response after retries, not thrown by Ktor.
        expectSuccess = false

        // A redirect is reported rather than followed. Ktor strips only Authorization on a cross-host redirect, so a
        // gateway header would reach the other host, and on Node fetch would re-send the POST body there too. Set
        // here, it applies to a supplied engine as well, and fetch then uses redirect: "manual".
        followRedirects = false

        // HttpRequestRetry must be installed before HttpTimeout. Installed after it, the timeout wraps the
        // whole retry loop: one expiry cancels every later attempt before it reaches the server.
        install(HttpRequestRetry) { follow(config.retry, config) }

        install(HttpTimeout) { limitTo(config.timeout, ownEngine = config.engine == null) }

        // Headers are set on each request instead (JevClient.send). DefaultRequest merges its headers with a
        // request's own rather than letting either replace the other, so a per-call header would be sent twice.
        defaultRequest { url("${config.baseUrl}/") }
    }
}

/** jev4k's retry rules for [policy], whether it is the client's or one call's. */
internal fun HttpRequestRetryConfig.follow(
    policy: RetryPolicy,
    config: JevConfig,
) {
    retryIf(policy.maxRetries) { _, response -> response.status.value in policy.retryStatuses }
    retryOnExceptionIf(policy.maxRetries) { _, cause -> policy.retriesOn(cause) }
    // The hint parsing (retry-after-ms, cap) lives in delayMillis, so Ktor's own Retry-After handling is off.
    delayMillis(respectRetryAfterHeader = false) { retry ->
        policy.delayMillis(response?.headers, retry, config.random, config.now())
    }
    delay { config.retryDelay(it) }
}

/**
 * [timeout] for each attempt. CIO prefers the connect and socket timeouts over its own endpoint.connectTimeout /
 * endpoint.socketTimeout, so setting them would overrule whatever a caller tuned on an engine they supplied. They are
 * only filled in for jev4k's own engine ([ownEngine]); a default engine that has no such timeout (Js has neither,
 * Darwin no connect timeout) ignores them.
 */
internal fun HttpTimeoutConfig.limitTo(
    timeout: Duration,
    ownEngine: Boolean,
) {
    val millis = timeout.inWholeMilliseconds
    requestTimeoutMillis = millis
    if (ownEngine) {
        connectTimeoutMillis = millis
        socketTimeoutMillis = millis
    }
}
