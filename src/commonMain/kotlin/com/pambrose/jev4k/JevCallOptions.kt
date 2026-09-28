package com.pambrose.jev4k

import com.pambrose.jev4k.internal.sendProblem
import kotlinx.serialization.json.JsonElement
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Settings for single calls that override the client's own, as both official SDKs allow:
 *
 * ```
 * val urgent = JevCallOptions {
 *     timeout = 2.seconds
 *     retry = RetryPolicy.NONE
 *     headers["X-Trace-Id"] = traceId
 * }
 * val result = jev.withOptions(urgent).ask(Triage, state = ticket)
 * ```
 *
 * A setting left unset keeps the client's value. Pass options to [JevApi.evaluate] or [JevApi.models] directly, or
 * wrap an API with [withOptions] so that `query`, `ask` and the blocking calls use them too. Options are checked when
 * they're built and can't change afterwards, so one instance can be shared.
 *
 * @property timeout The timeout for each HTTP attempt, instead of the client's.
 * @property retry The retry policy, replacing the client's policy as a whole.
 * @property headers Headers to send. One with the name of a client header, or of a built-in one such as
 *   `Authorization`, replaces it.
 * @property extraBody Top-level fields added to the `evaluate` request body, for API parameters jev4k doesn't model.
 *   `models()` sends no body, so it ignores them.
 */
class JevCallOptions internal constructor(
    val timeout: Duration?,
    val retry: RetryPolicy?,
    val headers: Map<String, String>,
    val extraBody: Map<String, JsonElement>,
) {
    /** Builds options inline. From Java: `new JevCallOptions(options -> { ...; return Unit.INSTANCE; })`. */
    constructor(block: JevCallOptionsBuilder.() -> Unit) : this(JevCallOptionsBuilder().apply(block).build())

    private constructor(options: JevCallOptions) :
        this(options.timeout, options.retry, options.headers, options.extraBody)

    /** These options with [later]'s over them: a setting [later] sets wins, and headers and body fields merge. */
    internal operator fun plus(later: JevCallOptions): JevCallOptions =
        JevCallOptions(
            timeout = later.timeout ?: timeout,
            retry = later.retry ?: retry,
            headers = headers + later.headers,
            extraBody = extraBody + later.extraBody,
        )

    // Header values and body fields can hold secrets, so only their names are shown.
    override fun toString(): String =
        "JevCallOptions(timeout=$timeout, retry=$retry, headers=${headers.keys}, extraBody=${extraBody.keys})"
}

/** Options that change nothing, for the calls made without any. */
internal val NoCallOptions = JevCallOptions(null, null, emptyMap(), emptyMap())

/**
 * Builder for [JevCallOptions]. Every setting starts unset, which keeps the client's value. [build] reports every
 * problem it finds at once, as [JevConfigBuilder.build] does.
 */
@JevDsl
class JevCallOptionsBuilder {
    /** Timeout for each HTTP attempt. */
    var timeout: Duration? = null

    /** [timeout] in milliseconds, for Java, which can't call its `Duration` setter. */
    var timeoutMillis: Long?
        get() = timeout?.inWholeMilliseconds
        set(value) {
            timeout = value?.milliseconds
        }

    /** Retry policy, replacing the client's policy as a whole. */
    var retry: RetryPolicy? = null

    /** Headers to send, over the client's own and the built-in ones. */
    val headers: MutableMap<String, String> = linkedMapOf()

    /**
     * Top-level fields to add to the `evaluate` request body. `state`, `model` and `questions` come from the call's
     * arguments, so they can't be set here.
     */
    val extraBody: MutableMap<String, JsonElement> = linkedMapOf()

    fun build(): JevCallOptions {
        val problems = buildList {
            timeout?.let { timeoutProblem(it)?.let(::add) }
            headers.forEach { (name, value) -> headerProblem(name, value)?.let(::add) }
            extraBody.forEach { (name, value) ->
                if (name in REQUEST_FIELDS) add("extraBody can't set '$name'; pass it as an argument")
                value.sendProblem("extraBody '$name'")?.let(::add)
            }
        }
        if (problems.isNotEmpty()) throw JevConfigException("Invalid call options: ${problems.joinToString("; ")}")
        return JevCallOptions(timeout, retry?.snapshot(), headers.toMap(), extraBody.toMap())
    }

    private companion object {
        val REQUEST_FIELDS = setOf("state", "model", "questions")
    }
}

/**
 * This API with [options] applied to every call, including those made through `query`, `ask` and [blocking].
 * Options passed to a call on the result are merged over [options].
 */
fun JevApi.withOptions(options: JevCallOptions): JevApi =
    if (this is OptionsApi) OptionsApi(api, this.options + options) else OptionsApi(this, options)

private class OptionsApi(
    val api: JevApi,
    val options: JevCallOptions,
) : JevApi {
    override suspend fun evaluate(
        state: JsonElement,
        questions: QuestionSet,
        model: String?,
    ): JevResult = api.evaluate(state, questions, model, options)

    override suspend fun evaluate(
        state: JsonElement,
        questions: QuestionSet,
        model: String?,
        options: JevCallOptions,
    ): JevResult = api.evaluate(state, questions, model, this.options + options)

    override suspend fun models(): ModelList = api.models(options)

    override suspend fun models(options: JevCallOptions): ModelList = api.models(this.options + options)
}
