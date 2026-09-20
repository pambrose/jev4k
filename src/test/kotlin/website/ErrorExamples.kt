package website

import com.pambrose.jev4k.JevApi
import com.pambrose.jev4k.JevApiException
import com.pambrose.jev4k.JevAuthenticationException
import com.pambrose.jev4k.JevConnectionException
import com.pambrose.jev4k.JevException
import com.pambrose.jev4k.JevOverloadedException
import com.pambrose.jev4k.JevRateLimitException
import com.pambrose.jev4k.JevResponseValidationException
import com.pambrose.jev4k.JevTimeoutException
import com.pambrose.jev4k.JevUnprocessableEntityException
import com.pambrose.jev4k.JevValidationException
import com.pambrose.jev4k.JevResult
import com.pambrose.jev4k.ask
import com.pambrose.jev4k.query

// --8<-- [start:handling]
suspend fun triageOrNull(
    jev: JevApi,
    ticket: String,
): JevResult? =
    try {
        jev.ask(Triage, state = ticket)
    } catch (e: JevAuthenticationException) {
        throw IllegalStateException("Check TYPESAFE_API_KEY", e)
    } catch (e: JevRateLimitException) {
        // Already retried per the RetryPolicy; back off further before trying again.
        println("rate limited; server suggests waiting ${e.retryAfter}")
        null
    } catch (e: JevTimeoutException) {
        println("timed out: ${e.message}")
        null
    } catch (e: JevConnectionException) {
        println("no response from TypeSafe: ${e.message}")
        null
    } catch (e: JevApiException) {
        // Every other HTTP error: status, raw body, and the request id for support.
        println("HTTP ${e.status} (request ${e.requestId}): ${e.body}")
        null
    }
// --8<-- [end:handling]

suspend fun validationErrors(jev: JevApi) {
    // --8<-- [start:validation]
    try {
        jev.query(state = "Is this spam?") {
            noul("spam", "")                                             // blank instructions
            score("severity", "How severe?") { level("only one level") } // a Score needs 2..10 levels
        }
    } catch (e: JevValidationException) {
        // Nothing was sent. Every problem is listed at once.
        e.problems.forEach(::println)
    }
    // --8<-- [end:validation]
}

fun describeFailure(e: JevException): String =
    // --8<-- [start:when]
    when (e) {
        is JevValidationException -> "invalid request: ${e.problems}"
        is JevUnprocessableEntityException -> "server rejected the request: ${e.bodyJson}"
        is JevOverloadedException -> "TypeSafe is overloaded (529); try again shortly"
        is JevResponseValidationException -> "unusable response at ${e.fieldPath}"
        is JevApiException -> "HTTP ${e.status} from ${e.endpoint}"
        is JevConnectionException -> "network problem: ${e.message}"
        else -> e.message ?: e.toString()
    }
// --8<-- [end:when]
