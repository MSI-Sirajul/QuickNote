package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class NoteRepository(private val context: Context) {

    private val db = QuickNoteDatabase.getDatabase(context)
    private val noteDao = db.noteDao()
    private val folderDao = db.folderDao()
    private val tagDao = db.tagDao()
    private val reminderDao = db.reminderDao()
    private val versionSnapshotDao = db.versionSnapshotDao()

    // -------------------------------------------------------------
    // Note Operations
    // -------------------------------------------------------------
    fun getAllNotes(): Flow<List<Note>> = noteDao.getAllNotesFlow()

    fun getNotesInFolder(folderId: Long): Flow<List<Note>> = noteDao.getNotesInFolderFlow(folderId)

    fun getRootNotes(): Flow<List<Note>> = noteDao.getRootNotesFlow()

    fun getNoteByIdFlow(id: Long): Flow<Note?> = noteDao.getNoteByIdFlow(id)

    suspend fun getNoteById(id: Long): Note? = withContext(Dispatchers.IO) {
        noteDao.getNoteById(id)
    }

    suspend fun insertOrUpdateNote(note: Note, createSnapshot: Boolean = true): Long = withContext(Dispatchers.IO) {
        val noteId = if (note.id == 0L) {
            val id = noteDao.insertNote(note)
            if (createSnapshot) {
                versionSnapshotDao.insertSnapshot(
                    VersionSnapshot(noteId = id, title = note.title, content = note.content)
                )
            }
            id
        } else {
            // Fetch old note to snapshot if editing matches
            if (createSnapshot) {
                noteDao.getNoteById(note.id)?.let { oldNote ->
                    if (oldNote.title != note.title || oldNote.content != note.content) {
                        versionSnapshotDao.insertSnapshot(
                            VersionSnapshot(noteId = note.id, title = oldNote.title, content = oldNote.content)
                        )
                    }
                }
            }
            noteDao.insertNote(note)
        }
        noteId
    }

    suspend fun deleteNote(note: Note) = withContext(Dispatchers.IO) {
        // Clear snapshots and tag relations first
        versionSnapshotDao.clearSnapshotsForNote(note.id)
        tagDao.clearTagsForNote(note.id)
        // Delete reminder alarms
        val alarms = reminderDao.getRemindersForNote(note.id)
        for (alarm in alarms) {
            reminderDao.deleteReminder(alarm)
        }
        noteDao.deleteNote(note)
    }

    fun filterNotes(
        query: String?,
        folderId: Long?,
        colorHex: String?,
        startDate: Long?,
        endDate: Long?,
        tagId: Long?
    ): Flow<List<Note>> {
        val wildcardQuery = if (query.isNullOrEmpty()) null else "%$query%"
        return noteDao.filterNotesFlow(wildcardQuery, folderId, colorHex, startDate, endDate, tagId)
    }

    // -------------------------------------------------------------
    // Folder Operations
    // -------------------------------------------------------------
    fun getAllFolders(): Flow<List<Folder>> = folderDao.getAllFoldersFlow()

    fun getRootFolders(): Flow<List<Folder>> = folderDao.getRootFoldersFlow()

    fun getSubfolders(parentId: Long): Flow<List<Folder>> = folderDao.getSubfoldersFlow(parentId)

    suspend fun insertFolder(folder: Folder): Long = withContext(Dispatchers.IO) {
        folderDao.insertFolder(folder)
    }

    suspend fun updateFolder(folder: Folder) = withContext(Dispatchers.IO) {
        folderDao.updateFolder(folder)
    }

    suspend fun deleteFolder(folder: Folder) = withContext(Dispatchers.IO) {
        folderDao.deleteFolder(folder)
    }

    suspend fun getFolderById(id: Long): Folder? = withContext(Dispatchers.IO) {
        folderDao.getFolderById(id)
    }

    // -------------------------------------------------------------
    // Tag Operations
    // -------------------------------------------------------------
    fun getAllTags(): Flow<List<Tag>> = tagDao.getAllTagsFlow()

    suspend fun insertTag(tag: Tag): Long = withContext(Dispatchers.IO) {
        tagDao.insertTag(tag)
    }

    suspend fun deleteTag(tag: Tag) = withContext(Dispatchers.IO) {
        tagDao.deleteTag(tag)
    }

    suspend fun addTagToNote(noteId: Long, tagId: Long) = withContext(Dispatchers.IO) {
        tagDao.insertNoteTagCrossRef(NoteTagCrossRef(noteId, tagId))
    }

    suspend fun removeTagFromNote(noteId: Long, tagId: Long) = withContext(Dispatchers.IO) {
        tagDao.deleteNoteTagCrossRef(noteId, tagId)
    }

    fun getTagsForNoteFlow(noteId: Long): Flow<List<Tag>> {
        return tagDao.getTagsForNoteFlow(noteId)
    }

    suspend fun getTagsForNote(noteId: Long): List<Tag> = withContext(Dispatchers.IO) {
        tagDao.getTagsForNote(noteId)
    }

    // -------------------------------------------------------------
    // Reminder Operations
    // -------------------------------------------------------------
    fun getActiveReminders(): Flow<List<Reminder>> = reminderDao.getActiveRemindersFlow()

    fun getRemindersForNoteFlow(noteId: Long): Flow<List<Reminder>> = reminderDao.getRemindersForNoteFlow(noteId)

    suspend fun insertReminder(reminder: Reminder): Long = withContext(Dispatchers.IO) {
        reminderDao.insertReminder(reminder)
    }

    suspend fun deleteReminder(reminder: Reminder) = withContext(Dispatchers.IO) {
        reminderDao.deleteReminder(reminder)
    }

    suspend fun markReminderAsTriggered(id: Long) = withContext(Dispatchers.IO) {
        reminderDao.markAsTriggered(id)
    }

    // -------------------------------------------------------------
    // Snapshots Operations
    // -------------------------------------------------------------
    fun getSnapshotsForNote(noteId: Long): Flow<List<VersionSnapshot>> {
        return versionSnapshotDao.getSnapshotsForNoteFlow(noteId)
    }

    // -------------------------------------------------------------
    // Extractor (Simulated OCR offline)
    // -------------------------------------------------------------
    suspend fun runOcrOnNoteImages(note: Note): Note = withContext(Dispatchers.IO) {
        if (note.imagePaths.isEmpty()) return@withContext note

        // Simulated OCR: extracts strings from named labels of attached image files
        val words = note.imagePaths.split(",")
            .map { it.substringAfterLast("/").substringBeforeLast(".") }
            .filter { it.isNotEmpty() }
            .joinToString(" ") { it.replace("_", " ") }

        val ocrResult = "Extracted Image Text Content: [ $words ]"
        val updatedNote = note.copy(ocrText = ocrResult, lastEdited = System.currentTimeMillis())
        noteDao.insertNote(updatedNote)
        updatedNote
    }

    // -------------------------------------------------------------
    // Single Note Document Export
    // -------------------------------------------------------------
    suspend fun exportNoteAsFile(note: Note, format: String): File = withContext(Dispatchers.IO) {
        val ext = when (format.lowercase()) {
            "markdown" -> "md"
            "pdf" -> "pdf"
            else -> "txt"
        }
        val file = File(context.cacheDir, "${note.title.ifBlank { "Untitled" }}.$ext")
        FileWriter(file).use { writer ->
            when (ext) {
                "md" -> {
                    writer.write("# ${note.title}\n\n")
                    writer.write("Last edited: ${java.text.SimpleDateFormat.getInstance().format(note.lastEdited)}\n\n")
                    writer.write(note.content)
                }
                "pdf" -> {
                    // Simulating a clean PDF print (a text document with signature blocks)
                    writer.write("%PDF-1.4\n")
                    writer.write("%% Title: ${note.title}\n")
                    writer.write("%% Body:\n${note.content}\n")
                    writer.write("%%EOF")
                }
                else -> {
                    writer.write("TITLE: ${note.title}\n")
                    writer.write("MODIFIED: ${java.text.SimpleDateFormat.getInstance().format(note.lastEdited)}\n\n")
                    writer.write(note.content)
                }
            }
        }
        file
    }

    // -------------------------------------------------------------
    // Full Application Backup & Restore (ZIP File Storage)
    // -------------------------------------------------------------
    suspend fun exportBackup(outputStream: OutputStream): Boolean = withContext(Dispatchers.IO) {
        try {
            val zipOut = ZipOutputStream(BufferedOutputStream(outputStream))

            // Obtain all database content to format as structured JSON
            val allNotes = db.noteDao().getAllNotesFlow() // Flow needs to be extracted
            // Wait, we can run direct synchronous SQL query to load it for the exporter!
            val rawNotes = db.query("SELECT * FROM notes", null)
            val notesList = mutableListOf<Map<String, Any?>>()
            while (rawNotes.moveToNext()) {
                val row = mapOf(
                    "id" to rawNotes.getLong(rawNotes.getColumnIndexOrThrow("id")),
                    "title" to rawNotes.getString(rawNotes.getColumnIndexOrThrow("title")),
                    "content" to rawNotes.getString(rawNotes.getColumnIndexOrThrow("content")),
                    "folderId" to if (rawNotes.isNull(rawNotes.getColumnIndexOrThrow("folderId"))) null else rawNotes.getLong(rawNotes.getColumnIndexOrThrow("folderId")),
                    "isPinned" to (rawNotes.getInt(rawNotes.getColumnIndexOrThrow("isPinned")) == 1),
                    "isFavorite" to (rawNotes.getInt(rawNotes.getColumnIndexOrThrow("isFavorite")) == 1),
                    "colorHex" to rawNotes.getString(rawNotes.getColumnIndexOrThrow("colorHex")),
                    "timestamp" to rawNotes.getLong(rawNotes.getColumnIndexOrThrow("timestamp")),
                    "lastEdited" to rawNotes.getLong(rawNotes.getColumnIndexOrThrow("lastEdited")),
                    "ocrText" to rawNotes.getString(rawNotes.getColumnIndexOrThrow("ocrText"))
                )
                notesList.add(row)
            }
            rawNotes.close()

            val rawFolders = db.query("SELECT * FROM folders", null)
            val foldersList = mutableListOf<Map<String, Any?>>()
            while (rawFolders.moveToNext()) {
                val row = mapOf(
                    "id" to rawFolders.getLong(rawFolders.getColumnIndexOrThrow("id")),
                    "name" to rawFolders.getString(rawFolders.getColumnIndexOrThrow("name")),
                    "parentId" to if (rawFolders.isNull(rawFolders.getColumnIndexOrThrow("parentId"))) null else rawFolders.getLong(rawFolders.getColumnIndexOrThrow("parentId")),
                    "colorHex" to rawFolders.getString(rawFolders.getColumnIndexOrThrow("colorHex"))
                )
                foldersList.add(row)
            }
            rawFolders.close()

            val rawTags = db.query("SELECT * FROM tags", null)
            val tagsList = mutableListOf<Map<String, Any?>>()
            while (rawTags.moveToNext()) {
                val row = mapOf(
                    "id" to rawTags.getLong(rawTags.getColumnIndexOrThrow("id")),
                    "name" to rawTags.getString(rawTags.getColumnIndexOrThrow("name")),
                    "colorHex" to rawTags.getString(rawTags.getColumnIndexOrThrow("colorHex"))
                )
                tagsList.add(row)
            }
            rawTags.close()

            val jsonBackup = JSONObject().apply {
                put("notes", JSONArray(notesList))
                put("folders", JSONArray(foldersList))
                put("tags", JSONArray(tagsList))
            }

            // Write database backup Entry
            zipOut.putNextEntry(ZipEntry("backup_data.json"))
            zipOut.write(jsonBackup.toString(2).toByteArray())
            zipOut.closeEntry()

            // Compress media attachments
            val attachmentsDir = File(context.filesDir, "attachments")
            if (attachmentsDir.exists()) {
                attachmentsDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        zipOut.putNextEntry(ZipEntry("attachments/${file.name}"))
                        val buffer = ByteArray(4096)
                        BufferedInputStream(FileInputStream(file)).use { bis ->
                            var read: Int
                            while (bis.read(buffer).also { read = it } != -1) {
                                zipOut.write(buffer, 0, read)
                            }
                        }
                        zipOut.closeEntry()
                    }
                }
            }

            zipOut.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun restoreBackup(inputStream: InputStream): Boolean = withContext(Dispatchers.IO) {
        try {
            val zipIn = ZipInputStream(BufferedInputStream(inputStream))
            var entry: ZipEntry? = zipIn.nextEntry
            var jsonString: String? = null

            val attachmentsDir = File(context.filesDir, "attachments").apply { mkdirs() }

            while (entry != null) {
                if (entry.name == "backup_data.json") {
                    val bytes = zipIn.readBytes()
                    jsonString = String(bytes)
                } else if (entry.name.startsWith("attachments/")) {
                    val file = File(attachmentsDir, entry.name.substringAfter("attachments/"))
                    FileOutputStream(file).use { fos ->
                        zipIn.copyTo(fos)
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
            zipIn.close()

            if (jsonString != null) {
                val backupObj = JSONObject(jsonString)
                val foldersArray = backupObj.getJSONArray("folders")
                val tagsArray = backupObj.getJSONArray("tags")
                val notesArray = backupObj.getJSONArray("notes")

                // Wipe existing data safely within a Transaction
                db.runInTransaction {
                    // Simple truncates
                    db.compileStatement("DELETE FROM note_tag_joins").execute()
                    db.compileStatement("DELETE FROM reminders").execute()
                    db.compileStatement("DELETE FROM version_snapshots").execute()
                    db.compileStatement("DELETE FROM notes").execute()
                    db.compileStatement("DELETE FROM folders").execute()
                    db.compileStatement("DELETE FROM tags").execute()

                    // Restore Folders
                    for (i in 0 until foldersArray.length()) {
                        val folderObj = foldersArray.getJSONObject(i)
                        db.compileStatement("""
                            INSERT OR REPLACE INTO folders (id, name, parentId, colorHex)
                            VALUES (${folderObj.getLong("id")}, ?, ${if (folderObj.isNull("parentId")) "NULL" else folderObj.getLong("parentId")}, ?)
                        """).run {
                            bindString(1, folderObj.getString("name"))
                            bindString(2, folderObj.getString("colorHex"))
                            executeInsert()
                        }
                    }

                    // Restore Tags
                    for (i in 0 until tagsArray.length()) {
                        val tagObj = tagsArray.getJSONObject(i)
                        db.compileStatement("""
                            INSERT OR REPLACE INTO tags (id, name, colorHex)
                            VALUES (${tagObj.getLong("id")}, ?, ?)
                        """).run {
                            bindString(1, tagObj.getString("name"))
                            bindString(2, tagObj.getString("colorHex"))
                            executeInsert()
                        }
                    }

                    // Restore Notes
                    for (i in 0 until notesArray.length()) {
                        val noteObj = notesArray.getJSONObject(i)
                        val folderIdVal = if (noteObj.isNull("folderId")) "NULL" else noteObj.getLong("folderId").toString()
                        db.compileStatement("""
                            INSERT OR REPLACE INTO notes (id, title, content, folderId, isPinned, isFavorite, colorHex, timestamp, lastEdited, ocrText)
                            VALUES (${noteObj.getLong("id")}, ?, ?, $folderIdVal, ${if (noteObj.getBoolean("isPinned")) 1 else 0}, ${if (noteObj.getBoolean("isFavorite")) 1 else 0}, ?, ${noteObj.getLong("timestamp")}, ${noteObj.getLong("lastEdited")}, ?)
                        """).run {
                            bindString(1, noteObj.getString("title"))
                            bindString(2, noteObj.getString("content"))
                            bindString(3, noteObj.getString("colorHex"))
                            if (noteObj.isNull("ocrText")) bindNull(4) else bindString(4, noteObj.getString("ocrText"))
                            executeInsert()
                        }
                    }
                }
                return@withContext true
            }
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
