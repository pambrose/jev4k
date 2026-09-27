package com.pambrose.jev4k.internal

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.curl.Curl

internal actual fun defaultHttpClient(
    timeoutMillis: Long,
    configure: HttpClientConfig<*>.() -> Unit,
): HttpClient = HttpClient(Curl) { configure() }

internal actual val DEFAULT_ENGINE_NAME: String = "Curl"

// Curl reports a refused connection, a failed DNS lookup or a failed TLS handshake as a plain IllegalStateException.
// The class is matched exactly: on Kotlin/Native a CancellationException is an IllegalStateException too.
internal actual fun isPlatformConnectionError(cause: Throwable): Boolean = cause::class == IllegalStateException::class
