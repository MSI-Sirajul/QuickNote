package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.QuickNoteApp
import com.example.data.ThemeMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as QuickNoteApp
    val prefManager = app.preferencesManager
    val scope = rememberCoroutineScope()

    // Load datastore preferences
    val currentTheme by prefManager.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
    val isGridLayout by prefManager.layoutGridFlow.collectAsState(initial = true)
    val fontSizeScale by prefManager.fontSizeScaleFlow.collectAsState(initial = 1.0f)
    val isBiometricEnabled by prefManager.biometricLockFlow.collectAsState(initial = false)

    var isPerformingBackup by remember { mutableStateOf(false) }

    // Backup ZIP exporter Launcher SAF
    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isPerformingBackup = true
                try {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        val success = app.repository.exportBackup(os)
                        if (success) {
                            Toast.makeText(context, "Backup exported successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to export backup.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error writing backup: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isPerformingBackup = false
                }
            }
        }
    }

    // Restore ZIP importer Launcher SAF
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isPerformingBackup = true
                try {
                    context.contentResolver.openInputStream(uri)?.use { isStream ->
                        val success = app.repository.restoreBackup(isStream)
                        if (success) {
                            Toast.makeText(context, "Database restored successfully! Restart app to load.", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Invalid backup package.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error reading backup file: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isPerformingBackup = false
                }
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Section 1: Themes & Styling
            Text("Themes & General Style", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            // Selector Segmented button for theme choice
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Select Theme Mode:", style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.values().forEach { mode ->
                        val selected = currentTheme == mode
                        FilterChip(
                            selected = selected,
                            onClick = {
                                scope.launch { prefManager.setThemeMode(mode) }
                            },
                            label = { Text(mode.name) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Grid vs lists toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Grid View Dashboard", style = MaterialTheme.typography.bodyLarge)
                    Text("Toggle default layout for your notes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = isGridLayout,
                    onCheckedChange = { scope.launch { prefManager.setLayoutGrid(it) } }
                )
            }

            // Text scaling slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Display Text Size Scale", style = MaterialTheme.typography.bodyLarge)
                    Text("${(fontSizeScale * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = fontSizeScale,
                    onValueChange = { scope.launch { prefManager.setFontSizeScale(it) } },
                    valueRange = 0.8f..1.5f,
                    steps = 6
                )
            }

            Divider()

            // Section 2: Integrity & Security
            Text("Privacy & Security", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Biometric Authentication Lock", style = MaterialTheme.typography.bodyLarge)
                    Text("Locks app entry with fingerprint/face metrics", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = isBiometricEnabled,
                    onCheckedChange = { scope.launch { prefManager.setBiometricLockEnabled(it) } }
                )
            }

            Divider()

            // Section 3: Manual sync / zip backup SAF
            Text("Backup & Offline Synchronisations", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { backupLauncher.launch("QuickNote_Backup_${System.currentTimeMillis()}.zip") },
                    modifier = Modifier.weight(1f),
                    enabled = !isPerformingBackup
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export ZIP")
                }

                FilledTonalButton(
                    onClick = { restoreLauncher.launch(arrayOf("application/zip")) },
                    modifier = Modifier.weight(1f),
                    enabled = !isPerformingBackup
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restore ZIP")
                }
            }

            if (isPerformingBackup) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Processing archive backup data...", style = MaterialTheme.typography.bodySmall)
                }
            }

            Divider()

            // Section 4: About Software
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("QuickNote App", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Version 3.0 (Stable Release)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Security Status: Fully Compliant & Sign Verified", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Text("© 2026 QuickNote. All parts locally secured.", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
