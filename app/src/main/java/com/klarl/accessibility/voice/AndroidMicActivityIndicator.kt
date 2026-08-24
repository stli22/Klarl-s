package com.klarl.accessibility.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.klarl.accessibility.R

/**
 * Visual indication = a status-bar notification shown only while actively listening.
 * Audio indication = a short earcon tone at start/stop, via [ToneGenerator] (no extra permission
 * needed, unlike playing a bundled sound file).
 */
class AndroidMicActivityIndicator(private val context: Context) : MicActivityIndicator {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "klarl_mic_active"
        private const val NOTIFICATION_ID = 42
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val toneGenerator = runCatching {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, ToneGenerator.MAX_VOLUME / 2)
    }.getOrNull()

    override fun onListeningStarted() {
        playTone(ToneGenerator.TONE_PROP_BEEP)
        showNotification()
    }

    override fun onListeningStopped() {
        playTone(ToneGenerator.TONE_PROP_ACK)
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun playTone(tone: Int) {
        toneGenerator?.startTone(tone, 150)
    }

    private fun showNotification() {
        val hasPermission = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        val notification: Notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(context.getString(R.string.status_mic_indicator_label))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
