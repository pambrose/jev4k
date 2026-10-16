package com.pambrose.jev4k

import io.kotest.core.annotation.Condition
import io.kotest.core.annotation.EnabledIf
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.doubles.shouldBeBetween
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import kotlin.reflect.KClass
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The opt-in alone decides, not the key too: a live run without a key then fails, each test naming the missing
 * `TYPESAFE_API_KEY`, instead of passing without having made a single real call.
 */
class LiveApiCondition : Condition {
    override fun evaluate(kclass: KClass<out Spec>): Boolean = liveOptIn()
}

/**
 * Real calls to the TypeSafe API. Runs only with `JEV4K_LIVE=1` (`make live-tests` sets it), so ordinary test runs
 * never spend tokens, and needs `TYPESAFE_API_KEY`. Jev isn't fully deterministic, so these check shapes and ranges
 * rather than exact values.
 *
 * The gate is on the spec, not on each test: `.env` supplies a real key to every test task, so a test added
 * here without the per-test gate would otherwise spend tokens on every `make tests`.
 */
@EnabledIf(LiveApiCondition::class)
class LiveSmokeTest : StringSpec() {
    private val live = liveOptIn()

    /** Belt and braces: the spec-level [EnabledIf] already covers this, and forgetting it is now harmless. */
    private fun String.liveTest(test: suspend TestScope.() -> Unit) = config(enabledIf = { live }, test = test)

    init {
        "an inline query returns well-formed answers".liveTest {
            JevClient().use { jev ->
                val r = jev.query(state = "Help! My payouts have been failing for 3 days.") {
                    noul("is_urgent", "Does this message convey urgency?")
                    choice("department", "Which team should handle this?") {
                        "billing" means "Payments, invoicing, refunds"
                        "technical" means "Bugs, outages, integrations"
                        "sales" means "Pricing, upgrades, new accounts"
                    }
                    score("frustration", "How frustrated is the customer?") {
                        levels("Calm, just stating facts", "Frustrated but civil", "Very angry, strong language")
                    }
                }
                r.noul("is_urgent").noul.shouldBeBetween(0.0, 1.0, 0.0)
                listOf("billing", "technical", "sales") shouldContain r.choice("department").choice
                r.choice("department").confidence.shouldBeBetween(0.0, 1.0, 0.0)
                r.score("frustration").score.shouldBeBetween(0.0, 2.0, 0.0)
                r.score("frustration").levelCount shouldBe 3
                r.model.shouldNotBeBlank()
                r.usage.inputTokens.shouldNotBeNull()
            }
        }

        "a typed JevQuery returns an enum choice".liveTest {
            JevClient().use { jev ->
                val r = jev.ask(Triage, state = "I was charged twice for order A-104. Please refund the duplicate.")
                Dept.entries shouldContain r[Triage.department].choice
                r[Triage.urgent].noul.shouldBeBetween(0.0, 1.0, 0.0)
            }
        }

        "models lists jev-latest".liveTest {
            JevClient().use { jev -> jev.models().map { it.name } shouldContain "jev-latest" }
        }

        "an image and a Box question come back as typed answers".liveTest {
            val photo = JevImage.of(redSquarePng())
            JevClient().use { jev ->
                val r = jev.query(buildJsonObject { put("photo", photo.reference) }, model = "jev-preview") {
                    attach(photo)
                    noul("square", "Is there a red square in `photo`?")
                    box("where", "the red square in `photo`")
                }
                r.noul("square").noul shouldBeGreaterThan 0.5
                val box = r.box("where").box
                box.left shouldBeLessThan 0.7
                box.right shouldBeGreaterThan 0.7
                box.top shouldBeLessThan 1.0 / 3
                box.bottom shouldBeGreaterThan 1.0 / 3
            }
        }
    }

    /** A 400 × 300 white PNG with a red square centered at (280, 100): 0.7 of the width, a third of the height. */
    private fun redSquarePng(): ByteArray {
        System.setProperty("java.awt.headless", "true")
        val image = BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = Color.WHITE
        graphics.fillRect(0, 0, 400, 300)
        graphics.color = Color.RED
        graphics.fillRect(240, 60, 80, 80)
        graphics.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }
}
