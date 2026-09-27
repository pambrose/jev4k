package com.pambrose.jev4k.internal

import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

internal actual fun platformGetenv(name: String): String? = System.getenv(name)

internal actual val defaultEngine: HttpClientEngineFactory<HttpClientEngineConfig> = CIO

// CIO reports every failure to connect as an IOException or an UnresolvedAddressException.
internal actual fun isPlatformConnectionError(cause: Throwable): Boolean = false

internal actual fun Enum<*>.enumTypeName(): String? = declaringJavaClass.simpleName
