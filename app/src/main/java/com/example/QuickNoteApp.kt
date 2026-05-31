package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.example.data.PreferencesManager
import com.example.data.repository.NoteRepository
import com.example.receiver.ReminderBroadcastReceiver
import com.example.security.SecurityManager

class QuickNoteApp : Application() {

    lateinit var repository: NoteRepository
        private set

    lateinit var preferencesManager: PreferencesManager
        private set

    var securityWarningNeeded = false
        private set

    override fun onCreate() {
        super.onCreate()
        Log.d("QuickNoteApp", "Application starting...")

        // Obtain dynamic security assessment on startup
        val status = SecurityManager.performIntegrityCheck(this)
        if (!status.isSecure) {
            Log.e("QuickNoteApp", "Integrity warning triggered: Rooted=${status.isRooted}, SignatureValid=${status.isSignatureValid}")
            securityWarningNeeded = true
        }

        // Initialize Services
        repository = NoteRepository(this)
        preferencesManager = PreferencesManager(this)

        // Initialize System Notification Channel
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ReminderBroadcastReceiver.CHANNEL_ID,
                ReminderBroadcastReceiver.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies task reminders and location geofence alerts."
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
