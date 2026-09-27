package com.pambrose.jev4k

/**
 * [JevApi] as blocking calls, for scripts, `main`, and tests, reached through [JevClient.blocking].
 *
 * The blocking calls exist on the JVM only. On every other platform this class has no members: Kotlin/JS and
 * Kotlin/Wasm can't block a thread at all, and Kotlin/Native code that needs to can wrap the suspend calls in
 * `runBlocking` itself.
 */
expect class BlockingJev internal constructor(
    api: JevApi,
)
