package com.pambrose.jev4k

/** Blocking calls are JVM-only; on Kotlin/JS and Kotlin/Wasm, call the suspend API. */
@Suppress("UnusedPrivateProperty")
actual class BlockingJev internal actual constructor(
    api: JevApi,
)
