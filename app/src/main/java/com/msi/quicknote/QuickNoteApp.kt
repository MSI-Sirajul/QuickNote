package com.msi.quicknote

import android.app.Application
import com.msi.quicknote.data.PreferencesManager
import com.msi.quicknote.data.QuickNoteDatabase
import com.msi.quicknote.data.repository.NoteRepository
import com.msi.quicknote.receiver.ReminderBroadcastReceiver
import com.msi.quicknote.security.SecurityManager

class QuickNoteApp : Application() {

    lateinit var database: QuickNoteDatabase
    lateinit var repository: NoteRepository
    lateinit var preferencesManager: PreferencesManager
    lateinit var securityManager: SecurityManager

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Core Services
        database = QuickNoteDatabase.getDatabase(this)
        repository = NoteRepository(database.folderDao(), database.noteDao())
        preferencesManager = PreferencesManager(this)
        securityManager = SecurityManager(this)

        // Create notification channel
        ReminderBroadcastReceiver.createNotificationChannel(this)
    }
}
