package com.klarl.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.klarl.accessibility.actions.ScreenActionExecutor
import com.klarl.accessibility.ai.ClaudeApiClient
import com.klarl.accessibility.ai.ClaudeConfig
import com.klarl.accessibility.ai.CommandInterpreter
import com.klarl.accessibility.confirmation.ActionRiskClassifier
import com.klarl.accessibility.confirmation.ConfirmationManager
import com.klarl.accessibility.extraction.AndroidAccessibilityNodeAdapter
import com.klarl.accessibility.extraction.NodeExtractor
import com.klarl.accessibility.model.CommandActionType
import com.klarl.accessibility.model.InterpretedCommand
import com.klarl.accessibility.model.LocalCommand
import com.klarl.accessibility.model.ScreenRole
import com.klarl.accessibility.model.ScreenSnapshot
import com.klarl.accessibility.serialization.ScreenSnapshotSerializer
import com.klarl.accessibility.state.SessionState
import com.klarl.accessibility.ui.SettingsStore
import com.klarl.accessibility.voice.AndroidMicActivityIndicator
import com.klarl.accessibility.voice.AndroidSpeechRecognizerInput
import com.klarl.accessibility.voice.AndroidTextToSpeechOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Coordinates the whole MVP flow described in the spec:
 * new foreground window -> extract & mask UI tree -> ask Claude for a summary -> speak it ->
 * listen for a voice command -> resolve it locally or via Claude -> confirm if sensitive -> act.
 *
 * Kept intentionally as "thin plumbing" - the actual logic each step depends on
 * (extraction/masking, prompt building, risk classification, local command parsing) lives in
 * separately unit-tested classes; this class mostly wires them together against the live window.
 */
class ScreenReaderAccessibilityService : AccessibilityService() {

