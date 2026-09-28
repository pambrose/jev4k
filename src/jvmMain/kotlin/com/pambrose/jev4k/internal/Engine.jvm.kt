package com.pambrose.jev4k.internal

import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

// A getter in a file of its own, not a stored value next to platformGetenv: a stored value would load CIO in the
// class initializer that every environment lookup runs, so a consumer who supplies an engine and excludes
// ktor-client-cio could not build a client at all. CioExclusionTest pins this.
internal actual val defaultEngine: HttpClientEngineFactory<HttpClientEngineConfig> get() = CIO
