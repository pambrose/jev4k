package com.pambrose.jev4k

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

private enum class Plan : JevOption {
    FREE {
        override val optionKey = "free"
        override val description = "No paid plan"
    },
    PRO {
        override val optionKey = "pro"
        override val entry = rubric("A paid individual plan", examples = listOf("Pro monthly"))
    },
    TEAM,
}

private enum class Color { RED, GREEN }

private open class BaseQuery : JevQuery() {
    val first by noul("Is this the first question?")
}

private class ChildQuery : BaseQuery() {
    val second by noul("Is this the second question?", id = "second_q")
}

class DslTest : StringSpec() {
    init {
        "the inline builder records questions in declaration order" {
            val set = questions {
                noul("urgent", "Does this convey urgency?")
                choice("department", "Which team should handle this?") {
                    "billing" means "Payments, invoicing, refunds"
                    option("other")
                }
                score("frustration", "How frustrated is the customer?") {
                    level("Calm")
                    levels("Frustrated", "Very angry")
                }
            }
            set.ids shouldBe listOf("urgent", "department", "frustration")
            set["department"].shouldNotBeNull().question shouldBe ChoiceQuestion(
                instructions = JsonPrimitive("Which team should handle this?"),
                options = mapOf("billing" to JsonPrimitive("Payments, invoicing, refunds"), "other" to JsonNull),
            )
            set["frustration"].shouldNotBeNull().question.shouldBeInstanceOf<ScoreQuestion>().levels shouldBe
                listOf(JsonPrimitive("Calm"), JsonPrimitive("Frustrated"), JsonPrimitive("Very angry"))
        }

        "noul criteria are captured from whenTrue and whenFalse" {
            val set = questions {
                noul("urgent", "Does this convey urgency?") {
                    whenTrue("Explicitly time-sensitive")
                    whenFalse(rubric("No urgency expressed"))
                }
            }
            set["urgent"].shouldNotBeNull().question shouldBe NoulQuestion(
                instructions = JsonPrimitive("Does this convey urgency?"),
                whenTrue = JsonPrimitive("Explicitly time-sensitive"),
                whenFalse = rubric("No urgency expressed"),
            )
        }

        "builder functions return handles that belong to the built set" {
            lateinit var handle: QuestionRef<NoulAnswer>
            val set = questions { handle = noul("urgent", "Does this convey urgency?") }
            handle.id shouldBe "urgent"
            set["urgent"] shouldBeSameInstanceAs handle
        }

        "structured instructions are accepted" {
            val instructions = entry("question" to "Which team?", "focus" to "The primary request")
            val set = questions { choice("team", instructions) { options("billing", "orders") } }
            set["team"].shouldNotBeNull().question.instructions shouldBe instructions
        }

        "a JevQuery takes question ids from property names in declaration order" {
            Triage.questions.ids shouldBe listOf("urgent", "department", "frustration")
            Triage.urgent.id shouldBe "urgent"
            Triage.questions["department"] shouldBeSameInstanceAs Triage.department
        }

        "an explicit id overrides the property name, and base-class questions come first" {
            val query = ChildQuery()
            query.questions.ids shouldBe listOf("first", "second_q")
            query.second.id shouldBe "second_q"
        }

        "enum choices use constant names, optionKey overrides, and structured entries" {
            val set = questions {
                choice<Plan>("plan", "Which plan is the customer on?")
                choice<Color>("color", "Which color is mentioned?")
            }
            set["plan"].shouldNotBeNull().question.shouldBeInstanceOf<ChoiceQuestion>().options shouldBe mapOf(
                "free" to JsonPrimitive("No paid plan"),
                "pro" to rubric("A paid individual plan", examples = listOf("Pro monthly")),
                "TEAM" to JsonNull,
            )
            set["color"].shouldNotBeNull().question.shouldBeInstanceOf<ChoiceQuestion>().options shouldBe
                mapOf("RED" to JsonNull, "GREEN" to JsonNull)
        }

        "a JevOption enum's descriptions become option descriptions" {
            (Triage.department.question as ChoiceQuestion).options shouldBe mapOf(
                "billing" to JsonPrimitive("Payments, invoicing, refunds"),
                "technical" to JsonPrimitive("Bugs, outages, integrations"),
                "sales" to JsonPrimitive("Pricing, upgrades, new accounts"),
            )
        }

        "include adds a JevQuery's own handles to an inline request" {
            val set = questions {
                noul("refund", "Does the customer explicitly request a refund?")
                include(Triage)
            }
            set.ids shouldBe listOf("refund", "urgent", "department", "frustration")
            set["department"] shouldBeSameInstanceAs Triage.department
        }

        // Generics are erased, so shouldBeInstanceOf could not tell these apart at runtime. The assignments are
        // the real assertion: they stop compiling if a handle's answer type changes.
        "question handles are typed by answer" {
            val department: QuestionRef<ChoiceAnswer<Dept>> = Triage.department
            val urgent: QuestionRef<NoulAnswer> = Triage.urgent
            val frustration: QuestionRef<ScoreAnswer> = Triage.frustration
            department.id shouldBe "department"
            urgent.question.shouldBeInstanceOf<NoulQuestion>()
            frustration.question.shouldBeInstanceOf<ScoreQuestion>()
        }

        "a hand-built Question can be added, and its answer comes back untyped" {
            val levels = listOf(JsonPrimitive("small"), JsonPrimitive("large"))
            lateinit var handle: QuestionRef<Answer>
            val set = questions {
                handle = question("scope", ScoreQuestion(JsonPrimitive("How big?"), levels))
            }
            set.ids shouldBe listOf("scope")
            val r = jevResult("""{"answers":{"scope":{"type":"score","score":1.0,"confidence":0.5}}}""", set)
            r[handle].shouldBeInstanceOf<ScoreAnswer>().score shouldBe 1.0
        }

        "a hand-built Question is validated like any other" {
            shouldThrow<JevValidationException> {
                questions { question("scope", ScoreQuestion(JsonPrimitive("How big?"), listOf(JsonPrimitive("only")))) }
            }.problems.single() shouldContain "2..10 levels (got 1)"
        }
    }
}
