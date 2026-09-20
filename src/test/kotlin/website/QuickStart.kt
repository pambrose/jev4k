package website

// --8<-- [start:first-query]
import com.pambrose.jev4k.JevClient
import com.pambrose.jev4k.query

suspend fun main() {
    // Reads the API key from the TYPESAFE_API_KEY environment variable.
    JevClient().use { jev ->
        val ticket = "Hi, my Stripe connection has failed for 3 days and I'm losing sales. Help ASAP!"
        val result = jev.query(state = ticket) {
            noul("urgent", "Does this message convey urgency or time-sensitivity?")
            choice("department", "Which team should handle this?") {
                "billing" means "Payment or subscription issues"
                "technical" means "Bugs or integration problems"
                "sales" means "Pricing or account questions"
            }
            score("frustration", "How frustrated does the customer appear?") {
                levels("Calm, just stating facts", "Frustrated but civil", "Very angry, strong language")
            }
        }

        println(result.noul("urgent").noul)         // e.g. 0.999: probability of yes
        println(result.choice("department").choice) // e.g. "technical"
        println(result.score("frustration").score)  // e.g. 1.04: position along the three levels
    }
}
// --8<-- [end:first-query]
