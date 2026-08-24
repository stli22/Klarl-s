package com.klarl.accessibility.voice

/** Abstraction over TTS so pipeline/confirmation logic can be unit tested without android.speech.tts. */
interface SpeechOutput {
    fun speak(text: String, onDone: (() -> Unit)? = null)
    fun stop()
    fun shutdown()
}
