package website

import com.pambrose.jev4k.JevApi
import com.pambrose.jev4k.JevOption
import com.pambrose.jev4k.JevQuery
import com.pambrose.jev4k.ask
import com.pambrose.jev4k.query
import com.pambrose.jev4k.rubric
import kotlinx.serialization.json.JsonElement

// --8<-- [start:plain-enum]
// Any enum works; each constant's name is the option key, sent undescribed.
enum class Language { KOTLIN, JAVA, PYTHON, GO, OTHER }

object CodeLanguage : JevQuery() {
    val language by choice<Language>("What programming language is this code written in?")
}
// --8<-- [end:plain-enum]

// --8<-- [start:option-key]
// optionKey changes the key that is sent to the model (and matched in the answer).
enum class Plan(
    override val description: String,
) : JevOption {
    FREE("No paid plan"),
    PRO("A paid plan for one person"),
    ENTERPRISE("A company-wide contract"),
    ;

    override val optionKey: String get() = name.lowercase() // the model sees "free", "pro", "enterprise"
}
// --8<-- [end:option-key]

// --8<-- [start:structured-entry]
// entry replaces the plain description with structured JSON, such as a contrastive rubric.
enum class ReturnTopic(
    override val entry: JsonElement,
) : JevOption {
    RETURN_POLICY(rubric("Whether and how an item can be returned", notFor = "A return already sent")),
    RETURN_STATUS(rubric("Progress of a return already sent", notFor = "Whether an item can be returned")),
}
// --8<-- [end:structured-entry]

suspend fun enumWhen(
    jev: JevApi,
    snippet: String,
) {
    // --8<-- [start:when]
    val language = jev.ask(CodeLanguage, state = snippet)[CodeLanguage.language]

    // choice is a Language, so `when` is exhaustive and the compiler checks every case.
    val linter =
        when (language.choice) {
            Language.KOTLIN -> "ktlint"
            Language.JAVA -> "checkstyle"
            Language.PYTHON -> "ruff"
            Language.GO -> "golangci-lint"
            Language.OTHER -> null
        }

    // Probabilities are keyed by constant, in declaration order.
    val kotlinOrJava = language.probability(Language.KOTLIN) + language.probability(Language.JAVA)
    // --8<-- [end:when]
    println("$linter $kotlinOrJava")
}

suspend fun enumInline(
    jev: JevApi,
    message: String,
) {
    // --8<-- [start:inline]
    // Enum choices work in the inline DSL too; read them back with enumChoice<E>(id).
    val result =
        jev.query(state = message) {
            choice<Plan>("plan", "Which plan is the customer on?")
            choice<ReturnTopic>("topic", "Which returns topic is the customer asking about?")
        }
    val plan: Plan = result.enumChoice<Plan>("plan").choice
    val topic: ReturnTopic = result.enumChoice<ReturnTopic>("topic").choice
    // --8<-- [end:inline]
    println("$plan $topic")
}
