package website

import com.pambrose.jev4k.JevApi
import com.pambrose.jev4k.jsonOf
import com.pambrose.jev4k.query

// --8<-- [start:taxonomy-walk]
// A tree of categories: each node maps child names to their own subtrees (empty for leaves).
data class Node(
    val children: Map<String, Node> = emptyMap(),
)

// Walk the taxonomy one level per request. Each option carries its subtree, so the model can see what
// lives under a branch before committing to it. Stop early when a level is too uncertain.
suspend fun classifyPath(
    jev: JevApi,
    item: String,
    root: Node,
    minConfidence: Double = 0.5,
): List<String> {
    val path = mutableListOf<String>()
    var node = root
    while (node.children.isNotEmpty()) {
        val next = pickChild(jev, item, node, minConfidence) ?: break // stop at the deepest confident ancestor
        path += next
        node = node.children.getValue(next)
    }
    return path
}

private suspend fun pickChild(
    jev: JevApi,
    item: String,
    node: Node,
    minConfidence: Double,
): String? {
    node.children.keys.singleOrNull()?.let { return it } // a single child needs no question
    val level =
        jev
            .query(state = item) {
                choice("child", "Which direct child category best matches this item?") {
                    node.children.forEach { (name, child) -> name means jsonOf(child.children.keys.toList()) }
                }
            }.choice("child")
    return level.choice.takeIf { level.confidence >= minConfidence }
}
// --8<-- [end:taxonomy-walk]

// --8<-- [start:shortlist-rerank]
data class Skill(
    val name: String,
    val summary: String,
    val details: String,
)

// For a large roster: rank everything cheaply, then judge the top few against their full details.
suspend fun suggestSkill(
    jev: JevApi,
    request: String,
    skills: List<Skill>,
): Skill? {
    val wide =
        jev.query(state = request) {
            choice("which", "Which of these skills, if any, is the right one for the user's request?") {
                skills.forEach { it.name means it.summary }
            }
            noul("needs_action", "Is the assistant being asked to act on the user's files, accounts, or services?")
        }
    if (!wide.noul("needs_action").isTrue(0.3)) return null

    val shortlist = wide.choice("which").ranked().take(3).map { (name, _) -> skills.first { it.name == name } }
    val detailed =
        jev.query(state = request) {
            choice("which", "Exactly one of these skills fits the request. Which one? Read what each actually does.") {
                shortlist.forEach { it.name means "${it.summary} — ${it.details}" }
            }
            shortlist.forEach { skill ->
                noul("fits_${skill.name}", "Does the skill '${skill.name}' do what the user asks? It: ${skill.summary}")
            }
        }
    // The Choice picks which skill; the independent Nouls decide whether to suggest one at all.
    if (detailed.nouls.values.none { it.isTrue(0.3) }) return null
    return shortlist.first { it.name == detailed.choice("which").choice }
}
// --8<-- [end:shortlist-rerank]
