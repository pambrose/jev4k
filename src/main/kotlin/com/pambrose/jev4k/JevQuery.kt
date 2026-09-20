package com.pambrose.jev4k

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.properties.PropertyDelegateProvider
import kotlin.reflect.KProperty
import kotlin.enums.enumEntries

/**
 * Optional metadata for enum constants used as Choice options. By default an option's key is the
 * constant's name and it is sent undescribed.
 */
interface JevOption {
    /** Plain-text description of the option. */
    val description: String? get() = null

    /** The description as sent; override for structured JSON such as [rubric]. */
    val entry: JsonElement get() = description?.let(::JsonPrimitive) ?: JsonNull

    /** The option key as sent (and matched in the answer), if not the constant's name. */
    val optionKey: String? get() = null
}

/**
 * A reusable set of questions declared as properties; each property name becomes the question id.
 *
 * ```
 * object Triage : JevQuery() {
 *     val urgent by noul("Does this message convey urgency?")
 *     val department by choice<Dept>("Which team should handle this?")
 * }
 * val result = jev.ask(Triage, state = ticket)
 * result[Triage.department].choice
 * ```
 *
 * Questions are validated the first time [questions] is used, so an invalid definition fails on first use.
 */
abstract class JevQuery {
    private val registered = mutableListOf<QuestionRef<*>>()

    /** The declared questions, in declaration order (base-class questions first). */
    val questions: QuestionSet by lazy { QuestionSet(registered) }

    internal fun register(question: QuestionRef<*>) {
        registered += question
    }

    protected fun noul(
        instructions: String,
        id: String? = null,
        criteria: (NoulBuilder.() -> Unit)? = null,
    ): QuestionProvider<NoulAnswer> = noul(JsonPrimitive(instructions), id, criteria)

    protected fun noul(
        instructions: JsonElement,
        id: String? = null,
        criteria: (NoulBuilder.() -> Unit)? = null,
    ): QuestionProvider<NoulAnswer> = QuestionProvider(id) { noulRef(it, instructions, criteria) }

    protected fun choice(
        instructions: String,
        id: String? = null,
        options: ChoiceBuilder.() -> Unit,
    ): QuestionProvider<ChoiceAnswer<String>> = choice(JsonPrimitive(instructions), id, options)

    protected fun choice(
        instructions: JsonElement,
        id: String? = null,
        options: ChoiceBuilder.() -> Unit,
    ): QuestionProvider<ChoiceAnswer<String>> = QuestionProvider(id) { choiceRef(it, instructions, options) }

    /** A Choice over the constants of [E]; see [JevOption] for descriptions and custom keys. */
    @JvmSynthetic
    protected inline fun <reified E : Enum<E>> choice(
        instructions: String,
        id: String? = null,
    ): QuestionProvider<ChoiceAnswer<E>> = enumChoiceProvider(JsonPrimitive(instructions), id, enumEntries<E>())

    @JvmSynthetic
    protected inline fun <reified E : Enum<E>> choice(
        instructions: JsonElement,
        id: String? = null,
    ): QuestionProvider<ChoiceAnswer<E>> = enumChoiceProvider(instructions, id, enumEntries<E>())

    protected fun score(
        instructions: String,
        id: String? = null,
        levels: ScoreBuilder.() -> Unit,
    ): QuestionProvider<ScoreAnswer> = score(JsonPrimitive(instructions), id, levels)

    protected fun score(
        instructions: JsonElement,
        id: String? = null,
        levels: ScoreBuilder.() -> Unit,
    ): QuestionProvider<ScoreAnswer> = QuestionProvider(id) { scoreRef(it, instructions, levels) }

    @PublishedApi
    internal fun <E : Enum<E>> enumChoiceProvider(
        instructions: JsonElement,
        id: String?,
        constants: List<E>,
    ): QuestionProvider<ChoiceAnswer<E>> = QuestionProvider(id) { enumChoiceRef(it, instructions, constants) }
}

/** Creates a question when it is bound to a [JevQuery] property, using the property name as the default id. */
class QuestionProvider<out A : Answer> internal constructor(
    private val id: String?,
    private val create: (String) -> QuestionRef<A>,
) : PropertyDelegateProvider<JevQuery, QuestionRef<A>> {
    override fun provideDelegate(
        thisRef: JevQuery,
        property: KProperty<*>,
    ): QuestionRef<A> = create(id ?: property.name).also(thisRef::register)
}
