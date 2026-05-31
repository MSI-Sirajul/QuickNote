package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.QuickNoteApp
import com.example.data.Folder
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
    onFolderSelected: (Long) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as QuickNoteApp
    val repository = app.repository
    val scope = rememberCoroutineScope()

    // Query folders in real-time Flow
    val allFolders by repository.getAllFolders().collectAsState(initial = emptyList())
    val allNotes by repository.getAllNotes().collectAsState(initial = emptyList())

    var curParentId by remember { mutableStateOf<Long?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    var newFolderName by remember { mutableStateOf("") }
    var selectedColorHex by remember { mutableStateOf("#FF6200EE") }
    var selectedParentId by remember { mutableStateOf<Long?>(null) }

    val currentFolder = remember(curParentId, allFolders) {
        allFolders.find { it.id == curParentId }
    }

    val visibleFolders = remember(curParentId, allFolders) {
        allFolders.filter { it.parentId == curParentId }
    }

    val colors = listOf("#FF6200EE", "#FF3700B3", "#FF03DAC5", "#FFFF5722", "#FF4CAF50", "#FF4CAF50", "#FFFFC107", "#FFE91E63")

    // Breadcrumb calculation trail
    val breadcrumbTrail = remember(curParentId, allFolders) {
        val trailList = mutableListOf<Folder>()
        var node = allFolders.find { it.id == curParentId }
        while (node != null) {
            trailList.add(0, node)
            node = allFolders.find { it.id == node?.parentId }
        }
        trailList
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(currentFolder?.name ?: "Folders & Notebooks", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        if (curParentId != null) {
                            curParentId = currentFolder?.parentId
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Navigate back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    newFolderName = ""
                    selectedParentId = curParentId
                    selectedColorHex = colors[0]
                    showCreateDialog = true 
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
            ) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = "Create Notebook")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Breadcrumbs visual bar if nested
            if (breadcrumbTrail.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Root",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { curParentId = null },
                        style = MaterialTheme.typography.bodyMedium
                    )

                    breadcrumbTrail.forEachIndexed { index, folder ->
                        Text("  /  ", color = Color.Gray)
                        Text(
                            text = folder.name,
                            color = if (index == breadcrumbTrail.lastIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { curParentId = folder.id },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Divider()
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (visibleFolders.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No notebooks/folders under this point.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            "Tap the pink card button below to add one!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(visibleFolders) { folder ->
                        val count = allNotes.count { it.folderId == folder.id }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onFolderSelected(folder.id) // view notes standard in this notebook
                                }
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(24.dp)
                                ),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            try {
                                                Color(android.graphics.Color.parseColor(folder.colorHex))
                                            } catch (e: Exception) {
                                                MaterialTheme.colorScheme.primary
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White)
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "$count Notes inside",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    TextButton(
                                        onClick = { curParentId = folder.id },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Open Nest", style = MaterialTheme.typography.bodySmall)
                                    }

                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                repository.deleteFolder(folder)
                                                Toast.makeText(context, "Notebook removed successfully.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete folder",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // New folder creation dialog
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("Create New Folder / Notebook") },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            label = { Text("Name of Notebook") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Color options list
                        Text("Choose Cover Accent Color:", style = MaterialTheme.typography.labelMedium)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            colors.forEach { hex ->
                                val rgb = Color(android.graphics.Color.parseColor(hex))
                                val isSelected = hex == selectedColorHex
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(rgb)
                                        .clickable { selectedColorHex = hex }
                                        .border(
                                            width = if (isSelected) 3.dp else 0.dp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                )
                            }
                        }

                        // Select nesting option
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = selectedParentId != null,
                                onCheckedChange = { checked ->
                                    selectedParentId = if (checked) curParentId else null
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Nest under current folder (${currentFolder?.name ?: "Root Folder"})",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newFolderName.isNotBlank()) {
                                scope.launch {
                                    val newFolder = Folder(
                                        name = newFolderName,
                                        parentId = selectedParentId,
                                        colorHex = selectedColorHex
                                    )
                                    repository.insertFolder(newFolder)
                                    showCreateDialog = false
                                }
                            } else {
                                Toast.makeText(context, "Please write folder name.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
