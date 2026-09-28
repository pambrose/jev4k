package com.pambrose.jev4k

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.jvm.JvmSynthetic

/**
 * The Jev API. [evaluate] is the only call that sends questions; [query] and [ask] are conveniences
 * on top of it, so application code that depends on [JevApi] can be tested with a fake or mock.
 */
interface JevApi {
    /**
     * Asks [questions] about [state] (`POST /v1/systemone`), using the default model unless [model] is given. A blank
     * [model] counts as not given.
     */
    suspend fun evaluate(
        state: JsonElement,
        questions: QuestionSet,
        model: String? = null,
    ): JevResult

    /**
     * [evaluate] with [options] overriding the client's settings for this one call.
     *
     * The default implementation ignores [options] and calls the three-argument [evaluate], so a fake written before
     * this member existed keeps compiling. [JevClient] applies them; a [JevApi] that wraps another should override
     * this and pass them on.
     */
    suspend fun evaluate(
        state: JsonElement,
        questions: QuestionSet,
        model: String?,
        options: JevCallOptions,
    ): JevResult = evaluate(state, questions, model)

    /** The models this account can use (`GET /v1/models`), with the call's request id. */
    suspend fun models(): ModelList

    /** [models] with [options] overriding the client's settings. The default implementation ignores them. */
    suspend fun models(options: JevCallOptions): ModelList = models()
}

/** Asks inline questions about a text [state]. */
suspend fun JevApi.query(
    state: String,
    model: String? = null,
    block: QueryBuilder.() -> Unit,
): JevResult = evaluate(JsonPrimitive(state), questions(block), model)

/** Asks inline questions about a JSON [state]. */
suspend fun JevApi.query(
    state: JsonElement,
    model: String? = null,
    block: QueryBuilder.() -> Unit,
): JevResult = evaluate(state, questions(block), model)

/** Asks inline questions about a `@Serializable` [state]. */
@JvmSynthetic
suspend inline fun <reified T : Any> JevApi.query(
    state: T,
    model: String? = null,
    noinline block: QueryBuilder.() -> Unit,
): JevResult = evaluate(jsonEntry(state), questions(block), model)

/** Asks a [JevQuery]'s questions about a text [state]. */
suspend fun JevApi.ask(
    query: JevQuery,
    state: String,
    model: String? = null,
): JevResult = evaluate(JsonPrimitive(state), query.questions, model)

/** Asks a [JevQuery]'s questions about a JSON [state]. */
suspend fun JevApi.ask(
    query: JevQuery,
    state: JsonElement,
    model: String? = null,
): JevResult = evaluate(state, query.questions, model)

/** Asks a [JevQuery]'s questions about a `@Serializable` [state]. */
@JvmSynthetic
suspend inline fun <reified T : Any> JevApi.ask(
    query: JevQuery,
    state: T,
    model: String? = null,
): JevResult = evaluate(jsonEntry(state), query.questions, model)
