package com.pambrose.jev4k

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

class EntriesTest : StringSpec() {
    init {
        "entry builds a JSON object from nested Kotlin values, keeping key order" {
            val e = entry(
                "question" to "Does `extracted_value` match?",
                "field" to mapOf("name" to "amount_due", "unit" to "USD"),
                "compare" to listOf("a", "b"),
                "limit" to 3,
                "strict" to true,
                "note" to null,
            )
            e shouldBe json(
                """{"question":"Does `extracted_value` match?","field":{"name":"amount_due","unit":"USD"},
                   "compare":["a","b"],"limit":3,"strict":true,"note":null}""",
            )
            e.keys.toList() shouldBe listOf("question", "field", "compare", "limit", "strict", "note")
        }

        "rubric produces what / not_for / examples and omits what isn't given" {
            rubric("Charges and refunds", notFor = "Order tracking", examples = listOf("I was charged twice")) shouldBe
                json("""{"what":"Charges and refunds","not_for":"Order tracking","examples":["I was charged twice"]}""")
            rubric("Charges and refunds") shouldBe json("""{"what":"Charges and refunds"}""")
        }

        "jsonOf passes JSON elements through and converts arrays" {
            jsonOf(JsonPrimitive("x")) shouldBe JsonPrimitive("x")
            jsonOf(null) shouldBe JsonNull
            jsonOf(arrayOf<Any>(1, "two")) shouldBe json("""[1,"two"]""")
        }

        "jsonOf sends an enum as its constant name" {
            jsonOf(NoulBand.UNCERTAIN) shouldBe JsonPrimitive("UNCERTAIN")
            jsonOf(listOf(NoulBand.NO, NoulBand.YES)) shouldBe json("""["NO","YES"]""")
        }

        "jsonOf rejects values it can't represent" {
            val e = shouldThrow<JevValidationException> { jsonOf(Any()) }
            // Kotlin's view of the type, not the JVM's: `Any`, not `Object`.
            e.message shouldContain "Any"
        }

        "jsonOf rejects a map keyed by anything but a String" {
            shouldThrow<JevValidationException> { jsonOf(mapOf(1 to "one")) }.message shouldContain "map key"
        }

        // JSON has no NaN or Infinity. Caught here, it's a JevValidationException naming the value; missed, it
        // escapes as a kotlinx encoding failure from inside the HTTP call.
        "jsonOf and jsonEntry reject non-finite numbers" {
            for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
                shouldThrow<JevValidationException> { jsonOf(bad) }.message shouldContain "no representation"
            }
            shouldThrow<JevValidationException> { jsonOf(Float.NaN) }.message shouldContain "no representation"
            shouldThrow<JevValidationException> { jsonEntry(Reading(Double.NaN)) }.message shouldContain "Reading"
        }

        "jsonEntry encodes a @Serializable value" {
            jsonEntry(Ticket("Duplicate charge", "I was charged twice")) shouldBe
                json("""{"subject":"Duplicate charge","message":"I was charged twice"}""")
        }

        "jsonEntry keeps fields that equal their defaults, so the model sees them" {
            jsonEntry(Order("A-104")) shouldBe json("""{"id":"A-104","status":"open","items":[]}""")
        }

        "jsonEntry rejects a value that isn't @Serializable" {
            val e = shouldThrow<JevValidationException> { jsonEntry(StringBuilder("x")) }
            e.message shouldContain "@Serializable"
        }
    }
}
