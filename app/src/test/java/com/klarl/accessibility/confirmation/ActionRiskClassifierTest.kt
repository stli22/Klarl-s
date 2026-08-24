package com.klarl.accessibility.confirmation

import com.klarl.accessibility.model.CommandActionType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionRiskClassifierTest {

    @Test
    fun `AI-flagged confirmation is always honoured regardless of local heuristic`() {
        assertTrue(
            ActionRiskClassifier.requiresConfirmation(
                actionType = CommandActionType.READ_MORE,
                targetDescription = null,
                aiSaysRequiresConfirmation = true,
                localHeuristicEnabled = false
            )
        )
    }

    @Test
    fun `destructive keyword on activate-element target requires confirmation`() {
        assertTrue(
            ActionRiskClassifier.requiresConfirmation(
                actionType = CommandActionType.ACTIVATE_ELEMENT,
                targetDescription = "Radera konto",
                aiSaysRequiresConfirmation = false
            )
        )
    }

    @Test
    fun `english destructive keyword is also caught`() {
        assertTrue(
            ActionRiskClassifier.requiresConfirmation(
                actionType = CommandActionType.ACTIVATE_ELEMENT,
                targetDescription = "Checkout now",
                aiSaysRequiresConfirmation = false
            )
        )
    }

    @Test
    fun `harmless activate-element target does not require confirmation`() {
        assertFalse(
            ActionRiskClassifier.requiresConfirmation(
                actionType = CommandActionType.ACTIVATE_ELEMENT,
                targetDescription = "Visa mer information",
                aiSaysRequiresConfirmation = false
            )
        )
    }

    @Test
    fun `local heuristic can be disabled by user setting`() {
        assertFalse(
            ActionRiskClassifier.requiresConfirmation(
                actionType = CommandActionType.ACTIVATE_ELEMENT,
                targetDescription = "Radera konto",
                aiSaysRequiresConfirmation = false,
                localHeuristicEnabled = false
            )
        )
    }

    @Test
    fun `non-activation actions never require confirmation from local heuristic`() {
        assertFalse(
            ActionRiskClassifier.requiresConfirmation(
                actionType = CommandActionType.NAVIGATE_TO,
                targetDescription = "Radera konto",
                aiSaysRequiresConfirmation = false
            )
        )
    }
}
