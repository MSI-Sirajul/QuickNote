package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "folders",
    foreignKeys = [
        ForeignKey(
            entity = Folder::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["parentId"])]
)
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long? = null, // Nested folder support; null means root folder
    val colorHex: String = "#FF6200EE" // Cover color
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = Folder::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["folderId"])]
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String, // Clean raw or structured text
    val folderId: Long? = null,
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val colorHex: String = "#FFFFFF", // Note accent background color card 
    val timestamp: Long = System.currentTimeMillis(),
    val lastEdited: Long = System.currentTimeMillis(),
    val ocrText: String? = null, // OCR text indexed for search
    val imagePaths: String = "", // Comma-separated paths of attached images
    val audioPath: String? = null, // Voice note clip path
    val fileAttachments: String = "" // Comma-separated PDF/TXT/MD attached paths
)

@Entity(tableName = "tags")
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String = "#FF03DAC5"
)

@Entity(
    tableName = "note_tag_joins",
    primaryKeys = ["noteId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = Note::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["noteId"]),
        Index(value = ["tagId"])
    ]
)
data class NoteTagCrossRef(
    val noteId: Long,
    val tagId: Long
)

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val triggerTime: Long, // Epoch timestamp for AlarmManager
    val isRecurring: Boolean = false,
    val isLocationBased: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radius: Float? = null, // Metres
    val locationName: String? = null,
    val isTriggered: Boolean = false
)

@Entity(
    tableName = "version_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = Note::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["noteId"])]
)
data class VersionSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
