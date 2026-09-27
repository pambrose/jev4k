package com.pambrose.jev4k.internal

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformGetenv(name: String): String? = getenv(name)?.toKString()

internal actual fun Enum<*>.enumTypeName(): String? = this::class.simpleName
