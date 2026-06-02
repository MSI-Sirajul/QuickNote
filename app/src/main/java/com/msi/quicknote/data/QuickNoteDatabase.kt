package com.msi.quicknote.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Folder::class, Note::class], version = 2, exportSchema = false)
abstract class QuickNoteDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun noteDao(): NoteDao

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
