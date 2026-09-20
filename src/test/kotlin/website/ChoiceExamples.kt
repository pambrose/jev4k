package website

import com.pambrose.jev4k.JevApi
import com.pambrose.jev4k.JevResult
import com.pambrose.jev4k.query

suspend fun choiceBasic(jev: JevApi) {
    // --8<-- [start:basic]
    val result =
        jev.query(state = "My running shoes arrived in the wrong size. Can I swap them for a size 10?") {
            choice("department", "Which team should handle this?") {
                "returns" means "Exchanges, refunds, wrong or damaged items"
                "shipping" means "Delivery status, delays, lost packages"
                "billing" means "Charges, invoices, payment problems"
            }
        }
    val department = result.choice("department")
    println(department.choice)     // "returns"
    println(department.confidence) // e.g. 1.0: all probability on one option
    // --8<-- [end:basic]
}

suspend fun choiceUndescribed(jev: JevApi) {
    // --8<-- [start:undescribed]
    // When the option names say it all, leave them undescribed; they are sent as null.
    val result =
        jev.query(state = "This is the third time I'm writing. Fix it or I'm cancelling.") {
            choice("tone", "What is the customer's tone?") {
                options("calm", "frustrated", "angry")
            }
        }
    // --8<-- [end:undescribed]
    println(result.choice("tone").choice)
}

suspend fun choiceEscape(jev: JevApi) {
    // --8<-- [start:escape]
    // Add an explicit way out when the list might not cover every input.
    val result =
        jev.query(state = "What's your company's policy on remote work?") {
            choice("meeting_type", "What type of meeting is this, based on the title and description?") {
                options("standup", "planning", "retrospective", "one_on_one")
                "none" means "Not a meeting, or a kind of meeting not listed here"
            }
        }
    if (result.choice("meeting_type").choice == "none") println("not a meeting")
    // --8<-- [end:escape]
}

// --8<-- [start:distribution]
fun describe(result: JevResult) {
    val department = result.choice("department")

    println(department.choice)                 // most likely option
    println(department.topProbability)         // its probability, e.g. 0.60
    println(department.probability("billing")) // any option's probability (0.0 if absent)

    // Options from most to least likely.
    department.ranked().forEach { (option, p) -> println("$option: $p") }

    // A second team with a real share of the probability gets a copy.
    department.probabilities
        .filter { (team, p) -> team != department.choice && p > 0.25 }
        .forEach { (team, _) -> routeTo(team, "cc") }
}
// --8<-- [end:distribution]

suspend fun choiceSpeculative(jev: JevApi) {
    // --8<-- [start:speculative]
    val result =
        jev.query(state = "Shoes arrived two weeks late and in the wrong size. Also I see two charges on my card.") {
            choice("department", "Which team should handle this?") {
                "returns" means "Exchanges, refunds, wrong or damaged items"
                "shipping" means "Delivery status, delays, lost packages"
                "billing" means "Charges, invoices, payment problems"
            }
            // Speculative: only used if the department is "returns".
            choice("return_reason", "If the customer wants to return something, why?") {
                "wrong_size" means "The item doesn't fit"
                "damaged" means "The item arrived broken or faulty"
                "changed_mind" means "The item is fine, the customer no longer wants it"
                "other" means "A return reason that fits none of the above"
            }
            // Speculative: only used if the department is "shipping".
            choice("shipping_issue", "If this is a shipping problem, which kind is it?") {
                "not_delivered" means "The package never arrived"
                "delayed" means "The package is late but still on its way"
                "other" means "A shipping problem that fits none of the above"
            }
        }

    val department = result.choice("department")
    when (department.choice) {
        "returns" -> routeTo("returns", result.choice("return_reason").choice)
        "shipping" -> routeTo("shipping", result.choice("shipping_issue").choice)
        else -> routeTo("billing", "charges")
    }
    // --8<-- [end:speculative]
}

// --8<-- [start:many-options]
// Options are cheap: offer the full list (up to 255) rather than a shortlist.
suspend fun categorize(
    jev: JevApi,
    listing: String,
    categories: List<String>,
): String {
    val result =
        jev.query(state = listing) {
            choice("category", "Which product category does this listing belong to?") {
                categories.forEach { option(it) }
                "other" means "None of the listed categories fits"
            }
        }
    return result.choice("category").choice
}
// --8<-- [end:many-options]
