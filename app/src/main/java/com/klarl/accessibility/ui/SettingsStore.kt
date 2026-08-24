package com.klarl.accessibility.ui

import android.content.Context

/**
 * Tiny SharedPreferences wrapper for the two user-facing toggles on the status screen.
 * `requireConfirmationForSensitiveActions` is a belt-and-braces UI setting - even when off, the
 * service-side [com.klarl.accessibility.confirmation.ActionRiskClassifier] still fails closed
 * for anything Claude itself flags as sensitive; this toggle only affects the additional local
 * keyword heuristic.
 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("klarl_settings", Context.MODE_PRIVATE)

    var readAloudAiResponses: Boolean
        get() = prefs.getBoolean(KEY_READ_ALOUD, true)
        set(value) = prefs.edit().putBoolean(KEY_READ_ALOUD, value).apply()

    var requireConfirmationForSensitiveActions: Boolean
        get() = prefs.getBoolean(KEY_REQUIRE_CONFIRMATION, true)
        set(value) = prefs.edit().putBoolean(KEY_REQUIRE_CONFIRMATION, value).apply()

    private companion object {
        const val KEY_READ_ALOUD = "read_aloud_ai_responses"
        const val KEY_REQUIRE_CONFIRMATION = "require_confirmation_sensitive_actions"
    }
}
