package com.pambrose.jev4k.internal

import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.curl.Curl

internal actual val defaultEngine: HttpClientEngineFactory<HttpClientEngineConfig> = Curl

// Curl reports a refused connection, a failed DNS lookup or a failed TLS handshake as a plain IllegalStateException.
internal actual fun isPlatformConnectionError(cause: Throwable): Boolean = cause.isBareIllegalStateException()
