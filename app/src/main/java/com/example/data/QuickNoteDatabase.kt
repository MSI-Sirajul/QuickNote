package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Folder::class,
        Note::class,
        Tag::class,
        NoteTagCrossRef::class,
        Reminder::class,
        VersionSnapshot::class
    ],
    version = 1,
    exportSchema = false
)
abstract class QuickNoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao
    abstract fun tagDao(): TagDao
    abstract fun reminderDao(): ReminderDao
    abstract fun versionSnapshotDao(): VersionSnapshotDao

    companion object {
        @Volatile
        private var INSTANCE: QuickNoteDatabase? = null

        fun getDatabase(context: Context): QuickNoteDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    QuickNoteDatabase::class.java,
                    "quicknote_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
