package com.klarl.accessibility.voice

/**
 * Surfaces "the microphone is currently listening" to the user, per the spec's requirement for
 * a clear visual/audio indication whenever the mic is active. Kept as an interface so
 * [AndroidSpeechRecognizerInput] doesn't hard-depend on notification/tone plumbing, and so tests
 * can assert start/stop calls without touching the Android framework.
 */
interface MicActivityIndicator {
    fun onListeningStarted()
    fun onListeningStopped()
}

/** No-op default used where no UI/notification surface is available (e.g. plain unit tests). */
object NoOpMicActivityIndicator : MicActivityIndicator {
    override fun onListeningStarted() {}
    override fun onListeningStopped() {}
}
