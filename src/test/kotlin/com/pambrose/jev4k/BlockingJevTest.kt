package com.pambrose.jev4k

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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

            coVerify(exactly = 1) { api.evaluate(JsonPrimitive("text state"), any(), "m1") }
            coVerify(exactly = 1) { api.evaluate(jsonState, any(), "m2") }
            coVerify(exactly = 1) { api.evaluate(jsonEntry(Ticket("s", "m")), any(), "m3") }
        }

        "models is forwarded too" {
            val listed = listOf(ModelInfo("jev-latest", null, null))
            val api = mockk<JevApi> { coEvery { models() } returns listed }
            BlockingJev(api).models() shouldBe listed
            coVerify(exactly = 1) { api.models() }
        }
    }
}
