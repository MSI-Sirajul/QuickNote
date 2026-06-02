package com.msi.quicknote.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.msi.quicknote.MainActivity
import com.msi.quicknote.R

class ReminderBroadcastReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "quicknote_reminders_channel_v1"
        const val EXTRA_NOTE_ID = "note_id"
        const val EXTRA_NOTE_TITLE = "note_title"
        const val EXTRA_NOTE_CONTENT = "note_content"
        const val ACTION_SHOW_REMINDER = "com.msi.quicknote.ACTION_SHOW_REMINDER"

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = context.getString(R.string.notification_channel_name)
                val descriptionText = context.getString(R.string.notification_channel_desc)
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SHOW_REMINDER || intent.action == "android.intent.action.BOOT_COMPLETED") {
            val noteId = intent.getIntExtra(EXTRA_NOTE_ID, -1)
            val title = intent.getStringExtra(EXTRA_NOTE_TITLE) ?: "Task Reminder"
            val body = intent.getStringExtra(EXTRA_NOTE_CONTENT) ?: "You have a reminder scheduled for this note."

            if (noteId == -1) return

            // Create notification channel
            createNotificationChannel(context)

            // Pending intent to open NoteEditor when clicked
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(EXTRA_NOTE_ID, noteId)
            }

            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                noteId, // unique request code
                openAppIntent,
                pendingIntentFlags
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder) // Fallback standard icon
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(noteId, builder.build())
        }
    }
}
