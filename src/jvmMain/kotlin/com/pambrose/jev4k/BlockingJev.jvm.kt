package com.pambrose.jev4k

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement

/**
 * [JevApi] as blocking calls, for scripts, `main`, and tests. Every call declares [InterruptedException], which
 * `runBlocking` throws when the waiting thread is interrupted, so Java code can catch it.
 */
actual class BlockingJev internal actual constructor(
    @PublishedApi internal val api: JevApi,
) {
    @JvmOverloads
    @Throws(InterruptedException::class)
    fun evaluate(
        state: JsonElement,
        questions: QuestionSet,
        model: String? = null,
    ): JevResult = runBlocking { api.evaluate(state, questions, model) }

    @Throws(InterruptedException::class)
    fun evaluate(
        state: JsonElement,
        questions: QuestionSet,
        model: String?,
        options: JevCallOptions,
    ): JevResult = runBlocking { api.evaluate(state, questions, model, options) }

    @Throws(InterruptedException::class)
    fun models(): ModelList = runBlocking { api.models() }

    @Throws(InterruptedException::class)
    fun models(options: JevCallOptions): ModelList = runBlocking { api.models(options) }

    @JvmOverloads
    @Throws(InterruptedException::class)
    fun query(
        state: String,
        model: String? = null,
        block: QueryBuilder.() -> Unit,
    ): JevResult = runBlocking { api.query(state, model, block) }

    @JvmOverloads
    @Throws(InterruptedException::class)
    fun query(
        state: JsonElement,
        model: String? = null,
        block: QueryBuilder.() -> Unit,
    ): JevResult = runBlocking { api.query(state, model, block) }

    @JvmSynthetic
    @Throws(InterruptedException::class)
    inline fun <reified T : Any> query(
        state: T,
        model: String? = null,
        noinline block: QueryBuilder.() -> Unit,
    ): JevResult = runBlocking { api.query(state, model, block) }

    @JvmOverloads
    @Throws(InterruptedException::class)
    fun ask(
        query: JevQuery,
        state: String,
        model: String? = null,
    ): JevResult = runBlocking { api.ask(query, state, model) }

    @JvmOverloads
    @Throws(InterruptedException::class)
    fun ask(
        query: JevQuery,
        state: JsonElement,
        model: String? = null,
    ): JevResult = runBlocking { api.ask(query, state, model) }

    @JvmSynthetic
    @Throws(InterruptedException::class)
    inline fun <reified T : Any> ask(
        query: JevQuery,
        state: T,
        model: String? = null,
    ): JevResult = runBlocking { api.ask(query, state, model) }
}
