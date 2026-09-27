package com.pambrose.jev4k.internal

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO

internal actual fun platformGetenv(name: String): String? = System.getenv(name)

internal actual fun defaultHttpClient(
    timeoutMillis: Long,
    configure: HttpClientConfig<*>.() -> Unit,
): HttpClient =
    HttpClient(CIO) {
        engine { requestTimeout = timeoutMillis }
        configure()
    }

internal actual val DEFAULT_ENGINE_NAME: String = "CIO"

// CIO reports every failure to connect as an IOException or an UnresolvedAddressException.
internal actual fun isPlatformConnectionError(cause: Throwable): Boolean = false

internal actual fun Enum<*>.enumTypeName(): String? = declaringJavaClass.simpleName
