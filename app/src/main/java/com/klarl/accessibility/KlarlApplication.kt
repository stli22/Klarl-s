package com.klarl.accessibility

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.klarl.accessibility.voice.AndroidMicActivityIndicator

class KlarlApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        createMicActivityNotificationChannel()
    }

    private fun createMicActivityNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            AndroidMicActivityIndicator.NOTIFICATION_CHANNEL_ID,
            getString(R.string.status_mic_indicator_label),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
