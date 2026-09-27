package com.pambrose.jev4k.internal

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin

// Darwin (NSURLSession) has no request timeout of its own to override; HttpTimeout sets it per request.
internal actual fun defaultHttpClient(
    timeoutMillis: Long,
    configure: HttpClientConfig<*>.() -> Unit,
): HttpClient = HttpClient(Darwin) { configure() }

internal actual val DEFAULT_ENGINE_NAME: String = "Darwin"

// Darwin reports a failure to connect as DarwinHttpRequestException, an IOException.
internal actual fun isPlatformConnectionError(cause: Throwable): Boolean = false
