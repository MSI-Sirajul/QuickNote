package com.example.ui.navigation

object Routes {
    const val NOTES = "notes"
    const val FOLDERS = "folders"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    
    // Notes editor route with custom parameter
    const val EDITOR = "editor/{noteId}"
    fun buildEditorRoute(noteId: Long): String {
        return "editor/$noteId"
    }
}
