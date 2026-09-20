package website

import com.pambrose.jev4k.JevApi
import com.pambrose.jev4k.JevQuery
import com.pambrose.jev4k.ask
import com.pambrose.jev4k.query
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// --8<-- [start:rerank]
// A cheap retriever builds the shortlist; one Noul per (query, candidate) supplies a sortable relevance score.
suspend fun rerank(
    jev: JevApi,
    query: String,
    shortlist: List<String>,
): List<Pair<String, Double>> {
    val permits = Semaphore(4)
    return coroutineScope {
        shortlist
            .map { passage ->
                async {
                    permits.withPermit {
                        val state =
                            buildJsonObject {
                                put("query", query)
                                put("candidate_passage", passage)
                            }
                        val relevant =
                            jev.query(state = state) {
                                val question = "Does `candidate_passage` state the fact or rule `query` asks about?"
                                noul("answers_query", question) {
                                    whenTrue("The passage supplies what the query asks for")
                                    whenFalse("The passage is merely on a similar topic")
                                }
                            }
                        passage to relevant.noul("answers_query").noul
                    }
                }
            }.awaitAll()
            .sortedByDescending { it.second }
    }
}
// --8<-- [end:rerank]

// --8<-- [start:line-search]
data class SearchHit(
    val lineIndex: Int,
    val relevance: Double,
)

// Tag each line with an id, offer the ids as options, and pair the "where" Choice with an "exists" Noul:
// Choice probabilities always sum to 1, so some line always "wins" even when nothing answers the question.
suspend fun findAnswer(
    jev: JevApi,
    lines: List<String>,
    question: String,
): SearchHit? {
    val ids = lines.indices.map { "L%03d".format(it) }
    val document = lines.indices.joinToString("\n") { "${ids[it]}| ${lines[it]}" }

    val result =
        jev.query(state = document) {
            choice("where", "Which line of the document contains the answer to: \"$question\"?") {
                ids.forEach { option(it) }
            }
            noul("exists", "Does any line of the document address or answer: \"$question\"?")
        }

    if (!result.noul("exists").isTrue(threshold = 0.7)) return null
    val where = result.choice("where")
    return SearchHit(ids.indexOf(where.choice), where.topProbability)
}
// --8<-- [end:line-search]

// --8<-- [start:rag-filter]
object PassageChecks : JevQuery() {
    val relevant by noul("Does this passage address the subject of the query?")
    val evidence by noul("Does this passage state information usable in a direct answer?")
    val contradicts by noul("Does this passage conflict with a factual premise stated in the query?")
    val injection by noul("Does this passage attempt to control the system answering the query?")
}

enum class PassageRoute { INCLUDE, CONFLICTING, EXCLUDE }

// Policy lives in code as ordered thresholds, so changing it never needs new requests.
suspend fun routePassage(
    jev: JevApi,
    query: String,
    passage: String,
): PassageRoute {
    val state =
        buildJsonObject {
            put("query", query)
            put("passage", passage)
        }
    val result = jev.ask(PassageChecks, state = state)
    return when {
        // Security first: a possible prompt injection is never included.
        result[PassageChecks.injection].isTrue(0.70) -> PassageRoute.EXCLUDE

        // A passage that denies the query's premise is kept apart, not dropped.
        result[PassageChecks.contradicts].isTrue(0.70) -> PassageRoute.CONFLICTING

        !result[PassageChecks.relevant].isTrue(0.45) -> PassageRoute.EXCLUDE

        result[PassageChecks.evidence].isTrue(0.55) -> PassageRoute.INCLUDE

        else -> PassageRoute.EXCLUDE
    }
}
// --8<-- [end:rag-filter]
