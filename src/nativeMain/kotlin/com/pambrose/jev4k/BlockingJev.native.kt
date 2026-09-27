package com.pambrose.jev4k

/** Blocking calls are JVM-only; on Kotlin/Native, call the suspend API, wrapped in `runBlocking` if need be. */
@Suppress("UnusedPrivateProperty")
actual class BlockingJev internal actual constructor(
    api: JevApi,
)
