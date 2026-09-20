package com.pambrose.jev4k

import com.pambrose.jev4k.internal.JevJson
import com.pambrose.jev4k.internal.WireQuestion
import com.pambrose.jev4k.internal.toWire
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.reflect.KProperty

/** Most options a Choice may offer. */
const val MAX_CHOICE_OPTIONS = 255

/** Fewest levels a Score may have. */
const val MIN_SCORE_LEVELS = 2

/** Most levels a Score may have. */
const val MAX_SCORE_LEVELS = 10

/**
 * What one question asks, independent of its id: instructions plus criteria, each arbitrary JSON
 * (usually a string). Named after the TypeSafe JS SDK's `Question` / `NoulQuestion` / `ChoiceQuestion`
 * / `ScoreQuestion`. A [QuestionRef] pairs one with an id.
 */
sealed interface Question {
    val instructions: JsonElement
}

/**
 * A yes/no question; [whenTrue][NoulQuestion.whenTrue] and [whenFalse][NoulQuestion.whenFalse] optionally
 * define what yes and no mean.
 */
data class NoulQuestion(
    override val instructions: JsonElement,
    val whenTrue: JsonElement? = null,
    val whenFalse: JsonElement? = null,
) : Question

/** Pick one of [options][ChoiceQuestion.options], in order; a [JsonNull] description sends the option undescribed. */
data class ChoiceQuestion(
    override val instructions: JsonElement,
    val options: Map<String, JsonElement>,
) : Question

/** Rate along ordered [levels][ScoreQuestion.levels]; a level's number is its index. */
data class ScoreQuestion(
    override val instructions: JsonElement,
    val levels: List<JsonElement>,
) : Question

/**
 * A handle to one question. The same handle reads its typed answer back from a [JevResult], and it
 * can be used as a property delegate so `val urgent by noul(...)` works in a [JevQuery].
 */
class QuestionRef<out A : Answer> internal constructor(
    val id: String,
    val question: Question,
    internal val decode: (Answer) -> A,
    /**
     * Problems noticed while the question was being built. They are carried rather than thrown, so that a
     * question declared as a property delegate fails when its [QuestionSet] is validated, with everything else
     * that is wrong with it, instead of from a class initializer.
     */
    internal val problems: List<String> = emptyList(),
) {
    operator fun getValue(
        thisRef: Any?,
        property: KProperty<*>,
    ): QuestionRef<A> = this

    override fun toString(): String = "QuestionRef(id=$id, question=$question)"
}

/** An ordered, validated set of questions: everything one request asks about a single state. */
class QuestionSet internal constructor(
    questions: List<QuestionRef<*>>,
) : Iterable<QuestionRef<*>> {
    private val questions: List<QuestionRef<*>> = questions.toList()

    init {
        val problems = validate(this.questions)
        if (problems.isNotEmpty()) throw JevValidationException(problems)
    }

    // Declared after the init block on purpose: initializers and init blocks run in declaration order, so
    // validation has already rejected duplicate ids by the time this map is built.
    private val byId: Map<String, QuestionRef<*>> = this.questions.associateBy { it.id }

    val ids: List<String> by lazy { questions.map { it.id } }

    val size: Int get() = questions.size

    operator fun get(id: String): QuestionRef<*>? = byId[id]

    /** True if this exact handle (not just its id) belongs to the set. */
    operator fun contains(question: QuestionRef<*>): Boolean = byId[question.id] === question

    override fun iterator(): Iterator<QuestionRef<*>> = questions.iterator()

    /** The `questions` object exactly as it is sent, e.g. for logging or cache keys. */
    fun toJson(): JsonObject =
        JevJson.encodeToJsonElement(MapSerializer(String.serializer(), WireQuestion.serializer()), toWire()).jsonObject
}

internal val Question.typeName: String
    get() = when (this) {
        is NoulQuestion -> "noul"
        is ChoiceQuestion -> "choice"
        is ScoreQuestion -> "score"
    }

