package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.QuickNoteApp
import com.example.data.Note
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNoteClick: (Note) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as QuickNoteApp
    val repository = app.repository

    var query by remember { mutableStateOf("") }
    val matchingNotes by remember(query) {
        if (query.isBlank()) {
            flowOf(emptyList<Note>())
        } else {
            repository.searchNotes(query)
        }
    }.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search Notes") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("search_back_button")
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
        ) {
            // Search Input Row
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search title, content, or checklist items") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { query = "" },
                            modifier = Modifier.testTag("search_clear_button")
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Search Query")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_query_input"),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (query.isBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Type in the search bar above to begin searching notes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            } else if (matchingNotes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No results found for \"$query\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(matchingNotes, key = { it.id }) { note ->
                        // Render brief card
                        val plainContent = remember(note.content, note.noteType) {
                            if (note.noteType == "TEXT") {
                                try {
                                    org.jsoup.Jsoup.parse(note.content).text()
                                } catch (e: Exception) {
                                    note.content
                                }
                            } else {
                                note.content
                            }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNoteClick(note) }
                                .testTag("search_result_note_${note.id}"),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(note.color).copy(alpha = 0.85f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = note.title.ifBlank { "Untitled Note" },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (note.color == 0xFFFFFFFF.toInt()) Color.Black else Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = plainContent,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = (if (note.color == 0xFFFFFFFF.toInt()) Color.Black else Color.White).copy(alpha = 0.8f),
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
