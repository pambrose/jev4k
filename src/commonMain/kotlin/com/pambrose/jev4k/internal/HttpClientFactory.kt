package com.pambrose.jev4k.internal

import com.pambrose.jev4k.JevConfig
import com.pambrose.jev4k.RetryPolicy
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestRetryConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import kotlin.time.Duration

/** The `User-Agent` jev4k sends unless a caller names its own. */
internal const val USER_AGENT = "jev4k/$JEV4K_VERSION"

/** Builds the Ktor [HttpClient] for a [JevConfig]: the platform's default engine unless one is injected. */
internal object HttpClientFactory {
    fun create(config: JevConfig): HttpClient {
        val engine = config.engine
        return if (engine != null) {
            HttpClient(engine) { configure(config) }
        } else {
            HttpClient(defaultEngine) { configure(config) }
        }
    }

    private fun HttpClientConfig<*>.configure(config: JevConfig) {
        // Errors are mapped from the raw response after retries, not thrown by Ktor.
        expectSuccess = false

        install(ContentNegotiation) { json(JevJson) }

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
        policy.delayMillis(response?.headers, retry, config.random)
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
