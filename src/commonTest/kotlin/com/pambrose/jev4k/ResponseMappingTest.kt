package com.pambrose.jev4k

import com.pambrose.jev4k.internal.mapSystemOne
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

class ResponseMappingTest : StringSpec() {
    private val documented = questions {
        noul("is_urgent", "Does this convey urgency?")
        choice("department", "Which team should handle this?") {
            options("billing", "technical", "sales")
        }
        score("frustration", "How frustrated is the customer?") {
            levels("Calm", "Frustrated", "Very angry")
        }
    }

    private val documentedResponse = """
        {
          "model": "jev-1.13.0",
          "answers": {
            "is_urgent": { "type": "noul", "noul": 0.92 },
            "department": {
              "type": "choice",
              "choice": "technical",
              "probabilities": { "sales": 0.07, "technical": 0.85, "billing": 0.08 },
              "confidence": 0.82
            },
            "frustration": {
              "type": "score",
              "score": 1.6,
              "legend": { "2": "Very angry", "0": "Calm", "1": "Frustrated" },
              "probabilities": { "2": 0.65, "0": 0.05, "1": 0.3 },
              "confidence": 0.78
            }
          },
          "usage": { "input_tokens": 312, "output_tokens": 48 }
        }
    """

    private fun map(
        body: String,
        set: QuestionSet = documented,
        requestedModel: String = "jev-latest",
    ): JevResult = mapSystemOne(json(body).jsonObject, requestedModel, set, requestId = "req-1", endpoint = "POST test")

    private fun single(
        answer: String,
        set: QuestionSet = documented,
    ) = map("""{"model":"m","answers":$answer,"usage":{"input_tokens":1,"output_tokens":1}}""", set)

