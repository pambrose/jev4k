package com.pambrose.jev4k

import kotlinx.serialization.json.JsonObject
import kotlin.enums.enumEntries

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
    /** The `x-typesafe-request-id` response header. */
    val requestId: String?,
    val questions: QuestionSet,
    /** Every answer by question id, choices keyed by option string. */
    val answers: Map<String, Answer>,
    /** The response body as received. */
    val raw: JsonObject,
    private val endpoint: String,
) {
    operator fun <A : Answer> get(ref: QuestionRef<A>): A {
        require(ref in questions) { "Question '${ref.id}' is not part of this request" }
        return decodeAnswer(ref.id, ref.decode)
    }

    fun noul(id: String): NoulAnswer = decodeAnswer(requireQuestion<NoulQuestion>(id, "noul"), ::decodeNoul)

    fun choice(id: String): ChoiceAnswer<String> =
        decodeAnswer(requireQuestion<ChoiceQuestion>(id, "choice"), ::decodeChoice)

    fun score(id: String): ScoreAnswer = decodeAnswer(requireQuestion<ScoreQuestion>(id, "score"), ::decodeScore)

    /** Reads a Choice by id as constants of [E]. */
    @JvmSynthetic
    inline fun <reified E : Enum<E>> enumChoice(id: String): ChoiceAnswer<E> = enumChoiceOf(id, enumEntries<E>())

    @PublishedApi
    internal fun <E : Enum<E>> enumChoiceOf(
        id: String,
        constants: List<E>,
    ): ChoiceAnswer<E> {
        val byKey = constants.associateBy(::enumOptionKey)
        return decodeAnswer(requireQuestion<ChoiceQuestion>(id, "choice")) { decodeEnumChoice(it, byKey) }
    }

    val nouls: Map<String, NoulAnswer> get() = answersOf<NoulAnswer>()

    @Suppress("UNCHECKED_CAST")
    val choices: Map<String, ChoiceAnswer<String>>
        get() = answersOf<ChoiceAnswer<*>>() as Map<String, ChoiceAnswer<String>>

    val scores: Map<String, ScoreAnswer> get() = answersOf<ScoreAnswer>()

    private inline fun <reified T : Answer> answersOf(): Map<String, T> =
        answers.mapNotNull { (id, answer) -> (answer as? T)?.let { id to it } }.toMap()

    /**
     * Checks that `id` was asked and that it is a [Q], and returns it. [Question] is sealed, so the shape is
     * checked by the compiler; [label] only names the expected type in the failure message.
     */
    private inline fun <reified Q : Question> requireQuestion(
        id: String,
        label: String,
    ): String {
        val ref = requireNotNull(questions[id]) { "No question '$id' in this request (ids: ${questions.ids})" }
        require(ref.question is Q) { "Question '$id' is a ${ref.question.typeName}, not a $label" }
        return id
    }

    private fun <T> decodeAnswer(
        id: String,
        decode: (Answer) -> T,
    ): T {
        val answer = answers[id] ?: throw invalid("answers.$id", "no answer returned for question '$id'")
        return try {
            decode(answer)
        } catch (e: AnswerDecodingException) {
            throw invalid("answers.$id", "question '$id': ${e.message}", e)
        }
    }

    private fun invalid(
        fieldPath: String,
        detail: String,
        cause: Throwable? = null,
    ) = JevResponseValidationException(detail, fieldPath, raw.toString(), requestId, endpoint, cause = cause)

    override fun toString(): String = "JevResult(model=$model, requestId=$requestId, usage=$usage, answers=$answers)"
}
