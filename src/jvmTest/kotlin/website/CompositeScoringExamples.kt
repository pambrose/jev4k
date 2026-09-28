package website

import com.pambrose.jev4k.JevApi
import com.pambrose.jev4k.JevQuery
import com.pambrose.jev4k.JevResult
import com.pambrose.jev4k.ask
import com.pambrose.jev4k.query
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

// --8<-- [start:resume]
object ResumeScreen : JevQuery() {
    val pythonDepth by score("How much depth of Python experience does this candidate have, based on the resume?") {
        level("No Python experience mentioned")
        level("Mentioned but no detail")
        level("Used in projects, some specifics")
        level("Primary language, multiple projects")
        level("Deep expertise: architecture, performance, libraries")
    }
    val leadership by score("How much experience does this candidate have managing or leading engineering teams?") {
        level("No management experience mentioned")
        level("Informal mentorship or tech lead role")
        level("Led a small team or project")
        level("Managed a team with direct reports")
        level("Managed multiple teams or an engineering org")
    }
    val systemDesign by score("How much experience does this candidate have designing large-scale systems?") {
        level("No architecture work mentioned")
        level("Contributed to design discussions")
        level("Designed components of a larger system")
        level("Owned the architecture of a significant system")
        level("Designed systems at scale across multiple domains")
    }
}
// --8<-- [end:resume]

// --8<-- [start:weights]
data class RoleWeights(
    val python: Double,
    val leadership: Double,
    val systemDesign: Double,
)

val seniorEngineer = RoleWeights(python = 0.45, leadership = 0.10, systemDesign = 0.45)
val engineeringManager = RoleWeights(python = 0.15, leadership = 0.55, systemDesign = 0.30)

// The same answers rank candidates for different roles; only the weights change.
fun fit(
    result: JevResult,
    weights: RoleWeights,
): Double =
    weights.python * result[ResumeScreen.pythonDepth].normalized +
        weights.leadership * result[ResumeScreen.leadership].normalized +
        weights.systemDesign * result[ResumeScreen.systemDesign].normalized
// --8<-- [end:weights]

// --8<-- [start:ranking]
// The only requests: one per resume, a few at a time. The results are what the rankings reuse.
suspend fun assessResumes(
    jev: JevApi,
    resumes: Map<String, String>,
    parallelism: Int = 4,
): Map<String, JevResult> {
    val permits = Semaphore(parallelism)
    return coroutineScope {
        resumes
            .map { (name, resume) -> async { name to permits.withPermit { jev.ask(ResumeScreen, state = resume) } } }
            .awaitAll()
            .toMap()
    }
}

// Pure code over stored results: every weighting is free.
fun rankCandidates(
    results: Map<String, JevResult>,
    weights: RoleWeights,
    top: Int = 5,
): List<Pair<String, Double>> =
    results
        .map { (name, result) -> name to fit(result, weights) }
        .sortedByDescending { it.second }
        .take(top)

suspend fun shortlistBothRoles(
    jev: JevApi,
    resumes: Map<String, String>,
) {
    val results = assessResumes(jev, resumes)                 // asks Jev once per resume
    val engineers = rankCandidates(results, seniorEngineer)   // no new requests
    val managers = rankCandidates(results, engineeringManager) // no new requests
    println("engineers: $engineers\nmanagers: $managers")
}
// --8<-- [end:ranking]

// --8<-- [start:signals]
// Independent yes/no signals combined with weights, instead of one vague "is this spam?".
suspend fun spamRisk(
    jev: JevApi,
    email: String,
): Double {
    val result =
        jev.query(state = email) {
            noul("credentials", "Does the message ask the recipient for a password or other login credential?")
            noul("reward", "Does the message claim the recipient received an unexpected prize, payment, or reward?")
            noul("pressure", "Does the message pressure the recipient to act quickly?")
            noul("sender_mismatch", "Does the sender's named organization conflict with their email domain?")
        }
    return 0.40 * result.noul("credentials").noul +
        0.25 * result.noul("sender_mismatch").noul +
        0.20 * result.noul("reward").noul +
        0.15 * result.noul("pressure").noul
}
// --8<-- [end:signals]
