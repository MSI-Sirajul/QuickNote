package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.data.Note
import com.example.data.Folder
import com.example.data.Tag
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNoteSelected: (Long) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as QuickNoteApp
    val repository = app.repository

    // Data streams
    val allFolders by repository.getAllFolders().collectAsState(initial = emptyList())
    val allTags by repository.getAllTags().collectAsState(initial = emptyList())

    // Search filter state variables
    var queryText by remember { mutableStateOf("") }
    var selectedFolderId by remember { mutableStateOf<Long?>(null) }
    var selectedTagId by remember { mutableStateOf<Long?>(null) }
    var selectedColorHex by remember { mutableStateOf<String?>(null) }
    var startDateFilter by remember { mutableStateOf<Long?>(null) }

    // Dynamic filtered results flow query
    var filteredNotes by remember { mutableStateOf<List<Note>>(emptyList()) }

    LaunchedEffect(queryText, selectedFolderId, selectedTagId, selectedColorHex, startDateFilter) {
        repository.filterNotes(
            query = queryText.ifBlank { null },
            folderId = selectedFolderId,
            colorHex = selectedColorHex,
            startDate = startDateFilter,
            endDate = null,
            tagId = selectedTagId
        ).collect { list ->
            filteredNotes = list
        }
    }

    val noteColors = listOf("#FFFFFF", "#FFF9C4", "#FFCCBC", "#C8E6C9", "#B3E5FC", "#FFCDD2")

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Filter Search Engine", fontWeight = FontWeight.Bold) },
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
        ) {
            // Big Search input field
            OutlinedTextField(
                value = queryText,
                onValueChange = { queryText = it },
                placeholder = { Text("Search titles, notes content or images OCR text...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (queryText.isNotEmpty()) {
                    {
                        IconButton(onClick = { queryText = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Scrollable Filters Row
            ScrollableTabRow(
                selectedTabIndex = 0,
                edgePadding = 0.dp,
                divider = {},
                indicator = {},
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                // Folder filter chip
                AssistChip(
                    onClick = {
                        // Cycles through folders
                        val curIdx = allFolders.indexOfFirst { it.id == selectedFolderId }
                        selectedFolderId = if (curIdx == -1 || curIdx == allFolders.lastIndex) {
                            null
                        } else {
                            allFolders[curIdx + 1].id
                        }
                    },
                    label = {
                        val folderName = allFolders.find { it.id == selectedFolderId }?.name ?: "All Folders"
                        Text("Notebook: $folderName")
                    },
                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.padding(end = 4.dp)
                )

                // Tag Filter chip
                AssistChip(
                    onClick = {
                        val curIdx = allTags.indexOfFirst { it.id == selectedTagId }
                        selectedTagId = if (curIdx == -1 || curIdx == allTags.lastIndex) {
                            null
                        } else {
                            allTags[curIdx + 1].id
                        }
                    },
                    label = {
                        val tagName = allTags.find { it.id == selectedTagId }?.name ?: "All Tags"
                        Text("Tag: $tagName")
                    },
                    leadingIcon = { Icon(Icons.Default.Label, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.padding(end = 4.dp)
                )

                // Color filter chip selector
                AssistChip(
                    onClick = {
                        val curIdx = noteColors.indexOf(selectedColorHex)
                        selectedColorHex = if (curIdx == -1 || curIdx == noteColors.lastIndex) {
                            null
                        } else {
                            noteColors[curIdx + 1]
                        }
                    },
                    label = {
                        Text(if (selectedColorHex == null) "All Colors" else "Has Accent Color")
                    },
                    leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.padding(end = 4.dp)
                )

                // Date filter chip
                AssistChip(
                    onClick = {
                        // Toggle last 24h filter
                        startDateFilter = if (startDateFilter == null) {
                            System.currentTimeMillis() - 24 * 60 * 60 * 1000L
                        } else null
                    },
                    label = {
                        Text(if (startDateFilter == null) "Any Time" else "Last 24 Hours")
                    },
                    leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            Divider()

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Results found: ${filteredNotes.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                // Clear all filters action
                if (selectedFolderId != null || selectedTagId != null || selectedColorHex != null || startDateFilter != null) {
                    TextButton(onClick = {
                        selectedFolderId = null
                        selectedTagId = null
                        selectedColorHex = null
                        startDateFilter = null
                    }) {
                        Text("Reset filters")
                    }
                }
            }

            // Results Listing
            if (filteredNotes.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No matching notes found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            "Try editing keywords or resetting category filters.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredNotes, key = { it.id }) { note ->
                        val cardBg = if (note.colorHex == "#FFFFFF") {
                            MaterialTheme.colorScheme.surface
                        } else {
                            try {
                                Color(android.graphics.Color.parseColor(note.colorHex)).copy(alpha = 0.85f)
                            } catch (e: Exception) {
                                MaterialTheme.colorScheme.surface
                            }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNoteSelected(note.id) }
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(24.dp)
                                ),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = note.title.ifBlank { "Untitled Note" },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (note.colorHex == "#FFFFFF") Color.Unspecified else Color.Black
                                    )

                                    if (note.isPinned) {
                                        Icon(Icons.Default.PushPin, contentDescription = "Pinned", tint = Color.DarkGray, modifier = Modifier.size(16.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Breadcrumb notebook folder helper
                                val fObj = allFolders.find { it.id == note.folderId }
                                if (fObj != null) {
                                    Text(
                                        text = "Notebook: ${fObj.name}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (note.colorHex == "#FFFFFF") MaterialTheme.colorScheme.primary else Color.Black.copy(0.7f),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                // OCR extracted text display indicator
                                if (!note.ocrText.isNullOrBlank()) {
                                    Text(
                                        text = "🔍 OCR Content: " + note.ocrText.substringBefore("]").substringAfter("["),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Blue,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                val bodySnippet = remember(note.content) {
                                    // De-serialize JSON or output snippet
                                    if (note.content.startsWith("[")) {
                                        try {
                                            val list = org.json.JSONArray(note.content)
                                            if (list.length() > 0) list.getJSONObject(0).getString("text") else ""
                                        } catch (e: Exception) { "" }
                                    } else {
                                        note.content
                                    }
                                }

                                if (bodySnippet.isNotBlank()) {
                                    Text(
                                        text = bodySnippet,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (note.colorHex == "#FFFFFF") MaterialTheme.colorScheme.onSurfaceVariant else Color.Black.copy(0.8f),
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
