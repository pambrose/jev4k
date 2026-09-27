package com.pambrose.jev4k.internal

import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.winhttp.WinHttp

internal actual val defaultEngine: HttpClientEngineFactory<HttpClientEngineConfig> = WinHttp

// WinHttp reports a failure to connect as a plain IllegalStateException.
internal actual fun isPlatformConnectionError(cause: Throwable): Boolean = cause.isBareIllegalStateException()
