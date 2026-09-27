package com.pambrose.jev4k.internal

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformGetenv(name: String): String? = getenv(name)?.toKString()

internal actual fun Enum<*>.enumTypeName(): String? = this::class.simpleName

/**
 * How the Curl and WinHttp engines report a failed connection. The class is matched exactly: on Kotlin/Native a
 * CancellationException is an IllegalStateException too, and must never count as one.
 */
internal fun Throwable.isBareIllegalStateException(): Boolean = this::class == IllegalStateException::class
