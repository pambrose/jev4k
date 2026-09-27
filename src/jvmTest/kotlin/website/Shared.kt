package website

import com.pambrose.jev4k.JevOption
import kotlinx.serialization.Serializable

/*
 * Code examples for the documentation site (website/jev4k). These files are compiled with the test
 * sources so the examples can't drift from the API, but they are not tests and nothing runs them.
 * Regions between matching start and end snippet markers (see below) are pulled into the docs by
 * pymdownx.snippets.
 */

// --8<-- [start:team]
enum class Team(
    override val description: String,
) : JevOption {
    BILLING("Payments, invoicing, refunds"),
    TECHNICAL("Bugs, outages, integrations"),
    SALES("Pricing, upgrades, new accounts"),
}
// --8<-- [end:team]

// --8<-- [start:support-ticket]
@Serializable
data class SupportTicket(
    val subject: String,
    val body: String,
    val customerPlan: String = "free",
)
// --8<-- [end:support-ticket]

/** Stands in for application code that an example hands work to. */
fun routeTo(
    queue: String,
    item: Any,
) = println("-> $queue: $item")
