package com.klarl.accessibility.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * Wraps [SpeechRecognizer]. Must be constructed and used on the main thread (a framework
 * requirement of SpeechRecognizer itself). Reports listening start/stop to [micIndicator] so the
 * "microphone is active" indication in the spec is never left to individual call sites to remember.
 */
class AndroidSpeechRecognizerInput(
    private val context: Context,
    private val micIndicator: MicActivityIndicator
) : SpeechInput {

    private companion object {
        const val TAG = "KlarlSpeechInput"
    }

    private var recognizer: SpeechRecognizer? = null

    override fun startListening(onResult: (String) -> Unit, onError: (() -> Unit)?) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "Ingen taligenkänning tillgänglig på enheten")
            onError?.invoke()
            return
        }
        stopListening()
        val newRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = newRecognizer

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.forLanguageTag("sv-SE").toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        newRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                micIndicator.onListeningStarted()
            }

            override fun onResults(results: Bundle) {
                micIndicator.onListeningStopped()
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (text.isNullOrBlank()) onError?.invoke() else onResult(text)
            }

            override fun onError(error: Int) {
                micIndicator.onListeningStopped()
                onError?.invoke()
            }

            override fun onEndOfSpeech() {
                micIndicator.onListeningStopped()
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        newRecognizer.startListening(intent)
    }

    override fun stopListening() {
        recognizer?.let {
            it.stopListening()
            it.destroy()
        }
        recognizer = null
        micIndicator.onListeningStopped()
    }

    override fun destroy() = stopListening()
}