    private companion object {
        const val TAG = "KlarlA11yService"

        /** Debounce so a burst of window-change events only triggers one summary pass. */
        const val WINDOW_SETTLE_DELAY_MS = 500L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pendingSettleRunnable: Runnable? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val nodeExtractor = NodeExtractor()
    private val sessionState = SessionState()
    private lateinit var claudeApiClient: ClaudeApiClient
    private lateinit var speechOutput: AndroidTextToSpeechOutput
    private lateinit var speechInput: AndroidSpeechRecognizerInput
    private lateinit var confirmationManager: ConfirmationManager
    private lateinit var actionExecutor: ScreenActionExecutor
    private lateinit var settingsStore: SettingsStore

    override fun onServiceConnected() {
        super.onServiceConnected()
        claudeApiClient = ClaudeApiClient()
        speechOutput = AndroidTextToSpeechOutput(this)
        val micIndicator = AndroidMicActivityIndicator(this)
        speechInput = AndroidSpeechRecognizerInput(this, micIndicator)
        confirmationManager = ConfirmationManager(this, speechOutput, speechInput)
        actionExecutor = ScreenActionExecutor(this)
        settingsStore = SettingsStore(this)
        Log.i(TAG, "Klarläs skärmguide ansluten")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return // never narrate our own settings screen

        pendingSettleRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable { handleForegroundWindowSettled(packageName) }
        pendingSettleRunnable = runnable
        handler.postDelayed(runnable, WINDOW_SETTLE_DELAY_MS)
    }

    override fun onInterrupt() {
        speechOutput.stop()
        speechInput.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        pendingSettleRunnable?.let { handler.removeCallbacks(it) }
        speechInput.destroy()
        speechOutput.shutdown()
        serviceScope.cancel()
    }

    // ---- Step 1-3: extract, mask, summarize --------------------------------------------------

    private fun handleForegroundWindowSettled(packageName: String) {
        val root = rootInActiveWindow ?: return

        val rootNode = AndroidAccessibilityNodeAdapter(root)
        val windowTitle = runCatching { root.window?.title?.toString() }.getOrNull()
        val extractedNodes = try {
            nodeExtractor.extract(rootNode)
        } finally {
            root.recycle()
        }

        val snapshot = ScreenSnapshot(
            packageName = packageName,
            windowTitle = windowTitle,
            timestampMillis = System.currentTimeMillis(),
            rootNodes = extractedNodes
        )
        if (snapshot.isEmpty) return

        val snapshotText = ScreenSnapshotSerializer.toCompactText(snapshot)
        sessionState.update(snapshot, snapshotText)

        if (!ClaudeConfig.isConfigured) {
            speechOutput.speak(getString(R.string.status_api_key_missing))
            return
        }

        serviceScope.launch {
            claudeApiClient.summarizeScreen(snapshotText)
                .onSuccess { summary ->
                    sessionState.lastSummary = summary
                    if (!settingsStore.readAloudAiResponses) {
                        // Quiet mode: screen is still summarized and ready for "läs mer"/
                        // "upprepa" on demand, just not narrated automatically.
                        beginListeningForCommand()
                        return@onSuccess
                    }
                    val spoken = buildString {
                        append(summary.summaryText)
                        if (summary.options.isNotEmpty()) {
                            append(" Du kan till exempel säga: ")
                            append(summary.options.joinToString(", ") { it.label })
                        }
                    }
                    speechOutput.speak(spoken) { beginListeningForCommand() }
                }
                .onFailure { error ->
                    Log.w(TAG, "Kunde inte hämta sammanfattning från Claude", error)
                    speechOutput.speak(getString(R.string.tts_ai_unavailable))
                }
        }
    }

    // ---- Step 4: voice commands ---------------------------------------------------------------

    private fun beginListeningForCommand() {
        speechInput.startListening(
            onResult = { spokenText -> handleVoiceCommand(spokenText) },
            onError = { /* stay silent; user can re-trigger by switching apps or repeating */ }
        )
    }

    private fun handleVoiceCommand(spokenText: String) {
        when (val local = CommandInterpreter.classify(spokenText)) {
            is LocalCommand.GoBack -> {
                actionExecutor.goBack()
                speechOutput.speak(getString(R.string.tts_navigated_back)) { beginListeningForCommand() }
            }
            is LocalCommand.RepeatSummary -> repeatLastSummary()
            is LocalCommand.ReadHeadings -> readHeadings()
            is LocalCommand.ReadMore -> readFullScreenText()
            is LocalCommand.SelectOption -> selectSummaryOption(local.index)
            is LocalCommand.NeedsAiInterpretation -> interpretWithAi(local.rawText)
        }
    }

    private fun repeatLastSummary() {
        val summary = sessionState.lastSummary
        if (summary == null) {
            speechOutput.speak(getString(R.string.tts_no_screen_data))
            return
        }
        speechOutput.speak(summary.summaryText) { beginListeningForCommand() }
    }

    private fun readHeadings() {
        val snapshot = sessionState.lastSnapshot
        if (snapshot == null) {
            speechOutput.speak(getString(R.string.tts_no_screen_data))
            return
        }
        val headings = mutableListOf<String>()
        fun collect(nodes: List<com.klarl.accessibility.model.ScreenNode>) {
            nodes.forEach {
                if (it.role == ScreenRole.HEADING && !it.text.isNullOrBlank()) headings += it.text
                collect(it.children)
            }
        }
        collect(snapshot.rootNodes)

        val spoken = if (headings.isEmpty()) {
            getString(R.string.tts_no_screen_data)
        } else {
            getString(R.string.tts_reading_headings_intro) + " " + headings.joinToString(". ")
        }
        speechOutput.speak(spoken) { beginListeningForCommand() }
    }

    private fun readFullScreenText() {
        val snapshotText = sessionState.lastSnapshotText
        if (snapshotText == null) {
            speechOutput.speak(getString(R.string.tts_no_screen_data))
            return
        }
        // MVP "read more": re-run the summarizer, but the AI already has the full tree - a
        // future iteration could ask for an *elaborated* (not just repeated) summary here.
        serviceScope.launch {
            claudeApiClient.summarizeScreen(snapshotText)
                .onSuccess { summary -> speechOutput.speak(summary.summaryText) { beginListeningForCommand() } }
                .onFailure { speechOutput.speak(getString(R.string.tts_ai_unavailable)) }
        }
    }

    private fun selectSummaryOption(index: Int) {
        val option = sessionState.lastSummary?.options?.getOrNull(index - 1)
        if (option == null) {
            speechOutput.speak(getString(R.string.tts_command_not_understood)) { beginListeningForCommand() }
            return
        }
        interpretWithAi(option.label)
    }

    private fun interpretWithAi(rawText: String) {
        val screenText = sessionState.lastSnapshotText
        if (screenText == null || !ClaudeConfig.isConfigured) {
            speechOutput.speak(getString(R.string.tts_ai_unavailable))
            return
        }
        serviceScope.launch {
            claudeApiClient.interpretCommand(rawText, screenText)
                .onSuccess { interpreted -> actOnInterpretedCommand(interpreted) }
                .onFailure {
                    Log.w(TAG, "Kunde inte tolka kommando via Claude", it)
                    speechOutput.speak(getString(R.string.tts_ai_unavailable))
                }
        }
    }

    // ---- Step 5-8: act on an AI-interpreted command, with confirmation for risky actions -----

    private fun actOnInterpretedCommand(interpreted: InterpretedCommand) {
        val needsConfirmation = ActionRiskClassifier.requiresConfirmation(
            actionType = interpreted.action,
            targetDescription = interpreted.targetDescription,
            aiSaysRequiresConfirmation = interpreted.requiresConfirmation,
            localHeuristicEnabled = settingsStore.requireConfirmationForSensitiveActions
        )

        if (needsConfirmation) {
            val description = interpreted.targetDescription ?: interpreted.spokenResponse
            confirmationManager.requestConfirmation(
                actionDescription = description,
                onConfirmed = { executeInterpretedAction(interpreted) },
                onCancelled = { /* nothing to do - action was not performed */ }
            )
            return
        }

        speechOutput.speak(interpreted.spokenResponse) { executeInterpretedAction(interpreted) }
    }

    private fun executeInterpretedAction(interpreted: InterpretedCommand) {
        when (interpreted.action) {
            CommandActionType.READ_MORE -> readFullScreenText()
            CommandActionType.REPEAT_SUMMARY -> repeatLastSummary()
            CommandActionType.GO_BACK -> {
                actionExecutor.goBack()
                speechOutput.speak(getString(R.string.tts_navigated_back)) { beginListeningForCommand() }
            }
            CommandActionType.READ_HEADINGS -> readHeadings()
            CommandActionType.NAVIGATE_TO -> {
                val target = interpreted.targetDescription
                val moved = target != null && actionExecutor.navigateTo(target)
                if (!moved) speechOutput.speak(getString(R.string.tts_command_not_understood))
                beginListeningForCommand()
            }
            CommandActionType.ACTIVATE_ELEMENT -> {
                val target = interpreted.targetDescription
                val activated = target != null && actionExecutor.activateElement(target)
                if (!activated) speechOutput.speak(getString(R.string.tts_command_not_understood))
                // Activating an element likely navigates to a new window, which will re-trigger
                // handleForegroundWindowSettled() and its own listening prompt - don't double-listen.
            }
            CommandActionType.UNKNOWN -> {
                speechOutput.speak(getString(R.string.tts_command_not_understood)) { beginListeningForCommand() }
            }
        }
    }
}
