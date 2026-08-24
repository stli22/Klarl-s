package com.klarl.accessibility.model

/**
 * Result of *local* command parsing (no network round trip). Kept intentionally small: the
 * spec asks for a handful of simple commands to resolve instantly, everything else goes to
 * Claude. See `ai/CommandInterpreter.kt`.
 */
sealed class LocalCommand {
    data object ReadMore : LocalCommand()
    data object RepeatSummary : LocalCommand()
    data object GoBack : LocalCommand()
    data object ReadHeadings : LocalCommand()
    data class SelectOption(val index: Int) : LocalCommand()

    /** Didn't match a known local pattern - needs to go to Claude with the last screen context. */
    data class NeedsAiInterpretation(val rawText: String) : LocalCommand()
}

enum class CommandActionType {
    READ_MORE,
    REPEAT_SUMMARY,
    GO_BACK,
    NAVIGATE_TO,
    ACTIVATE_ELEMENT,
    READ_HEADINGS,
    UNKNOWN
}

/** Claude's interpretation of an ambiguous voice command, resolved against the last [ScreenSnapshot]. */
data class InterpretedCommand(
    val action: CommandActionType,
    val targetDescription: String?,
    val spokenResponse: String,
    val requiresConfirmation: Boolean
)