    init {
        "the documented response maps to typed answers" {
            val r = map(documentedResponse)
            r.noul("is_urgent") shouldBe NoulAnswer(0.92)
            r.choice("department").choice shouldBe "technical"
            r.choice("department").confidence shouldBe 0.82
            r.score("frustration").score shouldBe 1.6
            r.usage shouldBe Usage(312, 48)
            r.requestId shouldBe "req-1"
        }

        "choice probabilities come back in the order the options were declared" {
            map(documentedResponse).choice("department").probabilities.keys.toList() shouldBe
                listOf("billing", "technical", "sales")
        }

        "score probabilities and legend are keyed by level number, in order" {
            val score = map(documentedResponse).score("frustration")
            score.probabilities shouldBe mapOf(0 to 0.05, 1 to 0.3, 2 to 0.65)
            score.probabilities.keys.toList() shouldBe listOf(0, 1, 2)
            score.legendText(2) shouldBe "Very angry"
            score.levelCount shouldBe 3
        }

        "score helpers normalize and pick levels" {
            val score = map(documentedResponse).score("frustration")
            score.normalized shouldBe (0.8 plusOrMinus 1e-9)
            score.nearestLevel shouldBe 2
            score.mostLikelyLevel shouldBe 2
        }

        "noul and choice helpers" {
            val r = map(documentedResponse)
            r.noul("is_urgent").isTrue() shouldBe true
            r.noul("is_urgent").band() shouldBe NoulBand.YES
            NoulAnswer(0.5).band() shouldBe NoulBand.UNCERTAIN
            NoulAnswer(0.1).band() shouldBe NoulBand.NO
            r.choice("department").topProbability shouldBe 0.85
            r.choice("department").ranked().first() shouldBe ("technical" to 0.85)
            r.choice("department").probability("missing") shouldBe 0.0
        }

        // Both helpers compare strictly, so a probability sitting exactly on a boundary is the interesting case.
        "isTrue and band are exclusive at their thresholds" {
            NoulAnswer(0.5).isTrue() shouldBe false
            NoulAnswer(0.50001).isTrue() shouldBe true
            NoulAnswer(0.9).isTrue(threshold = 0.9) shouldBe false
            NoulAnswer(0.30).band() shouldBe NoulBand.UNCERTAIN
            NoulAnswer(0.70).band() shouldBe NoulBand.UNCERTAIN
            NoulAnswer(0.5).band(no = 0.6, yes = 0.8) shouldBe NoulBand.NO
            NoulAnswer(0.9).band(no = 0.6, yes = 0.8) shouldBe NoulBand.YES
        }

        "an empty choice answer reports a zero top probability" {
            ChoiceAnswer("only", emptyMap(), 0.0).topProbability shouldBe 0.0
        }

        "score helpers cope with a degenerate or probability-free answer" {
            val noProbabilities = ScoreAnswer(1.6, emptyMap(), emptyMap(), 0.5, levelCount = 3)
            noProbabilities.mostLikelyLevel shouldBe 2
            // A single level has no range to normalize over, and the level number can't exceed the top one.
            val oneLevel = ScoreAnswer(4.0, emptyMap(), emptyMap(), 0.5, levelCount = 1)
            oneLevel.normalized shouldBe 0.0
            oneLevel.nearestLevel shouldBe 0
            ScoreAnswer(0.0, emptyMap(), emptyMap(), 0.5, levelCount = 0).nearestLevel shouldBe 0
        }

        "an answer for a question with no levels declared counts the levels the server returned" {
            val r = single(
                """{"surprise":{"type":"score","score":1.0,"confidence":0.5,"probabilities":{"0":0.2,"1":0.8}}}""",
            )
            r.answers["surprise"].shouldBeInstanceOf<ScoreAnswer>().levelCount shouldBe 2
        }

        "a structured legend entry is kept as JSON" {
            val set = questions {
                score("scope", "How focused is this PR?") {
                    level(entry("summary" to "One change"))
                    level("Several changes")
                }
            }
            val r = single(
                """{"scope":{"type":"score","score":0.0,"confidence":1.0,
                   "legend":{"0":{"summary":"One change"},"1":"Several changes"},"probabilities":{"0":1.0,"1":0.0}}}""",
                set,
            )
            r.score("scope").legend[0] shouldBe entry("summary" to "One change")
            r.score("scope").legendText(0) shouldBe null
        }

        "missing probabilities become empty, and a missing legend falls back to the levels sent" {
            val r = single("""{"frustration":{"type":"score","score":1.035,"confidence":0.842}}""")
            r.score("frustration").probabilities.shouldBeEmpty()
            r.score("frustration").legend shouldBe
                mapOf(0 to JsonPrimitive("Calm"), 1 to JsonPrimitive("Frustrated"), 2 to JsonPrimitive("Very angry"))
        }

        "null or missing usage counts are accepted" {
            map("""{"model":"m","answers":{},"usage":{"input_tokens":null}}""").usage shouldBe Usage(null, null)
            map("""{"model":"m","answers":{}}""").usage shouldBe Usage(null, null)
        }

        // Metadata is optional, so a quirk in it must not cost the caller answers that parsed cleanly.
        "unusable usage and model metadata degrade instead of failing" {
            val bodies = listOf(
                """{"model":42,"answers":{},"usage":{"input_tokens":"12","output_tokens":1.5}}""",
                """{"model":null,"answers":{},"usage":[]}""",
                """{"answers":{},"usage":{"input_tokens":{"n":1}}}""",
            )
            for (body in bodies) {
                withClue(body) {
                    val r = map(body)
                    r.usage shouldBe Usage(null, null)
                    r.model shouldBe "jev-latest"
                }
            }
        }

        // answers, unlike usage, is the payload: absent means "nothing came back" and is reported on read,
        // but the wrong shape is a broken response and fails straight away.
        "a null or absent answers field leaves every answer to fail on read, but a non-object fails now" {
            for (body in listOf("""{"answers":null}""", "{}")) {
                withClue(body) { map(body).answers.shouldBeEmpty() }
            }
            shouldThrow<JevResponseValidationException> { map("""{"answers":[]}""") }.fieldPath shouldBe "answers"
        }

        "an answer without a type is read as the type of question asked" {
            single("""{"is_urgent":{"noul":0.4}}""").noul("is_urgent") shouldBe NoulAnswer(0.4)
        }

        "an unknown answer type is kept as UnknownAnswer" {
            val r = single("""{"is_urgent":{"type":"ranking","order":[1,2]}}""")
            r.answers["is_urgent"].shouldBeInstanceOf<UnknownAnswer>().type shouldBe "ranking"
        }

        "a known answer missing a required field fails with its field path" {
            val e = shouldThrow<JevResponseValidationException> { single("""{"is_urgent":{"type":"noul"}}""") }
            e.fieldPath shouldBe "answers.is_urgent.noul"
            e.requestId shouldBe "req-1"
        }

        "a non-numeric probability is rejected" {
            shouldThrow<JevResponseValidationException> {
                single(
                    """{"department":{"type":"choice","choice":"sales","confidence":1,
                       "probabilities":{"sales":"high"}}}""",
                )
            }.fieldPath shouldBe "answers.department.probabilities.sales"
        }

        // One case per branch that can reject a malformed answer, each pinned by the field path it reports,
        // because the path is what tells a caller which answer to go and look at.
        "every malformed answer is rejected with the path of the field that broke" {
            val cases = listOf(
                """{"is_urgent":0.9}""" to "answers.is_urgent",
                """{"is_urgent":{"type":5,"noul":0.9}}""" to "answers.is_urgent.type",
                """{"is_urgent":{"type":"noul","noul":null}}""" to "answers.is_urgent.noul",
                // A numeric string is the only input that reaches the isString guard; doubleOrNull would
                // otherwise reject it anyway, which is why "high" above doesn't exercise it.
                """{"is_urgent":{"type":"noul","noul":"0.9"}}""" to "answers.is_urgent.noul",
                """{"department":{"type":"choice","confidence":1}}""" to "answers.department.choice",
                """{"department":{"type":"choice","choice":"sales"}}""" to "answers.department.confidence",
                """{"frustration":{"type":"score","score":1}}""" to "answers.frustration.confidence",
                """{"frustration":{"type":"score","score":1,"confidence":1,"probabilities":{"low":1.0}}}"""
                    to "answers.frustration.probabilities.low",
                """{"frustration":{"type":"score","score":1,"confidence":1,"legend":{"x":"Calm"}}}"""
                    to "answers.frustration.legend.x",
                """{"frustration":{"type":"score","score":1,"confidence":1,"legend":[]}}"""
                    to "answers.frustration.legend",
            )
            for ((body, path) in cases) {
                withClue(body) {
                    shouldThrow<JevResponseValidationException> { single(body) }.fieldPath shouldBe path
                }
            }
        }

        "a missing type is read as the question's type for choices and scores too" {
            single("""{"department":{"choice":"sales","confidence":0.4,"probabilities":{"sales":1.0}}}""")
                .choice("department").choice shouldBe "sales"
            single("""{"frustration":{"score":1.0,"confidence":0.4}}""").score("frustration").score shouldBe 1.0
        }

        "an answer the request never asked for is kept, after the declared ones" {
            val r = single(
                """{"surprise":{"type":"noul","noul":0.1},"is_urgent":{"type":"noul","noul":0.9}}""",
            )
            r.answers.keys.toList() shouldBe listOf("is_urgent", "surprise")
            r.answers["surprise"] shouldBe NoulAnswer(0.1)
        }

        "an extra answer with no type at all becomes an UnknownAnswer" {
            val r = single("""{"surprise":{"verdict":"maybe"}}""")
            val unknown = r.answers["surprise"].shouldBeInstanceOf<UnknownAnswer>()
            unknown.type shouldBe null
            unknown.raw shouldBe json("""{"verdict":"maybe"}""")
        }

        "choice probabilities keep declared keys first, then whatever else the server sent" {
            val r = single(
                """{"department":{"type":"choice","choice":"technical","confidence":0.5,
                   "probabilities":{"legal":0.1,"technical":0.9}}}""",
            )
            // "billing" and "sales" were declared but not returned, so they are simply absent.
            r.choice("department").probabilities.keys.toList() shouldBe listOf("technical", "legal")
        }

        "a missing answer fails only when it is read" {
            val r = map("""{"model":"m","answers":{}}""")
            val e = shouldThrow<JevResponseValidationException> { r.noul("is_urgent") }
            e.fieldPath shouldBe "answers.is_urgent"
        }

        "an answer of a different type than asked is a response error" {
            val r = single("""{"department":{"type":"noul","noul":0.3}}""")
            shouldThrow<JevResponseValidationException> { r.choice("department") }.message shouldContain
                "expected a choice answer but got noul"
        }

        "the reported model is exposed next to the requested one" {
            val r = map(documentedResponse)
            r.model shouldBe "jev-1.13.0"
            r.requestedModel shouldBe "jev-latest"
            map("""{"answers":{}}""").model shouldBe "jev-latest"
        }

        "typed handles read enum choices" {
            val r = single(
                """{"department":{"type":"choice","choice":"technical","confidence":0.82,
                   "probabilities":{"sales":0.07,"technical":0.85,"billing":0.08}}}""",
                Triage.questions,
            )
            val department: ChoiceAnswer<Dept> = r[Triage.department]
            val team = when (department.choice) {
                Dept.BILLING -> "billing team"
                Dept.TECHNICAL -> "engineering"
                Dept.SALES -> "sales team"
            }
            team shouldBe "engineering"
            department.probabilities.keys.toList() shouldBe listOf(Dept.BILLING, Dept.TECHNICAL, Dept.SALES)
            r.enumChoice<Dept>("department").choice shouldBe Dept.TECHNICAL
        }

        // Dept overrides every optionKey, so it never exercises the plain-name path that most enums take.
        "an enum without optionKey overrides decodes by constant name" {
            val set = questions { choice<NoulBand>("band", "Which band?") }
            val r = single(
                """{"band":{"type":"choice","choice":"YES","confidence":0.7,
                   "probabilities":{"NO":0.1,"UNCERTAIN":0.2,"YES":0.7}}}""",
                set,
            )
            r.enumChoice<NoulBand>("band").choice shouldBe NoulBand.YES
            r.enumChoice<NoulBand>("band").probabilities shouldBe
                mapOf(NoulBand.NO to 0.1, NoulBand.UNCERTAIN to 0.2, NoulBand.YES to 0.7)
        }

        "probabilities for options the enum doesn't have are dropped" {
            val r = single(
                """{"department":{"type":"choice","choice":"technical","confidence":0.7,
                   "probabilities":{"technical":0.7,"legal":0.3}}}""",
                Triage.questions,
            )
            r[Triage.department].probabilities shouldBe mapOf(Dept.TECHNICAL to 0.7)
        }

        "an enum choice the enum doesn't define is a response error" {
            val r = single("""{"department":{"type":"choice","choice":"legal","confidence":1.0}}""", Triage.questions)
            shouldThrow<JevResponseValidationException> { r[Triage.department] }.message shouldContain
                "unknown option 'legal'"
        }

        "accessors reject ids and handles that aren't part of the request" {
            val r = map(documentedResponse)
            shouldThrow<IllegalArgumentException> { r.noul("nope") }
            shouldThrow<IllegalArgumentException> { r.noul("department") }.message shouldContain "is a choice"
            shouldThrow<IllegalArgumentException> { r[Triage.urgent] }
        }

        // Handles are matched by identity, not by id: a handle from another request would otherwise decode
        // this one's answer through the wrong decoder and quietly return the wrong type.
        "a foreign handle is rejected even when its id matches" {
            lateinit var foreign: QuestionRef<ChoiceAnswer<Dept>>
            questions { foreign = choice<Dept>("department", "Which team should handle this?") }

            val r = map(documentedResponse)
            foreign.id shouldBe "department"
            shouldThrow<IllegalArgumentException> { r[foreign] }.message shouldContain "not part of this request"

            // The handle from the set that was actually asked reads fine.
            val own = documented["department"] as QuestionRef<*>
            r[own].shouldBeInstanceOf<ChoiceAnswer<*>>().choice shouldBe "technical"
        }

        "answers can be listed by type" {
            val r = map(documentedResponse)
            r.nouls.keys shouldBe setOf("is_urgent")
            r.choices.keys shouldBe setOf("department")
            r.scores.keys shouldBe setOf("frustration")
            r.raw shouldBe json(documentedResponse).jsonObject
        }
    }
}
