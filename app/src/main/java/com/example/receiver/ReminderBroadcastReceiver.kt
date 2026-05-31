package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class ReminderBroadcastReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "quicknote_reminders"
        const val CHANNEL_NAME = "QuickNote Reminders"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getLongExtra("note_id", 0L)
        val noteTitle = intent.getStringExtra("note_title") ?: "Note Reminder"
        val noteContent = intent.getStringExtra("note_content") ?: "Remember to check your note!"
        val lat = intent.getDoubleExtra("latitude", 0.0)
        val isGeofence = intent.getBooleanExtra("is_geofence", false)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create Channel on newer versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Triggers task reminders and geofence alerts."
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Open app on click
        val clickIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("open_note_id", noteId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            noteId.toInt(),
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val titleText = if (isGeofence) "QuickNote Geofence: Near Location!" else "QuickNote Reminder"
        val bodyText = if (isGeofence) "You entered the perimeter for note: \"$noteTitle\"" else "$noteTitle: $noteContent"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(titleText)
            .setContentText(bodyText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(noteId.toInt(), notification)
    }
}
