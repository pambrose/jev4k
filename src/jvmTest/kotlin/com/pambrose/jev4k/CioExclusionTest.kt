package com.pambrose.jev4k

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import java.io.File
import java.net.URLClassLoader

/** What excluding CIO takes off a consumer's classpath: the engine, and ktor-network-tls, which only CIO brings. */
private val HIDDEN = listOf("io.ktor.client.engine.cio.", "io.ktor.network.tls.")

/**
 * The README tells anyone who supplies their own engine that ktor-client-cio can be excluded. That holds only while
 * nothing but the default engine itself needs CIO, which this spec pins by running jev4k in a class loader that
 * can't see CIO, as a consumer's classpath looks after the exclusion.
 */
class CioExclusionTest : StringSpec() {
    /** The test classpath, loaded in isolation, with every class of the CIO engine and its TLS hidden. */
    private fun classLoaderWithoutCio(): URLClassLoader {
        val urls =
            System
                .getProperty("java.class.path")
                .split(File.pathSeparator)
                .map { File(it).toURI().toURL() }
                .toTypedArray()
        return object : URLClassLoader(urls, ClassLoader.getPlatformClassLoader()) {
            override fun loadClass(
                name: String,
                resolve: Boolean,
            ): Class<*> {
                if (HIDDEN.any { name.startsWith(it) }) throw ClassNotFoundException(name)
                return super.loadClass(name, resolve)
            }
        }
    }

    init {
        "the class loader really hides CIO" {
            classLoaderWithoutCio().use { loader ->
                shouldThrow<ClassNotFoundException> { loader.loadClass("io.ktor.client.engine.cio.CIO") }
                shouldThrow<ClassNotFoundException> { loader.loadClass("io.ktor.network.tls.TLSConfig") }
            }
        }

        // build() reads every setting left unset through platformGetenv, so this fails if the class that holds it
        // needs CIO to initialize: the NoClassDefFoundError a consumer who excluded CIO would get.
        "a config reads the environment without CIO on the classpath" {
            classLoaderWithoutCio().use { loader ->
                val builder = loader.loadClass("com.pambrose.jev4k.JevConfigBuilder").getConstructor().newInstance()
                builder.javaClass.getMethod("setApiKey", String::class.java).invoke(builder, "test-key")
                val config = builder.javaClass.getMethod("build").invoke(builder)
                config.javaClass.getMethod("getBaseUrl").invoke(config) as String shouldStartWith "http"
            }
        }

        // A client on a supplied engine, making a call and mapping a failed one, is what the README's exclusion
        // promises; a CIO reference added on that path would pass the config test above.
        "a client on a supplied engine makes calls without CIO on the classpath" {
            classLoaderWithoutCio().use { loader ->
                val run = loader.loadClass("com.pambrose.jev4k.CioFreeCalls").getMethod("run")
                run.invoke(null) shouldBe listOf("jev-latest", "JevConnectionException")
            }
        }
    }
}
