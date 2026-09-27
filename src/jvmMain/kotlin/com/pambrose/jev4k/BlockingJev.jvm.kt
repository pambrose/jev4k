package com.pambrose.jev4k

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement

/** [JevApi] as blocking calls, for scripts, `main`, and tests. */
actual class BlockingJev internal actual constructor(
    @PublishedApi internal val api: JevApi,
) {
    @JvmOverloads
    fun evaluate(
        state: JsonElement,
        questions: QuestionSet,
        model: String? = null,
    ): JevResult = runBlocking { api.evaluate(state, questions, model) }

    fun models(): List<ModelInfo> = runBlocking { api.models() }

    @JvmOverloads
    fun query(
        state: String,
        model: String? = null,
        block: QueryBuilder.() -> Unit,
    ): JevResult = runBlocking { api.query(state, model, block) }

    @JvmOverloads
    fun query(
        state: JsonElement,
        model: String? = null,
        block: QueryBuilder.() -> Unit,
    ): JevResult = runBlocking { api.query(state, model, block) }

    @JvmSynthetic
    inline fun <reified T : Any> query(
        state: T,
        model: String? = null,
        noinline block: QueryBuilder.() -> Unit,
    ): JevResult = runBlocking { api.query(state, model, block) }

    @JvmOverloads
    fun ask(
        query: JevQuery,
        state: String,
        model: String? = null,
    ): JevResult = runBlocking { api.ask(query, state, model) }

    @JvmOverloads
    fun ask(
        query: JevQuery,
        state: JsonElement,
        model: String? = null,
    ): JevResult = runBlocking { api.ask(query, state, model) }

    @JvmSynthetic
    inline fun <reified T : Any> ask(
        query: JevQuery,
        state: T,
        model: String? = null,
    ): JevResult = runBlocking { api.ask(query, state, model) }
}
