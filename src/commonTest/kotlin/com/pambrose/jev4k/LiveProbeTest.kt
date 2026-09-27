package com.pambrose.jev4k

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import kotlin.time.Duration.Companion.milliseconds

/**
 * Two calls to the real API that spend no tokens, so every platform's default engine is shown to reach it over
 * TLS, to map an HTTP error, and to honor the timeout. Like `LiveSmokeTest` on the JVM, it
 * runs only when `JEV4K_LIVE=1` (`make live-tests` sets it), so ordinary runs never touch the network.
 */
class LiveProbeTest : StringSpec() {
    private val enabled = liveOptIn()

    private fun probeClient(configure: JevConfigBuilder.() -> Unit = {}) =
        JevClient {
            apiKey = "not-a-real-key"
            retry = RetryPolicy.NONE
            env = { null }
            configure()
        }

    init {
        "an invalid key is rejected over TLS with JevAuthenticationException".config(enabled = enabled) {
            probeClient().use { client ->
                shouldThrow<JevAuthenticationException> { client.models() }
            }
        }

        "a request that can't finish in time surfaces as JevTimeoutException".config(enabled = enabled) {
            probeClient { timeout = 1.milliseconds }.use { client ->
                shouldThrow<JevTimeoutException> { client.models() }
            }
        }
    }
}
