package com.klarl.accessibility.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID

/** Wraps [TextToSpeech], defaulting to Swedish (this app's target users, per the spec). */
class AndroidTextToSpeechOutput(context: Context) : SpeechOutput {

    private companion object {
        const val TAG = "KlarlTts"
    }

    private var ready = false
    private val pendingOnInit = mutableListOf<() -> Unit>()

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            val result = tts_setSwedishLocale()
            if (!result) Log.w(TAG, "Svenska röststöd saknas, faller tillbaka på enhetens standardspråk")
        } else {
            Log.e(TAG, "TextToSpeech-initiering misslyckades, status=$status")
        }
        pendingOnInit.forEach { it() }
        pendingOnInit.clear()
    }

    // Named oddly to make it obvious this touches the lateinit `tts` field during its own
    // constructor callback - kept as a function rather than inlined for readability.
    private fun tts_setSwedishLocale(): Boolean {
        val result = tts.setLanguage(Locale.forLanguageTag("sv-SE"))
        return result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    override fun speak(text: String, onDone: (() -> Unit)?) {
        val utteranceId = UUID.randomUUID().toString()
        if (onDone != null) {
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) = onDone()
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = onDone()
            })
        }
        val speakNow = { tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle.EMPTY, utteranceId) }
        if (ready) speakNow() else pendingOnInit.add(speakNow)
    }

    override fun stop() {
        tts.stop()
    }

    override fun shutdown() {
        tts.shutdown()
    }
}
