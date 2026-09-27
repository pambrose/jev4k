package website

import com.pambrose.jev4k.JevApi
import com.pambrose.jev4k.JevClient
import com.pambrose.jev4k.RetryPolicy
import com.pambrose.jev4k.ask
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration.Companion.seconds

fun clientDefault() {
    // --8<-- [start:default]
    // With TYPESAFE_API_KEY set, the defaults are all you need.
    JevClient().use { jev ->
        println(jev.config) // the key is redacted
    }
    // --8<-- [end:default]
}

fun clientBuilder(): JevClient {
    // --8<-- [start:builder]
    val jev =
        JevClient {
            apiKey = System.getenv("MY_TYPESAFE_KEY")      // default: TYPESAFE_API_KEY
            baseUrl = "https://api.typesafe.ai"            // default: TYPESAFE_BASE_URL, then this
            defaultModel = "jev-1.13.0"                    // default: TYPESAFE_DEFAULT_MODEL, then "jev-latest"
            timeout = 15.seconds                           // per HTTP attempt; default 10 s
            retry = RetryPolicy(maxRetries = 4)            // see "Retries & Errors"
            headers["X-Request-Source"] = "support-triage" // sent with every request
        }
    // --8<-- [end:builder]
    return jev
}

fun clientRetry(): List<JevClient> =
    listOf(
        // --8<-- [start:retry]
        // More patience for a batch job:
        JevClient {
            retry =
                RetryPolicy(
                    maxRetries = 5,
                    initialBackoff = 1.seconds,
                    maxBackoff = 20.seconds,
                )
        },
        // Fail fast in an interactive request path:
        JevClient { retry = RetryPolicy.NONE },
        // Retry rate limits and overloads, but not other server errors:
        JevClient { retry = RetryPolicy(retryStatuses = setOf(429, 529)) },
        // --8<-- [end:retry]
    )

fun clientEngine(): JevClient {
    // --8<-- [start:engine]
    // Supply a tuned Ktor engine instead of the default CIO one. The client won't close an engine you pass in.
    val jev =
        JevClient {
            engine =
                CIO.create {
                    maxConnectionsCount = 64
                    endpoint.maxConnectionsPerRoute = 16
                    endpoint.connectTimeout = 2_000 // milliseconds
                }
        }
    // --8<-- [end:engine]
    return jev
}

suspend fun callsSuspend(jev: JevApi) {
    // --8<-- [start:suspend]
    // JevApi calls are suspend functions: call them from a coroutine.
    val result = jev.ask(Triage, state = "Our dashboard has been down for an hour.")
    // --8<-- [end:suspend]
    println(result)
}

fun callsBlocking() {
    // --8<-- [start:blocking]
    // Outside coroutines (scripts, main, Java callers), use the blocking mirror.
    JevClient().use { jev ->
        val result = jev.blocking.ask(Triage, state = "Our dashboard has been down for an hour.")
        println(result[Triage.team].choice)
        println(jev.blocking.models().map { it.name })
    }
    // --8<-- [end:blocking]
}

suspend fun callsModel(jev: JevApi) {
    // --8<-- [start:model]
    // Override the model per call. Pin a versioned model once you've tuned thresholds against it,
    // because an alias such as jev-latest can move to a newer release.
    val pinned = jev.ask(Triage, state = "The API returns 500s.", model = "jev-1.13.0")
    println("answered by ${pinned.model}")

    // List the model names this account can use.
    jev.models().forEach { println("${it.name} (${it.releaseDate}): ${it.description}") }
    // --8<-- [end:model]
}

suspend fun callsEvaluate(jev: JevApi) {
    // --8<-- [start:evaluate]
    // evaluate is the one network call everything else builds on.
    val result = jev.evaluate(JsonPrimitive("Refund me now!"), Triage.questions, model = null)
    // --8<-- [end:evaluate]
    println(result)
}

// --8<-- [start:concurrency]
// Run many requests concurrently, but bound the fan-out: shared keys hit rate limits at around 8 at once.
suspend fun triageBatch(
    jev: JevApi,
    tickets: List<String>,
    parallelism: Int = 4,
): List<Team> {
    val permits = Semaphore(parallelism)
    return coroutineScope {
        tickets
            .map { ticket -> async { permits.withPermit { jev.ask(Triage, state = ticket)[Triage.team].choice } } }
            .awaitAll()
    }
}
// --8<-- [end:concurrency]

// --8<-- [start:testable]
// Depend on the JevApi interface, not JevClient, and pass it in.
class TicketRouter(
    private val jev: JevApi,
) {
    suspend fun queueFor(ticket: String): String =
        when (jev.ask(Triage, state = ticket)[Triage.team].choice) {
            Team.BILLING -> "billing"
            Team.TECHNICAL -> "engineering"
            Team.SALES -> "sales"
        }
}

// In production, wire in the real client; elsewhere, any JevApi implementation will do.
fun productionRouter(): TicketRouter = TicketRouter(JevClient())
// --8<-- [end:testable]
