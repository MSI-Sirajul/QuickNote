package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY isPinned DESC, lastEdited DESC")
    fun getAllNotesFlow(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Long): Note?

    @Query("SELECT * FROM notes WHERE id = :id")
    fun getNoteByIdFlow(id: Long): Flow<Note?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Update
    suspend fun updateNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("SELECT * FROM notes WHERE folderId = :folderId ORDER BY isPinned DESC, lastEdited DESC")
    fun getNotesInFolderFlow(folderId: Long): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE folderId IS NULL ORDER BY isPinned DESC, lastEdited DESC")
    fun getRootNotesFlow(): Flow<List<Note>>

    // Flexible multi-filter query supporting OCR searches, colored lists, ranges & tagging
    @Query("""
        SELECT DISTINCT n.* FROM notes n
        LEFT JOIN note_tag_joins j ON n.id = j.noteId
        WHERE (:searchQuery IS NULL OR n.title LIKE :searchQuery OR n.content LIKE :searchQuery OR n.ocrText LIKE :searchQuery)
          AND (:folderId IS NULL OR n.folderId = :folderId)
          AND (:colorHex IS NULL OR n.colorHex = :colorHex)
          AND (:startDate IS NULL OR n.lastEdited >= :startDate)
          AND (:endDate IS NULL OR n.lastEdited <= :endDate)
          AND (:tagId IS NULL OR j.tagId = :tagId)
        ORDER BY n.isPinned DESC, n.lastEdited DESC
    """)
    fun filterNotesFlow(
        searchQuery: String?,
        folderId: Long?,
        colorHex: String?,
        startDate: Long?,
        endDate: Long?,
        tagId: Long?
    ): Flow<List<Note>>
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun getAllFoldersFlow(): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolderById(id: Long): Folder?

    @Query("SELECT * FROM folders WHERE parentId = :parentId ORDER BY name ASC")
    fun getSubfoldersFlow(parentId: Long): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE parentId IS NULL ORDER BY name ASC")
    fun getRootFoldersFlow(): Flow<List<Folder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: Folder): Long

    @Update
    suspend fun updateFolder(folder: Folder)

    @Delete
    suspend fun deleteFolder(folder: Folder)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun getAllTagsFlow(): Flow<List<Tag>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: Tag): Long

    @Delete
    suspend fun deleteTag(tag: Tag)

    // Note Tag junctions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNoteTagCrossRef(crossRef: NoteTagCrossRef)

    @Query("DELETE FROM note_tag_joins WHERE noteId = :noteId AND tagId = :tagId")
    suspend fun deleteNoteTagCrossRef(noteId: Long, tagId: Long)

    @Query("DELETE FROM note_tag_joins WHERE noteId = :noteId")
    suspend fun clearTagsForNote(noteId: Long)

    @Query("""
        SELECT t.* FROM tags t
        INNER JOIN note_tag_joins j ON t.id = j.tagId
        WHERE j.noteId = :noteId
    """)
    fun getTagsForNoteFlow(noteId: Long): Flow<List<Tag>>

    @Query("""
        SELECT t.* FROM tags t
        INNER JOIN note_tag_joins j ON t.id = j.tagId
        WHERE j.noteId = :noteId
    """)
    suspend fun getTagsForNote(noteId: Long): List<Tag>
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE isTriggered = 0 ORDER BY triggerTime ASC")
    fun getActiveRemindersFlow(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE noteId = :noteId")
    fun getRemindersForNoteFlow(noteId: Long): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE noteId = :noteId")
    suspend fun getRemindersForNote(noteId: Long): List<Reminder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: Reminder): Long

    @Delete
    suspend fun deleteReminder(reminder: Reminder)

    @Query("UPDATE reminders SET isTriggered = 1 WHERE id = :id")
    suspend fun markAsTriggered(id: Long)
}

@Dao
interface VersionSnapshotDao {
    @Query("SELECT * FROM version_snapshots WHERE noteId = :noteId ORDER BY timestamp DESC")
    fun getSnapshotsForNoteFlow(noteId: Long): Flow<List<VersionSnapshot>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: VersionSnapshot): Long

    @Query("DELETE FROM version_snapshots WHERE noteId = :noteId")
    suspend fun clearSnapshotsForNote(noteId: Long)
}
