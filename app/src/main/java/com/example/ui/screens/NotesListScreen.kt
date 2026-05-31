package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.QuickNoteApp
import com.example.data.Note
import com.example.ui.navigation.Routes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotesListScreen(
    folderIdFilter: Long? = null,
    onNavigateToEditor: (Long) -> Unit,
    onNavigateToFolders: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSearch: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as QuickNoteApp
    val repository = app.repository
    val scope = rememberCoroutineScope()

    // Preferences states
    val isGridLayout by app.preferencesManager.layoutGridFlow.collectAsState(initial = true)
    val fontSizeMultiplier by app.preferencesManager.fontSizeScaleFlow.collectAsState(initial = 1.0f)

    // Notes listing
    val allNotes by (if (folderIdFilter != null) {
        repository.getNotesInFolder(folderIdFilter)
    } else {
        repository.getAllNotes()
    }).collectAsState(initial = emptyList())

    val folderObject = remember(folderIdFilter) {
        if (folderIdFilter != null) {
            runCatching {
                // Return immediate placeholder or fetch folder
            }
        }
        null
    }

    val snackbarHostState = remember { SnackbarHostState() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val availableWidth = maxWidth
        val isTablet = availableWidth > 600.dp

        if (isTablet) {
            // Adaptive Two-Pane Landscape layout
            var selectedNoteIdForDetail by remember { mutableStateOf(0L) }
            var splitterFraction by remember { mutableStateOf(0.4f) }

            Row(modifier = Modifier.fillMaxSize()) {
                // Navigation Rail
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    FloatingActionButton(
                        onClick = { onNavigateToEditor(0L) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(16.dp),
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New note")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    NavigationRailItem(
                        selected = true,
                        onClick = {},
                        icon = { Icon(Icons.Default.Description, contentDescription = "Notes") },
                        label = { Text("Notes") }
                    )
                    NavigationRailItem(
                        selected = false,
                        onClick = onNavigateToFolders,
                        icon = { Icon(Icons.Default.Folder, contentDescription = "Folders") },
                        label = { Text("Folders") }
                    )
                    NavigationRailItem(
                        selected = false,
                        onClick = onNavigateToSearch,
                        icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        label = { Text("Search") }
                    )
                    NavigationRailItem(
                        selected = false,
                        onClick = onNavigateToSettings,
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Left pane: Note items list with dynamic split ratio width
                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(splitterFraction)) {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Dashboard",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )

                            IconButton(onClick = {
                                scope.launch {
                                    app.preferencesManager.setLayoutGrid(!isGridLayout)
                                }
                            }) {
                                Icon(
                                    imageVector = if (isGridLayout) Icons.Default.List else Icons.Default.GridView,
                                    contentDescription = "Toggle Grid/List layout"
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        NotesItemsList(
                            notes = allNotes,
                            isGrid = isGridLayout,
                            onNoteSelected = { noteId ->
                                selectedNoteIdForDetail = noteId
                            },
                            onDeleteNote = { note ->
                                scope.launch {
                                    repository.deleteNote(note)
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Note deleted successfully",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        repository.insertOrUpdateNote(note)
                                    }
                                }
                            }
                        )
                    }
                }

                // Splitter handle
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(6.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val parentWidthPx = size.width
                                val dFraction = dragAmount.x / parentWidthPx
                                splitterFraction = (splitterFraction + dFraction).coerceIn(0.25f, 0.70f)
                            }
                        }
                )

                // Right Pane: Detail View Editor or Empty view
                Box(modifier = Modifier.fillMaxHeight().weight(1f)) {
                    if (selectedNoteIdForDetail == 0L) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Select a note from the dashboard to display editor", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                            }
                        }
                    } else {
                        NoteEditorScreen(
                            noteId = selectedNoteIdForDetail,
                            onBack = { selectedNoteIdForDetail = 0L }
                        )
                    }
                }
            }
        } else {
            // Mobile Portrait Layout
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        title = { 
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                                Text(
                                    text = if (folderIdFilter != null) "Group Folder" else "QuickNote",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                            scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            actionIconContentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        actions = {
                            IconButton(onClick = onNavigateToSearch) {
                                Icon(Icons.Default.Search, contentDescription = "Search Notes")
                            }

                            // Layout grid list toggle
                            IconButton(onClick = {
                                scope.launch {
                                    app.preferencesManager.setLayoutGrid(!isGridLayout)
                                }
                            }) {
                                Icon(
                                    imageVector = if (isGridLayout) Icons.Default.List else Icons.Default.GridView,
                                    contentDescription = "Toggle Grid/List layout"
                                )
                            }
                        }
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        NavigationBarItem(
                            selected = true,
                            onClick = {},
                            icon = { Icon(Icons.Default.Description, contentDescription = null) },
                            label = { Text("Notes") }
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = onNavigateToFolders,
                            icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                            label = { Text("Folders") }
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = onNavigateToSettings,
                            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            label = { Text("Settings") }
                        )
                    }
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { onNavigateToEditor(0L) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(16.dp),
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Plus note", modifier = Modifier.size(28.dp))
                    }
                },
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp)
                ) {
                    NotesItemsList(
                        notes = allNotes,
                        isGrid = isGridLayout,
                        onNoteSelected = onNavigateToEditor,
                        onDeleteNote = { note ->
                            scope.launch {
                                repository.deleteNote(note)
                                val result = snackbarHostState.showSnackbar(
                                    message = "Note: \"${note.title}\" deleted.",
                                    actionLabel = "Undo"
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    repository.insertOrUpdateNote(note)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun NotesItemsList(
    notes: List<Note>,
    isGrid: Boolean,
    onNoteSelected: (Long) -> Unit,
    onDeleteNote: (Note) -> Unit
) {
    if (notes.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.EditNote,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("No notes saved yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                Text("Click the float '+' button to start writing!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    } else {
        val pinnedNotes = remember(notes) { notes.filter { it.isPinned } }
        val normalNotes = remember(notes) { notes.filter { !it.isPinned } }

        Column(modifier = Modifier.fillMaxSize()) {
            if (isGrid) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (pinnedNotes.isNotEmpty()) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pinned Notes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        items(pinnedNotes, key = { "pinned_" + it.id }) { note ->
                            NoteGridCard(note = note, onClick = { onNoteSelected(note.id) }, onDelete = { onDeleteNote(note) })
                        }
                    }

                    if (normalNotes.isNotEmpty()) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Text("Your Notes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                        }
                        items(normalNotes, key = { "normal_" + it.id }) { note ->
                            NoteGridCard(note = note, onClick = { onNoteSelected(note.id) }, onDelete = { onDeleteNote(note) })
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (pinnedNotes.isNotEmpty()) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pinned Notes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        items(pinnedNotes, key = { "list_pinned_" + it.id }) { note ->
                            NoteListCard(note = note, onClick = { onNoteSelected(note.id) }, onDelete = { onDeleteNote(note) })
                        }
                    }

                    if (normalNotes.isNotEmpty()) {
                        item {
                            Text("Your Notes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                        }
                        items(normalNotes, key = { "list_normal_" + it.id }) { note ->
                            NoteListCard(note = note, onClick = { onNoteSelected(note.id) }, onDelete = { onDeleteNote(note) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NoteGridCard(note: Note, onClick: () -> Unit, onDelete: () -> Unit) {
    val cardColor = if (note.colorHex == "#FFFFFF") {
        MaterialTheme.colorScheme.surface
    } else {
        try {
            Color(android.graphics.Color.parseColor(note.colorHex)).copy(alpha = 0.85f)
        } catch (e: Exception) {
            MaterialTheme.colorScheme.surface
        }
    }

    val finalColor = if (note.isPinned && note.colorHex == "#FFFFFF") {
        // Soft blue blend for standard pinned items
        Color(0xFFD3E3FD).copy(alpha = 0.85f)
    } else {
        cardColor
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(28.dp)
            ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = finalColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title.ifBlank { "Untitled Note" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (note.colorHex == "#FFFFFF") Color.Unspecified else Color.Black,
                    maxLines = 1
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = if (note.colorHex == "#FFFFFF") MaterialTheme.colorScheme.error.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Text excerpt snippet de-serializer
            val previewSnippet = remember(note.content) {
                if (note.content.startsWith("[")) {
                    try {
                        val arr = org.json.JSONArray(note.content)
                        if (arr.length() > 0) arr.getJSONObject(0).getString("text") else ""
                    } catch (e: Exception) { "" }
                } else {
                    note.content
                }
            }

            Text(
                text = previewSnippet.ifBlank { "No content description." },
                style = MaterialTheme.typography.bodySmall,
                color = if (note.colorHex == "#FFFFFF") MaterialTheme.colorScheme.onSurfaceVariant else Color.Black.copy(alpha = 0.8f),
                maxLines = 3,
                minLines = 2
            )

            if (note.isFavorite) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    Icon(Icons.Filled.Favorite, contentDescription = "Favorite", tint = Color.Red, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
fun NoteListCard(note: Note, onClick: () -> Unit, onDelete: () -> Unit) {
    val cardColor = if (note.colorHex == "#FFFFFF") {
        MaterialTheme.colorScheme.surface
    } else {
        try {
            Color(android.graphics.Color.parseColor(note.colorHex)).copy(alpha = 0.85f)
        } catch (e: Exception) {
            MaterialTheme.colorScheme.surface
        }
    }

    val finalColor = if (note.isPinned && note.colorHex == "#FFFFFF") {
        Color(0xFFD3E3FD).copy(alpha = 0.85f)
    } else {
        cardColor
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(24.dp)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = finalColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.title.ifBlank { "Untitled Note" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (note.colorHex == "#FFFFFF") Color.Unspecified else Color.Black
                )

                Spacer(modifier = Modifier.height(4.dp))

                val snippetText = remember(note.content) {
                    if (note.content.startsWith("[")) {
                        try {
                            val arr = org.json.JSONArray(note.content)
                            if (arr.length() > 0) arr.getJSONObject(0).getString("text") else ""
                        } catch (e: Exception) { "" }
                    } else {
                        note.content
                    }
                }

                if (snippetText.isNotBlank()) {
                    Text(
                        text = snippetText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (note.colorHex == "#FFFFFF") MaterialTheme.colorScheme.onSurfaceVariant else Color.Black.copy(alpha = 0.8f),
                        maxLines = 1
                    )
                }
            }

            if (note.isFavorite) {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color.Red, modifier = Modifier.padding(horizontal = 8.dp).size(16.dp))
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = if (note.colorHex == "#FFFFFF") MaterialTheme.colorScheme.error.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.5f)
                )
            }
        }
    }
}
