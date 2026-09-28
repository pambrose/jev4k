package com.pambrose.jev4k

import com.pambrose.jev4k.internal.platformGetenv
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Every other test stubs the environment, so this is the one that checks platformGetenv really reads it: a broken
 * lookup would make JevClient() ignore TYPESAFE_API_KEY on that platform while every other test stays green. The
 * build sets JEV4K_ENV_PROBE on every test task, and docker-linux-tests on its containers.
 */
class PlatformEnvTest : StringSpec() {
    init {
        "platformGetenv reads a variable the test task sets, and null for one that isn't set" {
            platformGetenv("JEV4K_ENV_PROBE") shouldBe "present"
            platformGetenv("JEV4K_ENV_PROBE_THAT_IS_NEVER_SET") shouldBe null
        }
    }
}
