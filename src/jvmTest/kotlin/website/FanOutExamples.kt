package website

import com.pambrose.jev4k.JevApi
import com.pambrose.jev4k.JevOption
import com.pambrose.jev4k.JevQuery
import com.pambrose.jev4k.ask

// --8<-- [start:support]
enum class TicketCategory(
    override val description: String,
) : JevOption {
    BUG_REPORT("The user is reporting something that is broken or producing errors"),
    BILLING("Charges, invoices, refunds, subscriptions"),
    FEATURE_REQUEST("The user is requesting new functionality"),
    ACCOUNT("Login, permissions, profile, security"),
}

object SupportFanOut : JevQuery() {
    val category by choice<TicketCategory>("Determine the broad category of this support ticket")

    // Speculative: only matter for bug reports.
    val bugSeverity by score("How severe is the reported issue?") {
        levels("Cosmetic; no impact to functionality", "Broken; workaround exists", "Blocking; no workaround")
    }
    val hasReproSteps by noul("Does the user describe specific steps to reproduce the issue?")

    // Speculative: only matters for billing.
    val refundRequested by noul("Is the user explicitly asking for a refund or credit?")

    // Useful for every category.
    val frustration by score("How frustrated does the user appear?") {
        levels("Calm, matter-of-fact", "Frustrated but civil", "Very angry")
    }
}
// --8<-- [end:support]

// --8<-- [start:support-routing]
// One request answers every question; code decides which answers matter for this ticket.
suspend fun handleTicket(
    jev: JevApi,
    ticket: String,
) {
    val result = jev.ask(SupportFanOut, state = ticket)

    when (result[SupportFanOut.category].choice) {
        TicketCategory.BUG_REPORT -> {
            val blocking =
                result[SupportFanOut.bugSeverity].score > 1.5 &&
                    result[SupportFanOut.hasReproSteps].isTrue(0.6)
            routeTo(if (blocking) "engineering-urgent" else "bug-backlog", ticket)
        }

        TicketCategory.BILLING -> {
            routeTo(if (result[SupportFanOut.refundRequested].isTrue(0.7)) "billing-refunds" else "billing", ticket)
        }

        TicketCategory.FEATURE_REQUEST -> {
            routeTo("product-feedback", ticket)
        }

        TicketCategory.ACCOUNT -> {
            routeTo("account-support", ticket)
        }
    }

    if (result[SupportFanOut.frustration].score > 1.5) routeTo("priority-response", ticket)
}
// --8<-- [end:support-routing]

// --8<-- [start:smart-home]
enum class Room { LIVING_ROOM, KITCHEN, BEDROOM, WHOLE_HOUSE, NONE }

enum class Device { LIGHTS, THERMOSTAT, DOOR_LOCK, SPEAKER, NONE }

object HomeCommand : JevQuery() {
    val isCommand by noul("Is the user asking the assistant to change something in the home?")
    val multipleActions by noul("Does the request ask for more than one distinct action?")
    val room by choice<Room>("Which room is the request about?")
    val device by choice<Device>("Which device is the request about?")

    // Speculative: asked before we know the device is the lights.
    val lightAction by choice("If the request is about lights, what should happen to them?") {
        options("turn_on", "turn_off", "dim", "brighten", "none")
    }

    // Speculative: asked before we know the device is the thermostat.
    val temperatureChange by choice("If the request is about temperature, which way should it go?") {
        options("warmer", "cooler", "none")
    }
}

suspend fun handleUtterance(
    jev: JevApi,
    utterance: String,
): String {
    val result = jev.ask(HomeCommand, state = utterance)
    // Every branch's answer arrives in the one request, so reading them all costs nothing extra.
    val device = result[HomeCommand.device].choice
    val lightAction = result[HomeCommand.lightAction].choice
    val room = result[HomeCommand.room].choice
    return when {
        !result[HomeCommand.isCommand].isTrue() -> "hand off to a conversational LLM"
        result[HomeCommand.multipleActions].isTrue() -> "split into single commands, then ask again for each"
        device == Device.LIGHTS -> "lights $lightAction in $room"
        device == Device.THERMOSTAT -> "make it ${result[HomeCommand.temperatureChange].choice}"
        else -> "ask the user to clarify"
    }
}
// --8<-- [end:smart-home]
