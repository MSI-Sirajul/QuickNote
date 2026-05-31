package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import kotlinx.serialization.Serializable

@Entity(tableName = "folders")
@Serializable
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val color: Int, // Hex ARGB color
    val iconName: String = "Folder"
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
    indices = [Index("folderId")]
)
@Serializable
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String, // Can be regular text or serialized List<EditorBlock>
    val folderId: Int? = null,
    val isPinned: Boolean = false,
    val color: Int = 0xFFFFFFFF.toInt(), // Card background color
    val isLocked: Boolean = false,
    val reminderTime: Long? = null,
    val drawingData: String? = null, // Coordinate points serialized to JSON
    val noteType: String = "TEXT", // "TEXT", "MARKDOWN", "SKETCH"
    val lastModified: Long = System.currentTimeMillis()
)

@Serializable
data class EditorBlock(
    val id: String,
    val type: String, // "paragraph", "heading", "bullet", "todo"
    val text: String,
    val isChecked: Boolean = false
)
