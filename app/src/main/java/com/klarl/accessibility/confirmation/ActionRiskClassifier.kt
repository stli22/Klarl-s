package com.klarl.accessibility.confirmation

import com.klarl.accessibility.model.CommandActionType

/**
 * Decides whether an interpreted command must be confirmed out loud before it's executed.
 *
 * Defense in depth: Claude's own `requiresConfirmation` flag from [ClaudeApiClient] is honoured,
 * but a local keyword check can *only* upgrade a command to "requires confirmation" - it can
 * never downgrade one Claude flagged as sensitive. This means a wrong/missing AI judgement fails
 * closed (still asks for confirmation) rather than open.
 */
object ActionRiskClassifier {

    private val destructiveKeywords = listOf(
        // Swedish
        "skicka", "radera", "ta bort", "köp", "beställ", "betala", "bekräfta köp",
        "avsluta konto", "radera konto", "avbeställ", "logga ut",
        // English (many Swedish apps mix in English UI strings)
        "delete", "remove", "buy", "purchase", "pay", "submit", "send", "checkout",
        "confirm order", "sign out", "log out"
    )

    /**
     * @param localHeuristicEnabled user-facing "kräv muntlig bekräftelse för känsliga åtgärder"
     *   toggle (default on). Only ever narrows *this* keyword heuristic - it can never turn off
     *   confirmation for something Claude itself flagged via [aiSaysRequiresConfirmation].
     */
    fun requiresConfirmation(
        actionType: CommandActionType,
        targetDescription: String?,
        aiSaysRequiresConfirmation: Boolean,
        localHeuristicEnabled: Boolean = true
    ): Boolean {
        if (aiSaysRequiresConfirmation) return true
        if (!localHeuristicEnabled) return false
        if (actionType != CommandActionType.ACTIVATE_ELEMENT) return false
        val text = targetDescription?.lowercase().orEmpty()
        return destructiveKeywords.any { text.contains(it) }
    }
}
