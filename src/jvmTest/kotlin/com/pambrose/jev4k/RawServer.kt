package com.pambrose.jev4k

import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.security.KeyStore
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import kotlin.concurrent.thread

/**
 * A local server that answers every request with [response], byte for byte, then closes the connection: for
 * responses no well-behaved server sends, such as a body cut short of its `Content-Length`. The server socket can be
 * a TLS one ([selfSignedTlsSocket]), to test the handshake itself. Tests use GET, so a request is only its head.
 */
internal class RawServer(
    private val response: ByteArray,
    private val socket: ServerSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")),
) : AutoCloseable {
    private val answered = AtomicInteger()

    val port: Int get() = socket.localPort

    /** Requests read and answered; a connection whose TLS handshake failed never counts. */
    val requests: Int get() = answered.get()

    init {
        thread(isDaemon = true, name = "raw-server") {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { answer(client) }
            }
        }
    }

    private fun answer(client: Socket) =
        client.use {
            // A client that gives up, after a failed handshake say, makes the read or write throw; that's expected.
            runCatching {
                // The head ends at the first empty line. A connection closed before it throws, and isn't counted.
                it.getInputStream().bufferedReader().lineSequence().first(String::isEmpty)
                answered.incrementAndGet()
                it.getOutputStream().apply {
                    write(response)
                    flush()
                }
            }
        }

    override fun close() = socket.close()

    companion object {
        private const val PASSWORD = "changeit"

        /** A TLS server socket on 127.0.0.1 with a fresh self-signed certificate, which the JDK doesn't trust. */
        fun selfSignedTlsSocket(): ServerSocket {
            val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            keys.init(selfSignedKeyStore(), PASSWORD.toCharArray())
            val tls = SSLContext.getInstance("TLS").apply { init(keys.keyManagers, null, null) }
            return tls.serverSocketFactory.createServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        }

        // The JDK's own keytool, so the test needs no certificate library.
        private fun selfSignedKeyStore(): KeyStore {
            val dir = Files.createTempDirectory("jev4k-tls").toFile()
            val file = File(dir, "self-signed.p12")
            try {
                val keytool = File(System.getProperty("java.home"), "bin/keytool").path
                val options =
                    "-genkeypair -alias server -keyalg RSA -keysize 2048 -validity 1 -dname CN=127.0.0.1 " +
                        "-ext SAN=ip:127.0.0.1 -storetype PKCS12 -storepass $PASSWORD -keypass $PASSWORD"
                val command = listOf(keytool) + options.split(' ') + listOf("-keystore", file.path)
                val process = ProcessBuilder(command).redirectErrorStream(true).start()
                val output = process.inputStream.readAllBytes().decodeToString()
                check(process.waitFor() == 0) { "keytool failed: $output" }
                return KeyStore.getInstance("PKCS12").apply {
                    file.inputStream().use { load(it, PASSWORD.toCharArray()) }
                }
            } finally {
                dir.deleteRecursively()
            }
        }
    }
}
