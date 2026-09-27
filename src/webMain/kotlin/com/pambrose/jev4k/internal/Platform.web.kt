package com.pambrose.jev4k.internal

import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.js.Js
import kotlin.js.ExperimentalWasmJsInterop

// Kotlin/Wasm only allows js() as the whole body of a top-level function, so the lookup is written that way for
// both targets. `process` exists only on Node.js; anywhere else there is no environment to read.
// detekt can't see that the JavaScript uses `name`.
@Suppress("UnusedParameter")
@OptIn(ExperimentalWasmJsInterop::class)
private fun nodeEnv(name: String): String? =
    js("(typeof process !== 'undefined' && process.env[name] !== undefined) ? process.env[name] : null")

internal actual fun platformGetenv(name: String): String? = nodeEnv(name)

internal actual val defaultEngine: HttpClientEngineFactory<HttpClientEngineConfig> = Js

// The Js engine turns a rejected fetch (refused connection, failed DNS lookup, TLS failure) into Error("Fail to
// fetch"), which is neither an Exception nor an IOException.
internal actual fun isPlatformConnectionError(cause: Throwable): Boolean =
    cause::class == Error::class && cause.message == "Fail to fetch"

internal actual fun Enum<*>.enumTypeName(): String? = this::class.simpleName
