package com.pambrose.jev4k

/**
 * [JevApi] as blocking calls, for scripts, `main`, and tests, reached through [JevClient.blocking], or [blocking] for
 * any other [JevApi].
 *
 * The blocking calls exist on the JVM only. On every other platform this class has no members: Kotlin/JS and
 * Kotlin/Wasm can't block a thread at all, and Kotlin/Native code that needs to can wrap the suspend calls in
 * `runBlocking` itself.
 */
expect class BlockingJev internal constructor(
    api: JevApi,
)

/**
 * This API as blocking calls, for code that depends on a [JevApi] rather than a [JevClient], so a fake or a
 * [withOptions] view can be handed in. From Java: `BlockingJevKt.blocking(api)`. The calls exist on the JVM only.
 */
fun JevApi.blocking(): BlockingJev = if (this is JevClient) blocking else BlockingJev(this)
