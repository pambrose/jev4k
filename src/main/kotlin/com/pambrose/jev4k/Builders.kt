package com.pambrose.jev4k

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.enums.enumEntries

/** Optional descriptions of what a Noul's yes and no mean. */
@JevDsl
class NoulBuilder internal constructor() {
    internal var trueEntry: JsonElement? = null
    internal var falseEntry: JsonElement? = null

    fun whenTrue(description: String) {
        whenTrue(JsonPrimitive(description))
    }

    fun whenTrue(entry: JsonElement) {
        trueEntry = entry
    }

    fun whenFalse(description: String) {
        whenFalse(JsonPrimitive(description))
    }

    fun whenFalse(entry: JsonElement) {
        falseEntry = entry
    }
}

/** A Choice's options, in order. Option keys and descriptions are both sent to the model. */
@JevDsl
class ChoiceBuilder internal constructor() {
    internal val options = LinkedHashMap<String, JsonElement>()

    /** Keys offered more than once. Reported when the question set is validated, with every other problem. */
    internal val duplicates = LinkedHashSet<String>()

    infix fun String.means(description: String) {
        option(this, JsonPrimitive(description))
    }

    infix fun String.means(entry: JsonElement) {
        option(this, entry)
    }

    /** Adds an option; with no description it is sent as `null`, fine when the key says it all. */
    fun option(
        key: String,
        description: String? = null,
    ) {
        option(key, description?.let(::JsonPrimitive) ?: JsonNull)
    }

    fun option(
        key: String,
        entry: JsonElement,
    ) {
        // Recorded, not thrown: throwing here would escape a JevQuery's class initializer as an
        // ExceptionInInitializerError, which no `catch (e: JevException)` can see. The first entry wins.
        if (key in options) {
            duplicates += key
            return
        }
        options[key] = entry
    }

    /** Adds undescribed options. */
    fun options(vararg keys: String) {
        keys.forEach { option(it) }
    }
}

/** A Score's levels, lowest first. Describe situations, not degrees; each level is judged on its own. */
@JevDsl
class ScoreBuilder internal constructor() {
    internal val levels = mutableListOf<JsonElement>()

    fun level(description: String) {
        level(JsonPrimitive(description))
    }

    fun level(entry: JsonElement) {
        levels += entry
    }

    fun levels(vararg descriptions: String) {
        descriptions.forEach(::level)
    }
}

/**
 * Collects the questions for one request, keyed by ids you choose. Every function returns a
 * [QuestionRef] handle, so `val urgent = noul(...)` then `result[urgent]` works too.
 */
@JevDsl
class QueryBuilder internal constructor() {
    private val questions = mutableListOf<QuestionRef<*>>()

    @JvmOverloads
    fun noul(
        id: String,
        instructions: String,
        criteria: (NoulBuilder.() -> Unit)? = null,
    ): QuestionRef<NoulAnswer> = noul(id, JsonPrimitive(instructions), criteria)

    @JvmOverloads
    fun noul(
        id: String,
        instructions: JsonElement,
        criteria: (NoulBuilder.() -> Unit)? = null,
    ): QuestionRef<NoulAnswer> = add(noulRef(id, instructions, criteria))

    fun choice(
        id: String,
        instructions: String,
        options: ChoiceBuilder.() -> Unit,
    ): QuestionRef<ChoiceAnswer<String>> = choice(id, JsonPrimitive(instructions), options)

    fun choice(
        id: String,
        instructions: JsonElement,
        options: ChoiceBuilder.() -> Unit,
    ): QuestionRef<ChoiceAnswer<String>> = add(choiceRef(id, instructions, options))

    /** A Choice over the constants of [E]; see [JevOption] for descriptions and custom keys. */
    @JvmSynthetic
    inline fun <reified E : Enum<E>> choice(
        id: String,
        instructions: String,
    ): QuestionRef<ChoiceAnswer<E>> = choice<E>(id, JsonPrimitive(instructions))

    @JvmSynthetic
    inline fun <reified E : Enum<E>> choice(
        id: String,
        instructions: JsonElement,
    ): QuestionRef<ChoiceAnswer<E>> = add(enumChoiceRef(id, instructions, enumEntries<E>()))

    fun score(
        id: String,
        instructions: String,
        levels: ScoreBuilder.() -> Unit,
    ): QuestionRef<ScoreAnswer> = score(id, JsonPrimitive(instructions), levels)

    fun score(
        id: String,
        instructions: JsonElement,
        levels: ScoreBuilder.() -> Unit,
    ): QuestionRef<ScoreAnswer> = add(scoreRef(id, instructions, levels))

    /** Adds a [Question] built directly, e.g. one generated programmatically. */
    fun question(
        id: String,
        question: Question,
    ): QuestionRef<Answer> = add(QuestionRef(id, question, decode = { it }))

    /** Adds every question of [query], keeping its handles, so typed and ad-hoc questions share one request. */
    fun include(query: JevQuery) {
        questions += query.questions
    }

    @PublishedApi
    internal fun <A : Answer> add(question: QuestionRef<A>): QuestionRef<A> = question.also { questions += it }

    internal fun build(): QuestionSet = QuestionSet(questions)
}

/** Builds a validated [QuestionSet] inline. */
fun questions(block: QueryBuilder.() -> Unit): QuestionSet = QueryBuilder().apply(block).build()
