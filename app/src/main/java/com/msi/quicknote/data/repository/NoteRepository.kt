package com.msi.quicknote.data.repository

import com.msi.quicknote.data.Folder
import com.msi.quicknote.data.FolderDao
import com.msi.quicknote.data.Note
import com.msi.quicknote.data.NoteDao
import kotlinx.coroutines.flow.Flow

class NoteRepository(
    private val folderDao: FolderDao,
    private val noteDao: NoteDao
) {
    val allNotes: Flow<List<Note>> = noteDao.getAllNotes()
    val allFolders: Flow<List<Folder>> = folderDao.getAllFolders()

    fun getNotesByFolder(folderId: Int): Flow<List<Note>> = noteDao.getNotesByFolder(folderId)

    suspend fun getNoteById(id: Int): Note? = noteDao.getNoteById(id)

    suspend fun getFolderById(id: Int): Folder? = folderDao.getFolderById(id)

    suspend fun insertFolder(folder: Folder): Long = folderDao.insertFolder(folder)

    suspend fun updateFolder(folder: Folder) = folderDao.updateFolder(folder)

    suspend fun deleteFolder(folder: Folder) = folderDao.deleteFolder(folder)

    suspend fun insertNote(note: Note): Long = noteDao.insertNote(note)

    suspend fun updateNote(note: Note) = noteDao.updateNote(note)

    suspend fun deleteNote(note: Note) = noteDao.deleteNote(note)

    fun searchNotes(query: String): Flow<List<Note>> = noteDao.searchNotes(query)
}
