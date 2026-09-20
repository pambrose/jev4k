package com.pambrose.jev4k

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.JsonNull

private class DuplicateIdQuery : JevQuery() {
    val a by noul("First question?", id = "same")
    val b by noul("Second question?", id = "same")
}

private enum class Clash : JevOption {
    A,
    B,
    ;

    override val optionKey: String get() = "same"
}

private object DuplicateOptionQuery : JevQuery() {
    val pick by choice("Which?") {
        option("a")
        option("a")
    }
}

private object ClashingEnumQuery : JevQuery() {
    val pick by choice<Clash>("Which?")
}

class ValidationTest : StringSpec() {
    private fun problemsOf(block: QueryBuilder.() -> Unit): List<String> =
        shouldThrow<JevValidationException> { questions(block) }.problems

    init {
        "a request needs at least one question" {
            problemsOf { } shouldBe listOf("A request needs at least one question")
        }

        "question ids must be unique" {
            problemsOf {
                noul("x", "First?")
                noul("x", "Second?")
            }.single() shouldContain "Duplicate question id 'x'"
        }

        "a JevQuery with duplicate ids fails when its questions are first used" {
            shouldThrow<JevValidationException> { DuplicateIdQuery().questions }
                .problems.single() shouldContain "Duplicate question id 'same'"
        }

        "question ids must not be blank" {
            problemsOf { noul(" ", "Is it?") }.single() shouldContain "Question ids must not be blank"
        }

        "instructions must not be blank or null, since ids are never sent to the model" {
            problemsOf { noul("a", "  ") }.single() shouldContain "ids are never sent to the model"
            problemsOf { noul("a", JsonNull) }.single() shouldContain "question 'a'"
        }

        "a Choice needs between 1 and 255 options" {
            problemsOf { choice("c", "Which?") { } }.single() shouldContain "1..255 options (got 0)"
            problemsOf { choice("c", "Which?") { (0..255).forEach { option("o$it") } } }
                .single() shouldContain "(got 256)"
            shouldNotThrowAny { questions { choice("c", "Which?") { (0 until 255).forEach { option("o$it") } } } }
        }

        "Choice option keys must not be blank" {
            problemsOf { choice("c", "Which?") { option("") } }.single() shouldContain "option keys must not be blank"
        }

        "a duplicate Choice option key is collected like any other problem" {
            problemsOf {
                choice("c", "Which?") {
                    option("a")
                    option("a")
                }
            }.single() shouldContain "duplicate Choice option 'a'"
        }

        // The reason it is collected rather than thrown where it is found: inside a JevQuery object, throwing
        // would escape the class initializer as an ExceptionInInitializerError, which no catch of JevException
        // can see, and every later access would then fail with NoClassDefFoundError.
        "a JevQuery with a duplicate option initializes cleanly and fails on first use" {
            shouldNotThrowAny { DuplicateOptionQuery.hashCode() }
            shouldThrow<JevValidationException> { DuplicateOptionQuery.questions }
                .problems.single() shouldContain "question 'pick': duplicate Choice option 'a'"
        }

        "enum constants sharing an option key are reported the same way" {
            shouldNotThrowAny { ClashingEnumQuery.hashCode() }
            shouldThrow<JevValidationException> { ClashingEnumQuery.questions }
                .problems.single() shouldContain "duplicate Choice option 'same' in enum Clash"
        }

        "a Score needs between 2 and 10 levels" {
            problemsOf { score("s", "How much?") { level("only") } }.single() shouldContain "2..10 levels (got 1)"
            problemsOf { score("s", "How much?") { (1..11).forEach { level("l$it") } } }
                .single() shouldContain "(got 11)"
            shouldNotThrowAny {
                questions {
                    score("two", "How much?") { levels("low", "high") }
                    score("ten", "How much?") { (1..10).forEach { level("l$it") } }
                }
            }
        }

        "every problem is reported at once" {
            val problems = problemsOf {
                noul("a", "")
                score("s", "How much?") { level("only") }
            }
            problems shouldHaveSize 2
        }
    }
}
