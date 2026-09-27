package com.pambrose.jev4k

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A local HTTP server that accepts connections, counts the requests it receives, and never answers,
 * so a real engine's request timeout fires.
 */
internal class SilentServer : AutoCloseable {
    // Bound to 127.0.0.1 explicitly: a test that dials that literal would miss a loopback resolved to ::1.
    private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val clients = CopyOnWriteArrayList<Socket>()
    private val requests = AtomicInteger()

    val port: Int get() = socket.localPort

    init {
        thread(isDaemon = true) {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                clients += client
                thread(isDaemon = true) {
                    runCatching {
                        client.getInputStream().bufferedReader().lineSequence().forEach {
                            if (it.startsWith("POST ")) requests.incrementAndGet()
                        }
                    }
                }
            }
        }
    }

    /**
     * Waits for [expected] requests to arrive, then returns how many did. On timeout Kotest reports the last
     * value it saw, so a failure says how many requests actually landed.
     */
    suspend fun awaitRequests(
        expected: Int,
        within: Duration = 10.seconds,
    ): Int =
        eventually(within) {
            requests.get().also { it shouldBe expected }
        }

    override fun close() {
        socket.close()
        clients.forEach { runCatching { it.close() } }
    }
}
