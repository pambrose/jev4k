package com.pambrose.jev4k.internal

import com.pambrose.jev4k.JevConfig
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json

/** Builds the Ktor [HttpClient] for a [JevConfig]: the platform's default engine unless one is injected. */
internal object HttpClientFactory {
    private const val USER_AGENT = "jev4k/$JEV4K_VERSION"

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
        val policy = config.retry
        install(HttpRequestRetry) {
            retryIf(policy.maxRetries) { _, response -> response.status.value in policy.retryStatuses }
            retryOnExceptionIf(policy.maxRetries) { _, cause -> policy.retriesOn(cause) }
            // The hint parsing (retry-after-ms, cap) lives in delayMillis, so Ktor's own Retry-After handling is off.
            delayMillis(respectRetryAfterHeader = false) { retry ->
                policy.delayMillis(response?.headers, retry, config.random)
            }
            delay { config.retryDelay(it) }
        }

        install(HttpTimeout) {
            val millis = config.timeout.inWholeMilliseconds
            requestTimeoutMillis = millis
            // CIO prefers these over its own endpoint.connectTimeout / endpoint.socketTimeout, so setting them
            // would overrule whatever a caller tuned on an engine they supplied. Only fill them in for our engine;
            // a default engine that has no such timeout (Js has neither, Darwin no connect timeout) ignores them.
            if (config.engine == null) {
                connectTimeoutMillis = millis
                socketTimeoutMillis = millis
            }
        }

        defaultRequest {
            url("${config.baseUrl}/")
            bearerAuth(config.apiKey)
            accept(ContentType.Application.Json)
            header(HttpHeaders.UserAgent, USER_AGENT)
            // set, not append: a caller naming Authorization, Accept or User-Agent replaces ours rather than
            // sending two values.
            config.headers.forEach { (name, value) -> headers[name] = value }
        }
    }
}
