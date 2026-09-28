package com.pambrose.jev4k

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.lang.reflect.Modifier
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * [BlockingJev] only wraps [JevApi] in `runBlocking`, so what matters is that every overload forwards the state
 * and the model unchanged. A mock says that directly, without a client or a coroutine of our own.
 */
class BlockingJevTest : StringSpec() {
    private val jsonState = buildJsonObject { put("ticket", "payouts failing") }

    /**
     * A fresh mock per test. A StringSpec runs its tests against one instance of the spec, so a shared mock
     * would carry its recorded calls from one test into the next and make `coVerify(exactly = 1)` meaningless.
     */
    private fun answering(): Pair<JevApi, BlockingJev> {
        val api = mockk<JevApi> {
            coEvery { evaluate(any(), any(), any()) } returns jevResult(TRIAGE_RESPONSE, Triage.questions)
        }
        return api to BlockingJev(api)
    }

    init {
        "evaluate forwards state, questions and model" {
            val (api, blocking) = answering()
            blocking.evaluate(jsonState, Triage.questions, "jev-1.13.0")[Triage.urgent].noul shouldBe 0.92
            coVerify(exactly = 1) { api.evaluate(jsonState, Triage.questions, "jev-1.13.0") }
        }

        "evaluate defaults the model to null, so the client's default applies" {
            val (api, blocking) = answering()
            blocking.evaluate(jsonState, Triage.questions)
            coVerify(exactly = 1) { api.evaluate(jsonState, Triage.questions, null) }
        }

        "ask forwards every kind of state" {
            val (api, blocking) = answering()
            val ticket = Ticket("Payouts failing", "Three days now.")
            blocking.ask(Triage, "text state", "m1")
            blocking.ask(Triage, jsonState, "m2")
            blocking.ask(Triage, ticket, "m3")

            coVerify(exactly = 1) { api.evaluate(JsonPrimitive("text state"), Triage.questions, "m1") }
            coVerify(exactly = 1) { api.evaluate(jsonState, Triage.questions, "m2") }
            coVerify(exactly = 1) { api.evaluate(jsonEntry(ticket), Triage.questions, "m3") }
        }

        "ask defaults the model to null" {
            val (api, blocking) = answering()
            blocking.ask(Triage, "text state")
            coVerify(exactly = 1) { api.evaluate(JsonPrimitive("text state"), Triage.questions, null) }
        }

        "query forwards every kind of state, with the questions built inline" {
            val (api, blocking) = answering()
            val inline: QueryBuilder.() -> Unit = { include(Triage) }
            blocking.query("text state", "m1", inline)
            blocking.query(jsonState, "m2", inline)
            blocking.query(Ticket("s", "m"), "m3", inline)

            val triage = Triage.questions.ids
            coVerify(exactly = 1) { api.evaluate(JsonPrimitive("text state"), match { it.ids == triage }, "m1") }
            coVerify(exactly = 1) { api.evaluate(jsonState, match { it.ids == triage }, "m2") }
            coVerify(exactly = 1) { api.evaluate(jsonEntry(Ticket("s", "m")), match { it.ids == triage }, "m3") }
        }

        "models is forwarded too" {
            val listed = ModelList(listOf(ModelInfo("jev-latest", null, null)), requestId = "req-1")
            val api = mockk<JevApi> { coEvery { models() } returns listed }
            BlockingJev(api).models().requestId shouldBe "req-1"
            coVerify(exactly = 1) { api.models() }
        }

        "the calls that take options forward them" {
            val options = JevCallOptions { headers["X-Trace-Id"] = "t-1" }
            val api = mockk<JevApi> {
                coEvery { evaluate(any(), any(), any(), any()) } returns jevResult(TRIAGE_RESPONSE, Triage.questions)
                coEvery { models(any()) } returns ModelList(emptyList())
            }
            val blocking = api.blocking()
            blocking.evaluate(jsonState, Triage.questions, "m1", options)
            blocking.models(options)
            coVerify(exactly = 1) { api.evaluate(jsonState, Triage.questions, "m1", options) }
            coVerify(exactly = 1) { api.models(options) }
        }

        "blocking() wraps any JevApi, and gives a client its own blocking view" {
            val (api, _) = answering()
            api.blocking().ask(Triage, "text state")
            coVerify(exactly = 1) { api.evaluate(JsonPrimitive("text state"), Triage.questions, null) }

            JevClient { testDefaults() }.use { client ->
                client.blocking() shouldBeSameInstanceAs client.blocking
            }
        }

        "an interrupted call throws InterruptedException, which every Java-visible call declares" {
            val started = CountDownLatch(1)
            val api = mockk<JevApi> {
                coEvery { evaluate(any(), any(), any()) } coAnswers {
                    started.countDown()
                    awaitCancellation()
                }
            }
            val thrown = AtomicReference<Throwable>()
            val caller = thread {
                runCatching { api.blocking().evaluate(jsonState, Triage.questions) }.onFailure(thrown::set)
            }
            started.await(10, TimeUnit.SECONDS) shouldBe true
            caller.interrupt()
            caller.join(10_000)
            thrown.get().shouldBeInstanceOf<InterruptedException>()

            // javac only lets Java code catch a checked exception that a method declares.
            val calls = BlockingJev::class.java.declaredMethods.filter {
                Modifier.isPublic(it.modifiers) && !it.isSynthetic && it.name != "getApi"
            }
            calls.map { it.name }.toSet() shouldBe setOf("evaluate", "models", "query", "ask")
            calls.forEach { withClue(it) { it.exceptionTypes.toList() shouldContain InterruptedException::class.java } }
        }
    }
}
