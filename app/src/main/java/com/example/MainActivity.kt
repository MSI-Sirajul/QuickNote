package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
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
        
        // Let application control drawing directly under system bars (EdgeToEdge)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val notificationNoteId = intent.getIntExtra(ReminderBroadcastReceiver.EXTRA_NOTE_ID, -1)

        setContent {
            val isDarkTheme = remember { mutableStateOf(false) }
            val app = applicationContext as QuickNoteApp
            val prefs = app.preferencesManager

            fun updateTheme() {
                isDarkTheme.value = when (prefs.theme) {
                    "Dark" -> true
                    "Light" -> false
                    else -> true // default to cosmic dark
                }
            }

            LaunchedEffect(Unit) {
                updateTheme()
            }

            QuickNoteTheme(darkTheme = isDarkTheme.value) {
                val navController = rememberNavController()

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) { innerPadding ->
                    val configuration = LocalConfiguration.current
                    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    val isTablet = configuration.screenWidthDp >= 600
                    val useTwoPane = isLandscape && isTablet

                    NavHost(
                        navController = navController,
                        startDestination = Navigation.ROUTE_NOTES_LIST,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Navigation.ROUTE_NOTES_LIST) {
                            if (useTwoPane) {
                                // Double Pane Tablet Master Detail View
                                var selectedTabletNoteId by remember { mutableStateOf<Int?>(null) }
                                var splitterWidth by remember { mutableStateOf(360.dp) }
                                val density = LocalDensity.current

                                Row(modifier = Modifier.fillMaxSize()) {
                                    // Left Master Layout
                                    Box(modifier = Modifier.width(splitterWidth)) {
                                        NotesListScreen(
                                            onNavigateToEditor = { id ->
                                                selectedTabletNoteId = id
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

                                    // Draggable Splitter handle
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(8.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant)
                                            .pointerInput(Unit) {
                                                detectDragGestures { change, dragAmount ->
                                                    change.consume()
                                                    val deltaDp = with(density) { dragAmount.x.toDp() }
                                                    splitterWidth = (splitterWidth + deltaDp).coerceIn(280.dp, 600.dp)
                                                }
                                            }
                                    )

                                    // Right Detailed Editor Layout
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (selectedTabletNoteId != null && selectedTabletNoteId != -1) {
                                            NoteEditorScreen(
                                                noteId = selectedTabletNoteId!!,
                                                onBack = {
                                                    selectedTabletNoteId = null
                                                }
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(MaterialTheme.colorScheme.background),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Select a note from the left to start editing, or tap '+' to create a new one.",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                    modifier = Modifier.padding(24.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Single Pane Mobile view
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
                                    if (useTwoPane) {
                                        // On wide screens we just jump back and show it in the detail master view
                                        navController.popBackStack()
                                    } else {
                                        navController.navigate(Navigation.noteEditorPath(note.id))
                                    }
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

                    // Handles Notification routing if app launches via Alarm intent action
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
