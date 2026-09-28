package com.pambrose.jev4k.internal

internal actual fun platformGetenv(name: String): String? = System.getenv(name)

// CIO reports every failure to connect as an IOException or an UnresolvedAddressException.
internal actual fun isPlatformConnectionError(cause: Throwable): Boolean = false

internal actual fun Enum<*>.enumTypeName(): String? = declaringJavaClass.simpleName
