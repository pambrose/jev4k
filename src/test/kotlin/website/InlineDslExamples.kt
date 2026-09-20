package website

import com.pambrose.jev4k.ChoiceAnswer
import com.pambrose.jev4k.ChoiceQuestion
import com.pambrose.jev4k.JevApi
import com.pambrose.jev4k.NoulAnswer
import com.pambrose.jev4k.NoulQuestion
import com.pambrose.jev4k.QuestionRef
import com.pambrose.jev4k.query
import com.pambrose.jev4k.questions
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

suspend fun inlineQuery(
    jev: JevApi,
    ticket: String,
) {
    // --8<-- [start:query-block]
    val result =
        jev.query(state = ticket) {
            noul("refund", "Does the customer explicitly ask for a refund or credit?") {
                whenTrue("Directly asks for money back or an account credit")
                whenFalse("A complaint or question with no requested remedy")
            }
            choice("topic", "Which returns topic is the customer asking about?") {
                "return_policy" means "Whether and how an item can be returned"
                option("return_status", "Progress of a return already sent")
                option("other") // no description: sent as null
            }
            score("severity", "How severe is the reported issue?") {
                levels("Cosmetic", "Broken, but a workaround exists", "Blocking, no workaround")
            }
        }

    result.noul("refund").isTrue()
    result.choice("topic").choice
    result.score("severity").score
    // --8<-- [end:query-block]
}

suspend fun inlineHandles(
    jev: JevApi,
    ticket: String,
) {
    // --8<-- [start:handles]
    // Builder functions return typed handles too, which avoids repeating string ids.
    lateinit var refund: QuestionRef<NoulAnswer>
    lateinit var tone: QuestionRef<ChoiceAnswer<String>>

    val result =
        jev.query(state = ticket) {
            refund = noul("refund", "Does the customer explicitly ask for a refund?")
            tone = choice("tone", "What is the customer's tone?") { options("calm", "frustrated", "angry") }
        }

    val wantsRefund: NoulAnswer = result[refund]
    val customerTone: ChoiceAnswer<String> = result[tone]
    // --8<-- [end:handles]
    println("$wantsRefund $customerTone")
}

// --8<-- [start:loops]
// Questions are just code, so generate them. Here: one Noul per policy clause.
suspend fun violatedClauses(
    jev: JevApi,
    post: String,
    clauses: Map<String, String>,
): List<String> {
    val result =
        jev.query(state = post) {
            clauses.forEach { (id, clause) -> noul(id, "Does the post violate this rule: $clause?") }
        }
    return clauses.keys.filter { result.noul(it).isTrue(threshold = 0.7) }
}
// --8<-- [end:loops]

// --8<-- [start:evaluate]
// Build a QuestionSet once and evaluate it against many states.
val toneCheck =
    questions {
        choice("tone", "What is the customer's tone?") { options("calm", "frustrated", "angry") }
        noul("threat", "Does the message threaten to cancel or leave?")
    }

suspend fun tones(
    jev: JevApi,
    messages: List<String>,
): List<String> = messages.map { jev.evaluate(JsonPrimitive(it), toneCheck).choice("tone").choice }
// --8<-- [end:evaluate]

suspend fun inlineProgrammatic(jev: JevApi) {
    // --8<-- [start:programmatic]
    // Question values can also be built directly, e.g. from configuration or a database.
    val region =
        ChoiceQuestion(
            instructions = JsonPrimitive("Which region is the customer writing from?"),
            options =
                mapOf(
                    "na" to JsonPrimitive("North America"),
                    "eu" to JsonPrimitive("Europe"),
                    "other" to JsonNull,
                ),
        )
    val result =
        jev.query(state = "Bonjour, ma commande n'est jamais arrivée à Lyon.") {
            question("region", region)
            question("is_complaint", NoulQuestion(JsonPrimitive("Is the customer complaining?")))
        }
    // --8<-- [end:programmatic]
    println(result.answers)
}
