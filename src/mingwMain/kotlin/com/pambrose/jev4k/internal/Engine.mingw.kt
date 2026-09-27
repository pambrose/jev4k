package com.pambrose.jev4k.internal

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.winhttp.WinHttp

internal actual fun defaultHttpClient(
    timeoutMillis: Long,
    configure: HttpClientConfig<*>.() -> Unit,
): HttpClient = HttpClient(WinHttp) { configure() }

internal actual val DEFAULT_ENGINE_NAME: String = "WinHttp"

// WinHttp reports a failure to connect as a plain IllegalStateException. The class is matched exactly: on
// Kotlin/Native a CancellationException is an IllegalStateException too.
internal actual fun isPlatformConnectionError(cause: Throwable): Boolean = cause::class == IllegalStateException::class
