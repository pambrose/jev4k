package com.pambrose.jev4k

import com.pambrose.jev4k.internal.ResponseInfo
import com.pambrose.jev4k.internal.enumTypeName
import kotlinx.serialization.json.JsonObject
import kotlin.enums.enumEntries
import kotlin.jvm.JvmSynthetic

/**
 * The answers to one request. Read them with the handles you declared (`result[Triage.department]`)
 * or by id (`result.noul("urgent")`). A missing or mismatched answer raises
 * [JevResponseValidationException] when it is read; an id or handle that wasn't part of the request
 * raises [IllegalArgumentException].
 */
class JevResult internal constructor(
    /** The model that answered, as reported by the API (falls back to [requestedModel][JevResult.requestedModel]). */
    val model: String,
    /** The model name that was sent, possibly an alias such as `jev-latest`. */
    val requestedModel: String,
    val usage: Usage,
    val questions: QuestionSet,
    /** Every answer by question id, choices keyed by option string. */
    val answers: Map<String, Answer>,
    /** The response body as received. */
    val raw: JsonObject,
    private val response: ResponseInfo,
) {
    /** The `x-typesafe-request-id` response header. */
    val requestId: String? get() = response.requestId

    operator fun <A : Answer> get(ref: QuestionRef<A>): A {
        require(ref in questions) {
            if (questions[ref.id] == null) {
                "Question '${ref.id}' is not part of this request"
            } else {
                // Typically a mock that returns a result built for a fixed set, while the code under test builds
                // its questions, and so its handles, afresh on every call.
                "Question '${ref.id}' is part of this request, but through a different handle: this handle belongs " +
                    "to another QuestionSet. Read the answer by id, or build the result from the set that was sent"
            }
        }
        return decodeAnswer(ref.id, ref.decode)
    }

    fun noul(id: String): NoulAnswer {
        requireQuestion<NoulQuestion>(id, "noul")
        return decodeAnswer(id, ::decodeNoul)
    }

    /**
     * Reads a Choice by id, keyed by option string. The chosen option is returned as the server sent it, even one the
     * question didn't declare, as both official SDKs do; [enumChoice] rejects one, since it has no constant for it.
     */
    fun choice(id: String): ChoiceAnswer<String> {
        requireQuestion<ChoiceQuestion>(id, "choice")
        return decodeAnswer(id, ::decodeChoice)
    }

    fun score(id: String): ScoreAnswer {
        requireQuestion<ScoreQuestion>(id, "score")
        return decodeAnswer(id, ::decodeScore)
    }

    /**
     * Reads a Choice by id as constants of [E]. [E] must have a constant for every option the question declared,
     * else this is a mistake in the calling code, reported as [IllegalArgumentException]. An option the server
     * returns that the question never declared is a [JevResponseValidationException].
     */
    @JvmSynthetic
    inline fun <reified E : Enum<E>> enumChoice(id: String): ChoiceAnswer<E> = enumChoiceOf(id, enumEntries<E>())

    @PublishedApi
    internal fun <E : Enum<E>> enumChoiceOf(
        id: String,
        constants: List<E>,
    ): ChoiceAnswer<E> {
        val byKey = constants.associateBy(::enumOptionKey)
        val declared = requireQuestion<ChoiceQuestion>(id, "choice").options.keys
        require(byKey.keys.containsAll(declared)) {
            val enumName = constants.firstOrNull()?.enumTypeName() ?: "the enum"
            "Question '$id' declares options $declared, but $enumName has no constant for " +
                "${declared - byKey.keys} (its option keys: ${byKey.keys})"
        }
        return decodeAnswer(id) { decodeEnumChoice(it, byKey) }
    }

    val nouls: Map<String, NoulAnswer> get() = answersOf<NoulAnswer>()

    @Suppress("UNCHECKED_CAST")
    val choices: Map<String, ChoiceAnswer<String>>
        get() = answersOf<ChoiceAnswer<*>>() as Map<String, ChoiceAnswer<String>>

    val scores: Map<String, ScoreAnswer> get() = answersOf<ScoreAnswer>()

    private inline fun <reified T : Answer> answersOf(): Map<String, T> =
        answers.mapNotNull { (id, answer) -> (answer as? T)?.let { id to it } }.toMap()

    /**
     * Checks that `id` was asked and that it is a [Q], and returns that question. [Question] is sealed, so the shape
     * is checked by the compiler; [label] only names the expected type in the failure message.
     */
    private inline fun <reified Q : Question> requireQuestion(
        id: String,
        label: String,
    ): Q {
        val ref = requireNotNull(questions[id]) { "No question '$id' in this request (ids: ${questions.ids})" }
        val question = ref.question
        require(question is Q) { "Question '$id' is a ${question.typeName}, not a $label" }
        return question
    }

    private fun <T> decodeAnswer(
        id: String,
        decode: (Answer) -> T,
    ): T {
        val path = "answers.$id"
        val answer = answers[id] ?: response.fail(path, "no answer returned for question '$id'")
        return try {
            decode(answer)
        } catch (e: AnswerDecodingException) {
            response.fail(path, "question '$id': ${e.message}", e)
        }
    }

    override fun toString(): String = "JevResult(model=$model, requestId=$requestId, usage=$usage, answers=$answers)"
}
