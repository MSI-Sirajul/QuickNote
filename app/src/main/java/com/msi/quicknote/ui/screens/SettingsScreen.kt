package com.msi.quicknote.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msi.quicknote.QuickNoteApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onThemeChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as QuickNoteApp
    val prefs = app.preferencesManager
    val security = app.securityManager

    var currentThemeSetting by remember { mutableStateOf(prefs.theme) }
    var currentLayoutSetting by remember { mutableStateOf(prefs.layout) }
    var isBiometricEnabled by remember { mutableStateOf(prefs.isBiometricEnabled) }
    var registeredPin by remember { mutableStateOf(prefs.pin) }

    var newPinInput by remember { mutableStateOf("") }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinDialogError by remember { mutableStateOf("") }

    val hasBiographicSensor = remember { security.canAuthenticate() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section: Visual Themes
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Theme drop-down or simple selection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Theme Preference", style = MaterialTheme.typography.bodyLarge)
                        var themeExpanded by remember { mutableStateOf(false) }
                        Box {
                            Button(
                                onClick = { themeExpanded = true },
                                modifier = Modifier.testTag("theme_selection_button")
                            ) {
                                Text(currentThemeSetting)
                            }
                            DropdownMenu(
                                expanded = themeExpanded,
                                onDismissRequest = { themeExpanded = false }
                            ) {
                                listOf("Light", "Dark", "System").forEach { tOption ->
                                    DropdownMenuItem(
                                        text = { Text(tOption) },
                                        onClick = {
                                            prefs.theme = tOption
                                            currentThemeSetting = tOption
                                            themeExpanded = false
                                            onThemeChanged()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Notes Layout Grid", style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = currentLayoutSetting == "Grid",
                            onCheckedChange = { isGrid ->
                                val lay = if (isGrid) "Grid" else "List"
                                prefs.layout = lay
                                currentLayoutSetting = lay
                            },
                            modifier = Modifier.testTag("layout_switch")
                        )
                    }
                }
            }

            // Section: Security Settings
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Privacy & Security", style = MaterialTheme.typography.titleMedium)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Note PIN Access", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = if (registeredPin.isEmpty()) "Not configured" else "PIN is enabled",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Button(
                            onClick = { 
                                newPinInput = ""
                                pinDialogError = ""
                                showPinDialog = true 
                            },
                            modifier = Modifier.testTag("setup_pin_button")
                        ) {
                            Text(if (registeredPin.isEmpty()) "Setup PIN" else "Change PIN")
                        }
                    }

                    if (registeredPin.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        TextButton(
                            onClick = {
                                prefs.pin = ""
                                registeredPin = ""
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("remove_pin_button")
                        ) {
                            Text("Disable PIN Access")
                        }
                    }

                    if (hasBiographicSensor) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Biometric Lock", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = "Unlock secure notes using system biometric sensors.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            Switch(
                                checked = isBiometricEnabled,
                                onCheckedChange = { isEnabled ->
                                    prefs.isBiometricEnabled = isEnabled
                                    isBiometricEnabled = isEnabled
                                },
                                modifier = Modifier.testTag("biometric_switch")
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Biometric sensors not available on this device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Section: App Info Version Box
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "QuickNote",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Version 3.1 (Stable Release)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "Clean, secure block notes with biometric protection.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Dialog for setting up PIN
        if (showPinDialog) {
            AlertDialog(
                onDismissRequest = { showPinDialog = false },
                title = { Text("Configure Security PIN") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Create a numeric PIN (4-6 digits) to secure your locked notebooks.", style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = newPinInput,
                            onValueChange = { 
                                if (it.length <= 6) {
                                    newPinInput = it
                                    pinDialogError = ""
                                }
                            },
                            label = { Text("Enter 4-6 Digit PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag("dialog_pin_input"),
                            singleLine = true
                        )

                        if (pinDialogError.isNotEmpty()) {
                            Text(
                                text = pinDialogError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newPinInput.length in 4..6) {
                                prefs.pin = newPinInput
                                registeredPin = newPinInput
                                showPinDialog = false
                            } else {
                                pinDialogError = "PIN must be between 4 and 6 digits."
                            }
                        },
                        modifier = Modifier.testTag("dialog_pin_confirm")
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPinDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
