package com.pambrose.jev4k

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import java.io.IOException

/**
 * What a consumer who supplies an engine and excludes CIO does: build a client and make calls, one that succeeds and
 * one the engine fails. [CioExclusionTest] runs it in a class loader that can't see CIO, so a CIO reference anywhere on
 * that path fails there with a `NoClassDefFoundError`. It returns plain strings, since the test reads them from
 * outside that loader.
 */
object CioFreeCalls {
    @JvmStatic
    fun run(): List<String> {
        var refuse = false
        val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        MockEngine {
            if (refuse) throw IOException("connection refused")
            respond("""{"models":[{"name":"jev-latest"}]}""", headers = json)
        }.use { engine ->
            JevClient {
                testDefaults()
                this.engine = engine
            }.use { jev ->
                val models = jev.blocking.models()
                refuse = true
                val failure = runCatching { jev.blocking.models() }.exceptionOrNull()
                return listOf(models.single().name, failure?.javaClass?.simpleName.toString())
            }
        }
    }
}
