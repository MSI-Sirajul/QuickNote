package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.receiver.ReminderBroadcastReceiver
import com.example.ui.navigation.Navigation
import com.example.ui.screens.*
import com.example.ui.theme.QuickNoteTheme

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()

        // Check if opened via notification click
        val notificationNoteId = intent.getIntExtra(ReminderBroadcastReceiver.EXTRA_NOTE_ID, -1)

        setContent {
            val isDarkTheme = remember { mutableStateOf(false) }
            val app = applicationContext as QuickNoteApp
            val prefs = app.preferencesManager

            // Check theme settings
            fun updateTheme() {
                isDarkTheme.value = when (prefs.theme) {
                    "Dark" -> true
                    "Light" -> false
                    else -> true // default to dark cosmic slate
                }
            }

            LaunchedEffect(Unit) {
                updateTheme()
            }

            QuickNoteTheme(darkTheme = isDarkTheme.value) {
                val navController = rememberNavController()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Navigation.ROUTE_NOTES_LIST,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Navigation.ROUTE_NOTES_LIST) {
                            NotesListScreen(
                                onNavigateToEditor = { id ->
                                    navController.navigate(Navigation.noteEditorPath(id))
                                },
                                onNavigateToFolders = {
                                    navController.navigate(Navigation.ROUTE_FOLDERS)
                                },
                                onNavigateToSearch = {
                                    navController.navigate(Navigation.ROUTE_SEARCH)
                                },
                                onNavigateToSettings = {
                                    navController.navigate(Navigation.ROUTE_SETTINGS)
                                }
                            )
                        }

                        composable(
                            route = Navigation.ROUTE_NOTE_EDITOR,
                            arguments = listOf(navArgument("noteId") { type = NavType.IntType })
                        ) { backStackEntry ->
                            val noteId = backStackEntry.arguments?.getInt("noteId") ?: -1
                            NoteEditorScreen(
                                noteId = noteId,
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable(Navigation.ROUTE_FOLDERS) {
                            FoldersScreen(
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable(Navigation.ROUTE_SEARCH) {
                            SearchScreen(
                                onNoteClick = { note ->
                                    navController.navigate(Navigation.noteEditorPath(note.id))
                                },
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable(Navigation.ROUTE_SETTINGS) {
                            SettingsScreen(
                                onBack = {
                                    navController.popBackStack()
                                },
                                onThemeChanged = {
                                    updateTheme()
                                }
                            )
                        }
                    }

                    // Direct routing if opened from custom notification
                    LaunchedEffect(notificationNoteId) {
                        if (notificationNoteId != -1) {
                            navController.navigate(Navigation.noteEditorPath(notificationNoteId))
                        }
                    }
                }
            }
        }
    }
}
