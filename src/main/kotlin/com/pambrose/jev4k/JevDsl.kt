package com.pambrose.jev4k

/** Marks jev4k's DSL receivers so nested builder lambdas can't call an outer builder by accident. */
@DslMarker
annotation class JevDsl
