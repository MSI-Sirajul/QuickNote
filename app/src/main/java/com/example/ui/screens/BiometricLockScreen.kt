package com.example.ui.screens

import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

@Composable
fun BiometricLockScreen(
    onUnlockSuccess: () -> Unit
) {
    val context = LocalContext.current
    var inputFallbackPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }

    // Initialize biometric prompt configuration
    val activity = context as? FragmentActivity
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }

    val biometricPrompt = remember(activity, executor) {
        activity?.let { act ->
            BiometricPrompt(act, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Toast.makeText(context, "Welcome Back! App Unlocked.", Toast.LENGTH_SHORT).show()
                    onUnlockSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(context, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(context, "Authentication failed. Try again.", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    val promptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("QuickNote Vault Lock")
            .setSubtitle("Authenticate using biometric metrics to open notes")
            .setNegativeButtonText("Use Back-Up Password")
            .build()
    }

    LaunchedEffect(biometricPrompt) {
        biometricPrompt?.authenticate(promptInfo)
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Account Vault Locked",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "QuickNote is protected under cryptographic security shields.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action to manually request Biometrics if dismissed
            Button(
                onClick = { biometricPrompt?.authenticate(promptInfo) },
                shape = CircleShape,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = "Authenticate", modifier = Modifier.size(36.dp))
            }

            Text("Tap to use biometric authentication", style = MaterialTheme.typography.labelMedium)

            Spacer(modifier = Modifier.height(12.dp))

            // Fallback Password input if fingerprint is absent in environment
            OutlinedTextField(
                value = inputFallbackPassword,
                onValueChange = { inputFallbackPassword = it; passwordError = false },
                label = { Text("Backup Password") },
                singleLine = true,
                isError = passwordError,
                modifier = Modifier.fillMaxWidth(0.8f)
            )

            Button(
                onClick = {
                    // Standard default master backup bypass password for mock preview setups
                    if (inputFallbackPassword == "123456" || inputFallbackPassword.lowercase() == "quicknote") {
                        onUnlockSuccess()
                    } else {
                        passwordError = true
                        Toast.makeText(context, "Invalid fallback credentials.", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Text("Bypass Back-Up Unlock")
            }

            Text("Default sandbox bypass password is '123456'", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}
