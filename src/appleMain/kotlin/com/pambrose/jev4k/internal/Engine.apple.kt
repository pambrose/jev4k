package com.pambrose.jev4k.internal

import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

internal actual val defaultEngine: HttpClientEngineFactory<HttpClientEngineConfig> = Darwin

// Darwin reports a failure to connect as DarwinHttpRequestException, an IOException.
internal actual fun isPlatformConnectionError(cause: Throwable): Boolean = false
