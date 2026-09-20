package com.pambrose.jev4k

import com.pambrose.jev4k.internal.JevJson
import com.pambrose.jev4k.internal.SystemOneRequest
import com.pambrose.jev4k.internal.toWire
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

class WireSerializationTest : StringSpec() {
    private fun requestJson(
        state: JsonElement,
        questions: QuestionSet,
        model: String = "jev-latest",
    ): JsonObject =
        JevJson.encodeToJsonElement(SystemOneRequest.serializer(), SystemOneRequest(state, model, questions.toWire()))
            .jsonObject

    init {
        "the documented three-question request is reproduced exactly" {
            val set = questions {
                noul("is_urgent", "Does this convey urgency?") {
                    whenTrue("Explicitly time-sensitive")
                    whenFalse("No urgency expressed")
                }
                choice("department", "Which team should handle this?") {
                    "billing" means "Payments, invoicing, refunds"
                    "technical" means "Bugs, outages, integrations"
                    "sales" means "Pricing, upgrades, new accounts"
                }
                score("frustration", "How frustrated is the customer?") {
                    levels("Calm", "Frustrated", "Very angry")
                }
            }
            requestJson(JsonPrimitive("Help! My payouts have been failing for 3 days."), set) shouldBe json(
                """
                {
                  "state": "Help! My payouts have been failing for 3 days.",
                  "model": "jev-latest",
                  "questions": {
                    "is_urgent": {
                      "type": "noul",
                      "instructions": "Does this convey urgency?",
                      "criteria": { "true": "Explicitly time-sensitive", "false": "No urgency expressed" }
                    },
                    "department": {
                      "type": "choice",
                      "instructions": "Which team should handle this?",
                      "criteria": {
                        "billing": "Payments, invoicing, refunds",
                        "technical": "Bugs, outages, integrations",
                        "sales": "Pricing, upgrades, new accounts"
                      }
                    },
                    "frustration": {
                      "type": "score",
                      "instructions": "How frustrated is the customer?",
                      "criteria": ["Calm", "Frustrated", "Very angry"]
                    }
                  }
                }
                """,
            )
        }

        "an undescribed Choice option is sent as an explicit null" {
            questions { choice("tone", "What is the customer's tone?") { options("calm", "angry") } }.toJson() shouldBe
                json(
                    """{"tone":{"type":"choice","instructions":"What is the customer's tone?",
                       "criteria":{"calm":null,"angry":null}}}""",
                )
        }

        "Noul criteria are omitted when unset, and only the given side is sent" {
            questions {
                noul("plain", "Is it plain?")
                noul("half", "Is it half?") { whenTrue("Yes, half") }
            }.toJson() shouldBe json(
                """{"plain":{"type":"noul","instructions":"Is it plain?"},
                    "half":{"type":"noul","instructions":"Is it half?","criteria":{"true":"Yes, half"}}}""",
            )
        }

        "structured instructions, options, and levels serialize as JSON" {
            questions {
                choice("department", entry("question" to "Which team?", "focus" to "The primary request")) {
                    "billing" means rubric("Charges", notFor = "Tracking", examples = listOf("I was charged twice"))
                }
                score("pr_scope", "How focused is this PR?") {
                    level(entry("summary" to "One change", "signals" to listOf("A single fix")))
                    level("Several changes")
                }
            }.toJson() shouldBe json(
                """
                {
                  "department": {
                    "type": "choice",
                    "instructions": { "question": "Which team?", "focus": "The primary request" },
                    "criteria": {
                      "billing": { "what": "Charges", "not_for": "Tracking", "examples": ["I was charged twice"] }
                    }
                  },
                  "pr_scope": {
                    "type": "score",
                    "instructions": "How focused is this PR?",
                    "criteria": [ { "summary": "One change", "signals": ["A single fix"] }, "Several changes" ]
                  }
                }
                """,
            )
        }

        "questions keep their declaration order on the wire" {
            val ids = listOf("zeta", "alpha", "mid")
            questions { ids.forEach { noul(it, "Is $it true?") } }.toJson().keys.toList() shouldBe ids
        }

        "the type discriminator is written first" {
            questions { noul("a", "Is it?") }.toJson().getValue("a").jsonObject.keys.first() shouldBe "type"
        }

        "a JevQuery serializes like the equivalent inline questions" {
            Triage.questions.toJson()["department"] shouldBe json(
                """{"type":"choice","instructions":"Which team should handle this?",
                    "criteria":{"billing":"Payments, invoicing, refunds","technical":"Bugs, outages, integrations",
                    "sales":"Pricing, upgrades, new accounts"}}""",
            )
        }
    }
}
