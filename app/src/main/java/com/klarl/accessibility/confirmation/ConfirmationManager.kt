package com.klarl.accessibility.confirmation

import android.content.Context
import com.klarl.accessibility.R
import com.klarl.accessibility.voice.SpeechInput
import com.klarl.accessibility.voice.SpeechOutput

/**
 * Speaks a confirmation prompt for a destructive/sensitive action and listens for a yes/no
 * answer before letting the caller proceed. See spec: "Bekräftelsesteg innan destruktiva eller
 * känsliga handlingar utförs (skicka, radera, köpa, skriva i formulär)".
 *
 * Whether confirmation is required at all is decided by [ActionRiskClassifier], not here - this
 * class only owns the speak/listen/decide flow once a caller has already determined it's needed.
 */
class ConfirmationManager(
    private val context: Context,
    private val speechOutput: SpeechOutput,
    private val speechInput: SpeechInput
) {
    private val affirmativeWords = setOf(
        "ja", "japp", "jepp", "jajamensan", "ja tack", "gör det", "bekräfta", "kör",
        "yes", "yeah", "confirm"
    )

    fun requestConfirmation(
        actionDescription: String,
        onConfirmed: () -> Unit,
        onCancelled: () -> Unit
    ) {
        val prompt = context.getString(R.string.tts_confirm_prompt_prefix, actionDescription)
        speechOutput.speak(prompt) {
            speechInput.startListening(
                onResult = { spoken -> resolve(spoken, onConfirmed, onCancelled) },
                onError = {
                    speechOutput.speak(context.getString(R.string.tts_cancelled_action))
                    onCancelled()
                }
            )
        }
    }

    private fun resolve(spoken: String, onConfirmed: () -> Unit, onCancelled: () -> Unit) {
        val normalized = spoken.trim().lowercase()
        when {
            affirmativeWords.any { normalized.contains(it) } -> {
                speechOutput.speak(context.getString(R.string.tts_confirmed_action))
                onConfirmed()
            }
            else -> {
                // Anything that isn't a clear "yes" (including an unrecognized/negative answer)
                // must fail closed and cancel the action.
                speechOutput.speak(context.getString(R.string.tts_cancelled_action))
                onCancelled()
            }
        }
    }
}
