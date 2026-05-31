package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.ThemeMode
import com.example.ui.navigation.Routes
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = applicationContext as QuickNoteApp
        val prefManager = app.preferencesManager

        setContent {
            // Read active preferences values
            val themeMode by prefManager.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            val isBiometricEnabled by prefManager.biometricLockFlow.collectAsState(initial = false)

            // Dynamic session unlock status
            var isUnlocked by rememberSaveable { mutableStateOf(false) }
            var showSecurityAlert by remember { mutableStateOf(app.securityWarningNeeded) }

            MyApplicationTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    if (showSecurityAlert) {
                        // Integrity/Tamper C++ security alert panel
                        AlertDialog(
                            onDismissRequest = {},
                            title = { Text("Secured Integrity Warning", color = MaterialTheme.colorScheme.error) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "Integrity validation has identified potential security risks (Signature mismatch or device behavior resembling Root permissions).",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text("To safeguard local notes, database keys, and documents, app operations have been locked down.")
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = { 
                                        showSecurityAlert = false // Allows bypass on sandbox test builds
                                    }
                                ) {
                                    Text("Bypass Verification (Preview Mode)")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { finish() }) {
                                    Text("Exit Safe")
                                }
                            }
                        )
                    } else if (isBiometricEnabled && !isUnlocked) {
                        BiometricLockScreen(
                            onUnlockSuccess = { isUnlocked = true }
                        )
                    } else {
                        // Main Navigation Graph
                        MainAppContent()
                    }
                }
            }
        }
    }
}

@Composable
fun MainAppContent() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.NOTES
    ) {
        // Dashboard Home page
        composable(Routes.NOTES) {
            NotesListScreen(
                folderIdFilter = null,
                onNavigateToEditor = { noteId ->
                    navController.navigate(Routes.buildEditorRoute(noteId))
                },
                onNavigateToFolders = {
                    navController.navigate(Routes.FOLDERS)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onNavigateToSearch = {
                    navController.navigate(Routes.SEARCH)
                }
            )
        }

        // Dashboard filtering specific notebooks / nested folders
        composable(
            route = "notes_folder/{folderId}",
            arguments = listOf(navArgument("folderId") { type = NavType.LongType })
        ) { backStackEntry ->
            val fid = backStackEntry.arguments?.getLong("folderId")
            NotesListScreen(
                folderIdFilter = fid,
                onNavigateToEditor = { noteId ->
                    navController.navigate(Routes.buildEditorRoute(noteId))
                },
                onNavigateToFolders = {
                    navController.navigate(Routes.FOLDERS)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onNavigateToSearch = {
                    navController.navigate(Routes.SEARCH)
                }
            )
        }

        // Folder hierarchy catalog view
        composable(Routes.FOLDERS) {
            FoldersScreen(
                onFolderSelected = { fid ->
                    navController.navigate("notes_folder/$fid")
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // Search panel screen
        composable(Routes.SEARCH) {
            SearchScreen(
                onNoteSelected = { noteId ->
                    navController.navigate(Routes.buildEditorRoute(noteId))
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // Settings config panel
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // Notepad Document Editor view screen
        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument("noteId") { type = NavType.LongType })
        ) { backStackEntry ->
            val nid = backStackEntry.arguments?.getLong("noteId") ?: 0L
            NoteEditorScreen(
                noteId = nid,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
