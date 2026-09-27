package com.pambrose.jev4k

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * Each platform's real default engine (CIO, Darwin, Curl, WinHttp or Js) against a loopback port nothing listens
 * on. The engines report a refused connection in different ways (an IOException, a bare IllegalStateException, a
 * kotlin.Error), and every one of them has to become a JevConnectionException and be retried.
 */
class PlatformEngineTest : StringSpec() {
    init {
        // Port 47 is unassigned in practice and, unlike ports such as 1 or 9, isn't on the fetch spec's list of
        // blocked ports, so the Js engine really dials it rather than refusing the URL up front.
        "a refused connection is retried, then surfaces as JevConnectionException" {
            val delays = mutableListOf<Long>()
            JevClient {
                testDefaults(delays)
                baseUrl = "http://127.0.0.1:47"
                retry = RetryPolicy(maxRetries = 1)
            }.use { client ->
                val e = shouldThrowExactly<JevConnectionException> { client.models() }
                e.message shouldContain "Could not reach GET http://127.0.0.1:47/v1/models"
            }
            delays shouldBe listOf(500L)
        }
    }
}
