package website

import com.pambrose.jev4k.JevApi
import com.pambrose.jev4k.JevOption
import com.pambrose.jev4k.JevQuery
import com.pambrose.jev4k.ask

// --8<-- [start:intent]
enum class CustomerIntent(
    override val description: String,
) : JevOption {
    ORDER_STATUS("Asking about an existing order"),
    PRODUCT_QUESTION("Asking about a product before buying"),
    RETURN_EXCHANGE("Wants to return or exchange something"),
    COMPLAINT("Unhappy with the experience, wants resolution"),
}

object IntentRouting : JevQuery() {
    val intent by choice<CustomerIntent>("What is the primary intent of this customer message?")
    val complexity by score("How complex is this request to resolve?") {
        level("Simple lookup or standard procedure")
        level("Requires some judgment or a multi-step process")
        level("Unusual situation, edge case, or escalation needed")
    }
}
// --8<-- [end:intent]

// --8<-- [start:intent-routing]
sealed interface Handler {
    data object Deterministic : Handler

    data class SpecialistLlm(
        val specialty: String,
    ) : Handler

    data object Human : Handler
}

// One fast, cheap call picks the handler; expensive resources are used only when needed.
suspend fun chooseHandler(
    jev: JevApi,
    message: String,
): Handler {
    val result = jev.ask(IntentRouting, state = message)
    val intent = result[IntentRouting.intent]
    val complexity = result[IntentRouting.complexity]

    if (intent.confidence < 0.5) return Handler.Human

    return when (intent.choice) {
        CustomerIntent.ORDER_STATUS -> {
            Handler.Deterministic
        }

        CustomerIntent.PRODUCT_QUESTION -> {
            Handler.SpecialistLlm("product catalog")
        }

        CustomerIntent.RETURN_EXCHANGE -> {
            Handler.SpecialistLlm("returns policy")
        }

        CustomerIntent.COMPLAINT -> {
            val tooHardToAutomate = complexity.score > 1 || complexity.confidence < 0.5
            if (tooHardToAutomate) Handler.Human else Handler.SpecialistLlm("complaints")
        }
    }
}
// --8<-- [end:intent-routing]

// --8<-- [start:model-router]
enum class ModelTier { SMALL, LARGE, REASONING }

object PromptDifficulty : JevQuery() {
    val difficulty by score("How much reasoning does answering this prompt require?") {
        level("A lookup, rewrite, or short factual answer")
        level("Several steps, or combining a few pieces of information")
        level("Long multi-step reasoning, math, or planning")
    }
    val needsTools by noul("Does answering require acting on files, accounts, or external services?")
}

// Route each prompt to the cheapest model that can handle it.
suspend fun pickModel(
    jev: JevApi,
    prompt: String,
): ModelTier {
    val result = jev.ask(PromptDifficulty, state = prompt)
    val difficulty = result[PromptDifficulty.difficulty]
    return when {
        // Unsure: don't under-provision.
        difficulty.confidence < 0.5 -> ModelTier.LARGE

        result[PromptDifficulty.needsTools].isTrue() || difficulty.score > 1.5 -> ModelTier.REASONING

        difficulty.score > 0.5 -> ModelTier.LARGE

        else -> ModelTier.SMALL
    }
}
// --8<-- [end:model-router]
