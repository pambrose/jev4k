package com.pambrose.jev4k

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.JsonPrimitive

private object Finder : JevQuery() {
    val mug by box("the coffee mug in `photo`")
}

private const val MUG_ANSWER =
    """{"type":"box","box":{"top":0.25,"bottom":0.82,"left":0.44,"right":0.84},"confidence":0.92}"""

class BoxTest : StringSpec() {
    private val finder = questions { box("mug", "the coffee mug in `photo`") }

    init {
        "box builds a Box question in both layers, sent with no criteria" {
            finder.toJson() shouldBe json("""{"mug":{"type":"box","instructions":"the coffee mug in `photo`"}}""")
            Finder.questions.toJson() shouldBe finder.toJson()
        }

        "a blank Box instruction fails validation like any other" {
            shouldThrow<JevValidationException> { questions { box("mug", " ") } }
                .problems.single() shouldContain "question 'mug': instructions must be non-blank"
        }

        "a Box answer maps to a BoxAnswer, read by handle, by id and in boxes" {
            val r = jevResult("""{"model":"jev-1.14.0","answers":{"mug":$MUG_ANSWER}}""", Finder.questions)
            val expected = BoxAnswer(BoundingBox(top = 0.25, bottom = 0.82, left = 0.44, right = 0.84), 0.92)
            r[Finder.mug] shouldBe expected
            r.box("mug") shouldBe expected
            r.boxes shouldBe mapOf("mug" to expected)
        }

        "an answer with no type is read as the Box that was asked, and confidence may be null or absent" {
            val box = """"box":{"top":0.1,"bottom":0.9,"left":0.2,"right":0.8}"""
            jevResult("""{"answers":{"mug":{$box}}}""", finder).box("mug").confidence shouldBe null
            jevResult("""{"answers":{"mug":{$box,"confidence":null}}}""", finder).box("mug").confidence shouldBe null
        }

        "a Box answer nobody asked for still maps to a BoxAnswer" {
            val r = jevResult("""{"answers":{"mug":$MUG_ANSWER,"extra":$MUG_ANSWER}}""", finder)
            r.answers["extra"].shouldBeInstanceOf<BoxAnswer>()
            r.boxes.keys shouldBe setOf("mug", "extra")
        }

        "a malformed Box answer fails with the field's path" {
            fun box(edges: String) = """{"type":"box","box":{$edges}}"""
            val cases =
                mapOf(
                    """{"type":"box"}""" to "answers.mug.box",
                    box(""""top":0.1,"bottom":0.9,"left":0.2""") to "answers.mug.box.right",
                    box(""""top":0.1,"bottom":0.9,"left":0.2,"right":1.5""") to "answers.mug.box.right",
                    box(""""top":0.1,"bottom":0.9,"left":0.2,"right":"0.8"""") to "answers.mug.box.right",
                    box(""""top":0.1,"bottom":0.9,"left":0.8,"right":0.2""") to "answers.mug.box.left",
                    box(""""top":0.9,"bottom":0.1,"left":0.2,"right":0.8""") to "answers.mug.box.top",
                    """{"type":"box","box":{"top":0.1,"bottom":0.9,"left":0.2,"right":0.8},"confidence":2}""" to
                        "answers.mug.confidence",
                )
            for ((answer, path) in cases) {
                withClue(answer) {
                    shouldThrow<JevResponseValidationException> {
                        jevResult("""{"answers":{"mug":$answer}}""", finder)
                    }.fieldPath shouldBe path
                }
            }
        }

        "question() takes a BoxQuestion, and its handle checks the answer's type" {
            lateinit var ref: QuestionRef<Answer>
            val set = questions { ref = question("mug", BoxQuestion(JsonPrimitive("the mug in `photo`"))) }
            val r = jevResult("""{"answers":{"mug":{"type":"noul","noul":0.5}}}""", set)
            shouldThrow<JevResponseValidationException> { r[ref] }.message shouldContain
                "expected a box answer but got noul"
        }

        "box() by id checks the question's type" {
            val r = jevResult("""{"answers":{}}""", Triage.questions)
            shouldThrow<IllegalArgumentException> { r.box("urgent") }.message shouldBe
                "Question 'urgent' is a noul, not a box"
        }
    }
}
