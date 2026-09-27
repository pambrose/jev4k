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

/** True only with a key and an explicit opt-in, so a live run is never something that just happens. */
internal fun liveApiEnabled(): Boolean =
    !System.getenv("TYPESAFE_API_KEY").isNullOrBlank() && System.getenv("JEV4K_LIVE") == "1"

class LiveApiCondition : Condition {
    override fun evaluate(kclass: KClass<out Spec>): Boolean = liveApiEnabled()
}

/**
 * Real calls to the TypeSafe API. Runs only when `TYPESAFE_API_KEY` is set and `JEV4K_LIVE=1`
 * (`make live-tests` sets the latter), so ordinary test runs never spend tokens. Jev isn't fully
 * deterministic, so these check shapes and ranges rather than exact values.
 *
 * The gate is on the spec, not on each test: `.env` supplies a real key to every test task, so a test added
 * here without the per-test gate would otherwise spend tokens on every `make tests`.
 */
@EnabledIf(LiveApiCondition::class)
class LiveSmokeTest : StringSpec() {
    private val live = liveApiEnabled()

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
    }
}
