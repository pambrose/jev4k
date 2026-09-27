package com.pambrose.jev4k.internal

import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory

// What differs between platforms. Each has an actual per source set: jvmMain, nativeMain (split into appleMain,
// linuxMain and mingwMain for the engine), and webMain for js and wasmJs.

/** An environment variable, or null when it is unset or the platform has no environment. */
internal expect fun platformGetenv(name: String): String?

/**
 * The platform's default engine: CIO on the JVM, Darwin on Apple platforms, Curl on Linux, WinHttp on Windows, and
 * the fetch-based Js engine on Node.js. None needs a timeout of its own: HttpTimeout sets one on every request.
 */
internal expect val defaultEngine: HttpClientEngineFactory<HttpClientEngineConfig>

/**
 * True when [cause] is the default engine's own way of saying that no connection was made. The JVM, Darwin and
 * CIO engines throw an `IOException`, which is already recognized; Curl, WinHttp and the Js engine don't.
 */
internal expect fun isPlatformConnectionError(cause: Throwable): Boolean

/** The simple name of the enum class a constant belongs to, for error messages. */
internal expect fun Enum<*>.enumTypeName(): String?
