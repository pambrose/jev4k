package com.pambrose.jev4k.internal

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

// What differs between platforms. Each has an actual per source set: jvmMain, nativeMain (split into appleMain,
// linuxMain and mingwMain for the engine), and webMain for js and wasmJs.

/** An environment variable, or null when it is unset or the platform has no environment. */
internal expect fun platformGetenv(name: String): String?

/**
 * A client on the platform's default engine: CIO on the JVM, Darwin on Apple platforms, Curl on Linux, WinHttp on
 * Windows, and the fetch-based Js engine on Node.js. [timeoutMillis] is for engines with their own request
 * timeout, which would otherwise cut a longer [io.ktor.client.plugins.HttpTimeout] short.
 */
internal expect fun defaultHttpClient(
    timeoutMillis: Long,
    configure: HttpClientConfig<*>.() -> Unit,
): HttpClient

/** The default engine's name, as [com.pambrose.jev4k.JevConfig.toString] reports it. */
internal expect val DEFAULT_ENGINE_NAME: String

/**
 * True when [cause] is the default engine's own way of saying that no connection was made. The JVM, Darwin and
 * CIO engines throw an `IOException`, which is already recognized; Curl, WinHttp and the Js engine don't.
 */
internal expect fun isPlatformConnectionError(cause: Throwable): Boolean

/** The simple name of the enum class a constant belongs to, for error messages. */
internal expect fun Enum<*>.enumTypeName(): String?
