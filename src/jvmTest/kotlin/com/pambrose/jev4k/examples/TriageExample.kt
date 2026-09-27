package com.pambrose.jev4k.examples

import com.pambrose.jev4k.ChoiceAnswer
import com.pambrose.jev4k.JevClient
import com.pambrose.jev4k.JevOption
import com.pambrose.jev4k.JevQuery
import com.pambrose.jev4k.JevResult
import com.pambrose.jev4k.NoulAnswer
import com.pambrose.jev4k.NoulBand
import com.pambrose.jev4k.QuestionRef
import com.pambrose.jev4k.ScoreAnswer
import com.pambrose.jev4k.rubric

/*
 * Both DSL styles against the live API. Run with `make example` (needs TYPESAFE_API_KEY).
 */

/** Enum options: the constant name is the option key; the description is sent with it. */
enum class Team(
    override val description: String,
) : JevOption {
    BILLING("Payments, invoicing, refunds"),
    TECHNICAL("Bugs, outages, integrations"),
    SALES("Pricing, upgrades, new accounts"),
}

/** A reusable query: property names become question ids, and handles read typed answers. */
object SupportTriage : JevQuery() {
    val urgent: QuestionRef<NoulAnswer> by noul("Does this message convey urgency or time-sensitivity?") {
        whenTrue("Explicitly time-sensitive, or the customer is blocked right now")
        whenFalse("No urgency expressed")
    }

    val team: QuestionRef<ChoiceAnswer<Team>> by choice<Team>("Which team should handle this message?")

    val frustration: QuestionRef<ScoreAnswer> by score("How frustrated does the customer appear?") {
        level("Calm, just stating facts")
        level("Frustrated but civil")
        level("Very angry, strong language or threatening to leave")
    }
}

fun main() {
    val ticket =
        "I was charged twice for order A-104, and I still can't log in after the update. Please fix this today."

    JevClient().use { jev ->
        // Typed style: one request answers every question in the query.
        val triage: JevResult = jev.blocking.ask(SupportTriage, state = ticket)
        val team = triage[SupportTriage.team]
        val route = when (team.choice) {
            Team.BILLING -> "billing queue"
            Team.TECHNICAL -> "engineering on-call"
            Team.SALES -> "sales desk"
        }

        println("Route to $route (confidence ${"%.2f".format(team.confidence)}; ${team.ranked()})")
        println("Urgency: ${triage[SupportTriage.urgent].band()}")
        println("Frustration: ${"%.2f".format(triage[SupportTriage.frustration].normalized)} of 1.0")

        // Inline style: ad-hoc questions by string id, plus the typed query in the same request.
        val detail = jev.blocking.query(state = ticket) {
            noul("refund", "Does the customer explicitly ask for a refund or credit?")
            choice("topic", "Which billing topic is the customer asking about?") {
                "duplicate_charge" means
                    rubric("Charged more than once for one order", notFor = "A single disputed charge")
                "refund_status" means "Progress of a refund already requested"
                "other" means "Any other billing topic"
            }
            include(SupportTriage)
        }
        if (detail.noul("refund").band() == NoulBand.YES) println("Refund requested: ${detail.choice("topic").choice}")
        println("Model ${detail.model}, ${detail.usage.inputTokens} input tokens, request ${detail.requestId}")
    }
}
