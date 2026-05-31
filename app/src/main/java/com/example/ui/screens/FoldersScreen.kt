package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.QuickNoteApp
import com.example.data.Folder
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as QuickNoteApp
    val repository = app.repository
    val scope = rememberCoroutineScope()

    val folders by repository.allFolders.collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(0xFF38BDF8.toInt()) }
    var selectedIcon by remember { mutableStateOf("Folder") }

    val colors = listOf(
        0xFF38BDF8.toInt(), // Sky
        0xFFF87171.toInt(), // Coral
        0xFF34D399.toInt(), // Emerald
        0xFFFBBF24.toInt(), // Amber
        0xFFA78BFA.toInt(), // Purple
        0xFFF472B6.toInt(), // Pink
        0xFF94A3B8.toInt()  // Slate Grey
    )

    val icons = listOf("Folder", "Work", "Home", "Book", "Star", "Payments", "Lightbulb")

    fun getIcon(name: String) = when (name) {
        "Work" -> Icons.Default.Work
        "Home" -> Icons.Default.Home
        "Book" -> Icons.Default.Book
        "Star" -> Icons.Default.Star
        "Payments" -> Icons.Default.Payments
        "Lightbulb" -> Icons.Default.Lightbulb
        else -> Icons.Default.Folder
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Folders") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("folders_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    folderName = ""
                    showAddDialog = true 
                },
                modifier = Modifier.testTag("add_folder_fab"),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create New Folder")
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (folders.isEmpty()) {
                // Empty State
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "No folders placeholder",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Folders Created Yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Categorize your notes by organizing them into colored folders.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(folders, key = { it.id }) { folder ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("folder_card_${folder.id}"),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color(folder.color)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = getIcon(folder.iconName),
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Text(
                                        text = folder.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            repository.deleteFolder(folder)
                                        }
                                    },
                                    modifier = Modifier.testTag("delete_folder_${folder.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Folder",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showAddDialog) {
                AlertDialog(
                    onDismissRequest = { showAddDialog = false },
                    title = { Text("Create Folder") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            OutlinedTextField(
                                value = folderName,
                                onValueChange = { folderName = it },
                                label = { Text("Folder Name") },
                                modifier = Modifier.fillMaxWidth().testTag("dialog_folder_name_input"),
                                singleLine = true
                            )

                            // Color Selector row
                            Text("Select Color:", style = MaterialTheme.typography.labelSmall)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                colors.forEach { colorInt ->
                                    val isColorSelected = selectedColor == colorInt
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(colorInt))
                                            .border(
                                                width = if (isColorSelected) 2.dp else 0.dp,
                                                color = Color.White,
                                                shape = CircleShape
                                            )
                                            .clickable { selectedColor = colorInt }
                                    )
                                }
                            }

                            // Icon Selector dynamic selection
                            Text("Select Icon:", style = MaterialTheme.typography.labelSmall)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                icons.forEach { iconName ->
                                    val isIconSelected = selectedIcon == iconName
                                    IconButton(
                                        onClick = { selectedIcon = iconName },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                color = if (isIconSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                                shape = CircleShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector = getIcon(iconName),
                                            contentDescription = null,
                                            tint = if (isIconSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (folderName.isNotBlank()) {
                                    scope.launch {
                                        repository.insertFolder(
                                            Folder(
                                                name = folderName,
                                                color = selectedColor,
                                                iconName = selectedIcon
                                            )
                                        )
                                        showAddDialog = false
                                    }
                                }
                            },
                            modifier = Modifier.testTag("dialog_folder_confirm")
                        ) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}
