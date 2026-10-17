package com.pambrose.jev4k

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Java sees a Kotlin `Map`, `Set` or `List` as the mutable `java.util` type, so a built object that only copied its
 * collections with toMap(), toSet() or toList() could be changed from Java. Each cast below is what a Java caller gets
 * without one.
 */
@Suppress("UNCHECKED_CAST")
class ReadOnlyJvmTest : StringSpec() {
    init {
        "a built call's headers and extra body fields can't be changed, at any size" {
            for (count in 1..2) {
                val options = JevCallOptions {
                    repeat(count) { headers["X-$it"] = "v" }
                    extraBody["note"] = JsonPrimitive("hi")
                }
                shouldThrow<UnsupportedOperationException> {
                    (options.headers as MutableMap<String, String>)["X-Bad"] = "line1\nline2"
                }
                shouldThrow<UnsupportedOperationException> {
                    (options.extraBody as MutableMap<String, Any>).clear()
                }
                options.headers.size shouldBe count
            }
        }

        "a built client's headers and retry statuses can't be changed, nor the default statuses" {
            val config = JevConfigBuilder().apply {
                testDefaults()
                headers["X-A"] = "a"
                headers["X-B"] = "b"
                retry = RetryPolicy()
            }.build()
            shouldThrow<UnsupportedOperationException> { (config.headers as MutableMap<String, String>).remove("X-A") }
            shouldThrow<UnsupportedOperationException> { (config.retry.retryStatuses as MutableSet<Int>).add(400) }
            shouldThrow<UnsupportedOperationException> { (RetryPolicy.NONE.retryStatuses as MutableSet<Int>).add(400) }
            RetryPolicy().retryStatuses shouldBe setOf(408, 429) + (500..599)
        }

        "a question set's ids, iterator, images and criteria can't be changed" {
            val set = questions {
                choice("pick", "Which?") { options("a", "b") }
                score("rate", "How much?") { levels("Low", "High") }
            }
            shouldThrow<UnsupportedOperationException> { (set.ids as MutableList<String>).add("c") }
            shouldThrow<UnsupportedOperationException> {
                val each = set.iterator() as MutableIterator<QuestionRef<*>>
                each.next()
                each.remove()
            }
            shouldThrow<UnsupportedOperationException> { (set.images as MutableList<JevImage>).clear() }
            val choice = set["pick"]!!.question as ChoiceQuestion
            shouldThrow<UnsupportedOperationException> {
                (choice.options as MutableMap<String, Any>).remove("a")
            }
            shouldThrow<UnsupportedOperationException> {
                (choice.options.entries.first() as MutableMap.MutableEntry<String, Any>).setValue(JsonPrimitive("x"))
            }
            val score = set["rate"]!!.question as ScoreQuestion
            shouldThrow<UnsupportedOperationException> { (score.levels as MutableList<Any>).add(JsonPrimitive("x")) }
            set.size shouldBe 2
        }

        "a question passed to question(id, q) is copied into one that can't be changed either" {
            lateinit var ref: QuestionRef<Answer>
            // Two options: toMap() of a single entry is already immutable, so one wouldn't show the difference.
            val options = mutableMapOf<String, JsonElement>("a" to JsonPrimitive("A"), "b" to JsonPrimitive("B"))
            val original = ChoiceQuestion(JsonPrimitive("Which?"), options)
            questions { ref = question("pick", original) }
            shouldThrow<UnsupportedOperationException> {
                ((ref.question as ChoiceQuestion).options as MutableMap<String, Any>).clear()
            }
        }

        "a result's answers can't be changed" {
            val body = """{"answers":{"urgent":{"noul":0.9},"department":{"choice":"billing","confidence":0.8}}}"""
            val result = jevResult(body, Triage.questions)
            shouldThrow<UnsupportedOperationException> {
                (result.answers as MutableMap<String, Answer>).remove("urgent")
            }
            result.answers.size shouldBe 2
        }
    }
}
