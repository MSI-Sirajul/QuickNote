package com.msi.quicknote.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msi.quicknote.QuickNoteApp
import com.msi.quicknote.data.Folder
import com.msi.quicknote.data.Note
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    onNavigateToEditor: (Int) -> Unit,
    onNavigateToFolders: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as QuickNoteApp
    val repository = app.repository
    val prefs = app.preferencesManager
    val scope = rememberCoroutineScope()

    val notes by repository.allNotes.collectAsState(initial = emptyList())
    val folders by repository.allFolders.collectAsState(initial = emptyList())

    var selectedFolderId by remember { mutableStateOf<Int?>(null) }
    val isGrid = remember { mutableStateOf(prefs.layout == "Grid") }
    var isMenuExpanded by remember { mutableStateOf(false) }

    // Re-read preferences layout periodically or track locally
    LaunchedEffect(prefs.layout) {
        isGrid.value = prefs.layout == "Grid"
    }

    // Filter notes dynamically
    val filteredNotes = remember(notes, selectedFolderId) {
        val fId = selectedFolderId
        if (fId == null) notes else notes.filter { it.folderId == fId }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "QuickNote",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            )
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = onNavigateToSearch,
                            modifier = Modifier.testTag("action_search")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search Notes")
                        }

                        IconButton(
                            onClick = onNavigateToFolders,
                            modifier = Modifier.testTag("action_folders")
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = "Folders Management")
                        }

                        IconButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier.testTag("action_settings")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings Options")
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
            ) {
                // Horizontal Folders Row for quick filtering
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "All Notes" selector
                item {
                    FilterChip(
                        selected = selectedFolderId == null,
                        onClick = { selectedFolderId = null },
                        label = { Text("All Notes") },
                        modifier = Modifier.testTag("filter_all_notes")
                    )
                }

                // Dynamic Folders filters
                items(folders, key = { it.id }) { folder ->
                    FilterChip(
                        selected = selectedFolderId == folder.id,
                        onClick = { selectedFolderId = folder.id },
                        label = { Text(folder.name) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(folder.color))
                            )
                        },
                        modifier = Modifier.testTag("filter_folder_${folder.id}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredNotes.isEmpty()) {
                // Empty state layout holding instructions
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.StickyNote2,
                            contentDescription = "Empty notes display",
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (selectedFolderId == null) "Write your first note!" else "No notes in this folder",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (selectedFolderId == null) {
                                "Tap the floating button below to compose standard or rich notes, add drawings, checkboxes, and setup biometric locks."
                            } else {
                                "Try creating dynamic notes in this folder or select a different filter!"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                // Vertical or horizontal visual lists grid
                val columns = if (isGrid.value) 2 else 1
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(columns),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalItemSpacing = 8.dp
                ) {
                    items(filteredNotes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            onClick = { onNavigateToEditor(note.id) },
                            onDelete = {
                                scope.launch {
                                    repository.deleteNote(note)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

        if (isMenuExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { isMenuExpanded = false }
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            AnimatedVisibility(
                visible = isMenuExpanded,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    // Sketch Note Mini-FAB
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Text(
                                text = "Sketch Note",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        SmallFloatingActionButton(
                            onClick = {
                                isMenuExpanded = false
                                scope.launch {
                                    val newId = repository.insertNote(
                                        Note(title = "", content = "", noteType = "SKETCH", color = 0xFFED6C02.toInt())
                                    )
                                    onNavigateToEditor(newId.toInt())
                                }
                            },
                            containerColor = Color(0xFFED6C02), // Orange
                            contentColor = Color.White,
                            modifier = Modifier.testTag("add_sketch_note_item")
                        ) {
                            Icon(Icons.Outlined.Draw, contentDescription = "Sketch Note")
                        }
                    }

                    // Markdown Note Mini-FAB
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Text(
                                text = "Markdown Note",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        SmallFloatingActionButton(
                            onClick = {
                                isMenuExpanded = false
                                scope.launch {
                                    val newId = repository.insertNote(
                                        Note(title = "", content = "", noteType = "MARKDOWN", color = 0xFF2E7D32.toInt())
                                    )
                                    onNavigateToEditor(newId.toInt())
                                }
                            },
                            containerColor = Color(0xFF2E7D32), // Green
                            contentColor = Color.White,
                            modifier = Modifier.testTag("add_markdown_note_item")
                        ) {
                            Icon(Icons.Outlined.Code, contentDescription = "Markdown Note")
                        }
                    }

                    // Text Note Mini-FAB
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Text(
                                text = "Text Note",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        SmallFloatingActionButton(
                            onClick = {
                                isMenuExpanded = false
                                scope.launch {
                                    val newId = repository.insertNote(
                                        Note(title = "", content = "", noteType = "TEXT", color = 0xFF0288D1.toInt())
                                    )
                                    onNavigateToEditor(newId.toInt())
                                }
                            },
                            containerColor = Color(0xFF0288D1), // Blue
                            contentColor = Color.White,
                            modifier = Modifier.testTag("add_text_note_item")
                        ) {
                            Icon(Icons.Outlined.TextFields, contentDescription = "Text Note")
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = { isMenuExpanded = !isMenuExpanded },
                modifier = Modifier.testTag("new_note_fab"),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = if (isMenuExpanded) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = "Create Note"
                )
            }
        }
    }
}

@Composable
fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val plainContent = remember(note.content, note.noteType) {
        if (note.noteType == "TEXT") {
            try {
                org.jsoup.Jsoup.parse(note.content).text()
            } catch (e: Exception) {
                note.content
            }
        } else if (note.noteType == "SKETCH") {
            "Sketch Canvas Drawing Artwork"
        } else {
            note.content
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("note_card_${note.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min)
        ) {
            // Far Left Colored Accent Bar based on note's Color
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(Color(note.color))
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp)
            ) {
                // Header Row: Title & Icons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = note.title.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Type indicator icon
                    val typeIcon = when (note.noteType) {
                        "TEXT" -> Icons.Default.Notes
                        "MARKDOWN" -> Icons.Default.Code
                        "SKETCH" -> Icons.Default.Brush
                        else -> Icons.Default.Notes
                    }
                    Icon(
                        imageVector = typeIcon,
                        contentDescription = "Note Type ${note.noteType}",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )

                    if (note.isPinned) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned Note",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Content snippet (exactly 2 lines max)
                if (note.isLocked) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Encrypted Note locked",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Encrypted Note (Locked)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = plainContent.ifBlank { "Empty note" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Footer Row: Alert Status, delete icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (note.reminderTime != null) {
                            Icon(
                                imageVector = Icons.Default.Alarm,
                                contentDescription = "Alarm active",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("delete_note_button_${note.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete note",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
