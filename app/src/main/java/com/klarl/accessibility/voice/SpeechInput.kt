package com.klarl.accessibility.voice

/** Abstraction over speech-to-text so pipeline logic can be unit tested without android.speech. */
interface SpeechInput {
    fun startListening(onResult: (String) -> Unit, onError: (() -> Unit)? = null)
    fun stopListening()
    fun destroy()
}
