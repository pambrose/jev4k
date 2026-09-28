package com.pambrose.jev4k

import com.pambrose.jev4k.internal.platformGetenv
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank

/**
 * Every other test stubs the environment, so this is the one that checks platformGetenv really reads it: a broken
 * lookup would make JevClient() ignore TYPESAFE_API_KEY on that platform while every other test stays green. PATH is
 * set for every process, including a simulator test spawned by simctl and a Docker container, and Windows looks it
 * up whatever its case.
 */
class PlatformEnvTest : StringSpec() {
    init {
        "platformGetenv reads a variable that is set, and null for one that isn't" {
            platformGetenv("PATH").shouldNotBeNull().shouldNotBeBlank()
            platformGetenv("JEV4K_VARIABLE_THAT_IS_NEVER_SET") shouldBe null
        }
    }
}