/** Null, or a blank string. */
private fun JsonElement.isEmptyEntry(): Boolean =
    this is JsonNull || (this is JsonPrimitive && isString && content.isBlank())

private const val INSTRUCTIONS_REQUIRED =
    "instructions must not be empty; question ids are never sent to the model, so the instructions must state " +
        "the full question"

internal fun validate(questions: List<QuestionRef<*>>): List<String> =
    buildList {
        if (questions.isEmpty()) add("A request needs at least one question")
        val seen = mutableSetOf<String>()
        for (ref in questions) {
            when {
                ref.id.isBlank() -> add("Question ids must not be blank")
                !seen.add(ref.id) -> add("Duplicate question id '${ref.id}'")
            }
            ref.problems.forEach { add("question '${ref.id}': $it") }
            addAll(validate(ref.id, ref.question))
        }
    }

private fun validate(
    id: String,
    question: Question,
): List<String> =
    buildList {
        if (question.instructions.isEmptyEntry()) add("question '$id': $INSTRUCTIONS_REQUIRED")
        when (question) {
            is NoulQuestion -> {
                // Noul criteria are optional; nothing else to check.
            }

            is ChoiceQuestion -> {
                if (question.options.size !in 1..MAX_CHOICE_OPTIONS) {
                    add("question '$id': a Choice needs 1..$MAX_CHOICE_OPTIONS options (got ${question.options.size})")
                }
                if (question.options.keys.any { it.isBlank() }) {
                    add("question '$id': Choice option keys must not be blank")
                }
            }

            is ScoreQuestion -> {
                if (question.levels.size !in MIN_SCORE_LEVELS..MAX_SCORE_LEVELS) {
                    val range = "$MIN_SCORE_LEVELS..$MAX_SCORE_LEVELS"
                    add("question '$id': a Score needs $range levels (got ${question.levels.size})")
                }
            }
        }
    }

internal fun noulRef(
    id: String,
    instructions: JsonElement,
    criteria: (NoulBuilder.() -> Unit)?,
): QuestionRef<NoulAnswer> {
    val builder = NoulBuilder().apply { criteria?.invoke(this) }
    return QuestionRef(id, NoulQuestion(instructions, builder.trueEntry, builder.falseEntry), ::decodeNoul)
}

internal fun choiceRef(
    id: String,
    instructions: JsonElement,
    options: ChoiceBuilder.() -> Unit,
): QuestionRef<ChoiceAnswer<String>> {
    val builder = ChoiceBuilder().apply(options)
    return QuestionRef(
        id = id,
        question = ChoiceQuestion(instructions, builder.options.toMap()),
        decode = ::decodeChoice,
        problems = builder.duplicates.map { "duplicate Choice option '$it'" },
    )
}

internal fun scoreRef(
    id: String,
    instructions: JsonElement,
    levels: ScoreBuilder.() -> Unit,
): QuestionRef<ScoreAnswer> =
    QuestionRef(id, ScoreQuestion(instructions, ScoreBuilder().apply(levels).levels.toList()), ::decodeScore)

@PublishedApi
internal fun <E : Enum<E>> enumChoiceRef(
    id: String,
    instructions: JsonElement,
    constants: List<E>,
): QuestionRef<ChoiceAnswer<E>> {
    val byKey = LinkedHashMap<String, E>()
    val options = LinkedHashMap<String, JsonElement>()
    val problems = mutableListOf<String>()
    for (constant in constants) {
        val key = enumOptionKey(constant)
        if (byKey.put(key, constant) != null) {
            problems += "duplicate Choice option '$key' in enum ${constant.declaringJavaClass.simpleName}"
        }
        options[key] = (constant as? JevOption)?.entry ?: JsonNull
    }
    return QuestionRef(
        id = id,
        question = ChoiceQuestion(instructions, options),
        decode = { decodeEnumChoice(it, byKey) },
        problems = problems,
    )
}
