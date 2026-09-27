package website

import com.pambrose.jev4k.JevApi
import com.pambrose.jev4k.query

// The Kotlin that produces the request in WireExamples.txt.
suspend fun wireRequest(jev: JevApi) {
    // --8<-- [start:dsl]
    val result =
        jev.query(state = "Help! My payouts have been failing for 3 days.") {
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
    // --8<-- [end:dsl]
    println(result)
}
