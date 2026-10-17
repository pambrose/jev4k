package com.pambrose.jev4k

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.properties.PropertyDelegateProvider
import kotlin.reflect.KProperty
import kotlin.enums.enumEntries
import kotlin.jvm.JvmSynthetic

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
 * Questions are validated the first time [questions] is used, so an invalid definition fails on first use, with a
 * [JevValidationException], rather than from the object's initializer. That covers a problem found while a question is
 * built too, such as an unsupported value passed to `entry()` in a Choice's options or in an enum option's
 * [JevOption.entry]. An argument is evaluated before the builder runs, though, so a failing `entry()` or
 * `jsonEntry()` passed as the instructions still throws from the initializer.
 *
 * [questions] can be read once the first question has registered. A read before every property is initialized, from
 * an `init` block or a base class, sees only the questions declared so far; the next read includes the rest. A read
 * before any question has registered, such as from the `init` block of a base class with no questions of its own,
 * fails with a [JevValidationException], since a set needs at least one question.
 *
 * The builders take the instructions first and an optional `id` second, the reverse of the inline [QueryBuilder]'s
 * `(id, instructions)`. Both are strings, so pass `id` by name (`noul("Is this urgent?", id = "urgent")`): a
 * question moved from an inline query with its arguments unchanged would otherwise compile and send its id as the
 * instructions.
 */
abstract class JevQuery {
    private val registered = mutableListOf<QuestionRef<*>>()
    private var built: QuestionSet? = null
    private val registeredImages = mutableListOf<JevImage>()

    /** The declared questions, in declaration order (base-class questions first), with the images [attach]ed. */
    val questions: QuestionSet
        // Rebuilt when questions or images have registered since the last read, which only happens during
        // initialization. Once that is over the set never changes, and a race between two first reads just builds it
        // twice.
        get() =
            built?.takeIf { it.size == registered.size && it.images.size == registeredImages.size }
                ?: QuestionSet(registered, registeredImages).also { built = it }

    internal fun register(question: QuestionRef<*>) {
        registered += question
    }

    /**
     * Attaches [image] to every request that asks these questions, and returns it, so a picture can be declared and
     * attached at once: `private val thumb = attach(JevImage.of(thumbBytes))`. Reading a file here, with
     * [JevImage.read], does I/O in the object's initializer; a missing file then fails the object's first use.
     */
    @ExperimentalJevImages
    protected fun attach(image: JevImage): JevImage {
        if (registeredImages.none { it === image }) registeredImages += image
        return image
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

    /** A Box: where, in an image attached to the request, the thing [instructions] describes is. Needs Jev 1.14. */
    @ExperimentalJevImages
    protected fun box(
        instructions: String,
        id: String? = null,
    ): QuestionProvider<BoxAnswer> = box(JsonPrimitive(instructions), id)

    @ExperimentalJevImages
    protected fun box(
        instructions: JsonElement,
        id: String? = null,
    ): QuestionProvider<BoxAnswer> = QuestionProvider(id) { boxRef(it, instructions) }

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
    ): QuestionRef<A> {
        val questionId = id ?: property.name
        // Building runs the builder lambda, and for an enum Choice each constant's JevOption.entry, so entry() or
        // jsonOf() can throw here. From a JevQuery object's initializer that would escape as an
        // ExceptionInInitializerError; a stand-in carries it to the set's validation instead.
        val ref =
            try {
                create(questionId)
            } catch (e: JevValidationException) {
                failedQuestionRef(questionId, e.problems)
            }
        return ref.also(thisRef::register)
    }
}
