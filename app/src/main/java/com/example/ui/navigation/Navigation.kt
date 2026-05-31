package com.example.ui.navigation

object Navigation {
    const val ROUTE_NOTES_LIST = "notes_list"
    const val ROUTE_NOTE_EDITOR = "note_editor/{noteId}"
    const val ROUTE_FOLDERS = "folders"
    const val ROUTE_SEARCH = "search"
    const val ROUTE_SETTINGS = "settings"

    fun noteEditorPath(noteId: Int): String {
        return "note_editor/$noteId"
    }
}
