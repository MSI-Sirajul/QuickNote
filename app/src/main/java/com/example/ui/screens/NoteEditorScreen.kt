package com.example.ui.screens

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.QuickNoteApp
import com.example.data.Note
import com.example.receiver.ReminderBroadcastReceiver
import com.example.ui.components.*
import com.example.ui.theme.NoteColorColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as QuickNoteApp
    val repository = app.repository
    val prefs = app.preferencesManager
    val scope = rememberCoroutineScope()

    var initialized by remember { mutableStateOf(false) }

    // State Fields
    var noteTitle by remember { mutableStateOf("") }
    var noteContentBlocksJson by remember { mutableStateOf("") }
    var noteFolderId by remember { mutableStateOf<Int?>(null) }
    var noteIsPinned by remember { mutableStateOf(false) }
    var noteColor by remember { mutableStateOf(0xFF1E293B.toInt()) }
    var noteIsLocked by remember { mutableStateOf(false) }
    var noteReminderTime by remember { mutableStateOf<Long?>(null) }
    var noteDrawingData by remember { mutableStateOf<String?>(null) }
    var noteType by remember { mutableStateOf("TEXT") }

    // UI Controls
    var showFolderSelector by remember { mutableStateOf(false) }
    var isBiometricCheckingBeforeShowingLockedContent by remember { mutableStateOf(false) }
    
    // Status Feedback
    var isSaving by remember { mutableStateOf(false) }
    var lastSavedText by remember { mutableStateOf("All changes saved") }

    // Editors specific state
    var textEditorValue by remember { mutableStateOf(TextFieldValue("")) }
    var markdownEditorValue by remember { mutableStateOf(TextFieldValue("")) }
    
    // Markdown preview state
    var showMarkdownPreview by remember { mutableStateOf(false) }

    // HTML/Rich properties undo/redo stacks
    val richUndoStack = remember { mutableStateListOf<TextFieldValue>() }
    val richRedoStack = remember { mutableStateListOf<TextFieldValue>() }

    val folders by repository.allFolders.collectAsState(initial = emptyList())

    // Load Note Data
    LaunchedEffect(noteId) {
        if (noteId != -1 && !initialized) {
            val retrievedNote = repository.getNoteById(noteId)
            if (retrievedNote != null) {
                noteTitle = retrievedNote.title
                noteContentBlocksJson = retrievedNote.content
                noteFolderId = retrievedNote.folderId
                noteIsPinned = retrievedNote.isPinned
                noteColor = retrievedNote.color
                noteIsLocked = retrievedNote.isLocked
                noteReminderTime = retrievedNote.reminderTime
                noteDrawingData = retrievedNote.drawingData
                noteType = retrievedNote.noteType

                if (noteType == "TEXT") {
                    val decodedAnnotated = RichTextParser.htmlToAnnotatedString(retrievedNote.content)
                    textEditorValue = TextFieldValue(annotatedString = decodedAnnotated)
                } else if (noteType == "MARKDOWN") {
                    markdownEditorValue = TextFieldValue(retrievedNote.content)
                    showMarkdownPreview = true // Let's open saved files with preview visible by default
                }

                if (retrievedNote.isLocked) {
                    isBiometricCheckingBeforeShowingLockedContent = true
                }
            }
            initialized = true
        } else if (noteId == -1) {
            initialized = true
        }
    }

    // Alarm scheduler helper
    fun scheduleAndroidReminder(timeInMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_SHOW_REMINDER
            putExtra(ReminderBroadcastReceiver.EXTRA_NOTE_ID, noteId)
            putExtra(ReminderBroadcastReceiver.EXTRA_NOTE_TITLE, noteTitle.ifBlank { "Task reminder" })
            putExtra(ReminderBroadcastReceiver.EXTRA_NOTE_CONTENT, "Open QuickNote to view details.")
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            if (noteId == -1) 9999 else noteId,
            intent,
            pendingIntentFlags
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Core Save action
    fun saveDirectSynchronous() {
        val finalContent = when (noteType) {
            "TEXT" -> RichTextParser.annotatedStringToHtml(textEditorValue.annotatedString)
            "MARKDOWN" -> markdownEditorValue.text
            else -> noteContentBlocksJson
        }
        val noteEntity = Note(
            id = if (noteId == -1) 0 else noteId,
            title = noteTitle,
            content = finalContent,
            folderId = noteFolderId,
            isPinned = noteIsPinned,
            color = noteColor,
            isLocked = noteIsLocked,
            reminderTime = noteReminderTime,
            drawingData = noteDrawingData,
            noteType = noteType,
            lastModified = System.currentTimeMillis()
        )
        scope.launch {
            if (noteId == -1) {
                val newId = repository.insertNote(noteEntity)
                noteReminderTime?.let { scheduleAndroidReminder(it) }
            } else {
                repository.updateNote(noteEntity)
                noteReminderTime?.let { scheduleAndroidReminder(it) }
            }
        }
    }

    // Auto save debouncer: saves 5 seconds after last change
    LaunchedEffect(
        noteTitle,
        textEditorValue,
        markdownEditorValue,
        noteContentBlocksJson,
        noteFolderId,
        noteIsPinned,
        noteColor,
        noteIsLocked,
        noteReminderTime,
        noteDrawingData
    ) {
        if (!initialized) return@LaunchedEffect
        delay(5000)
        isSaving = true
        lastSavedText = "Saving..."
        
        val finalContent = when (noteType) {
            "TEXT" -> RichTextParser.annotatedStringToHtml(textEditorValue.annotatedString)
            "MARKDOWN" -> markdownEditorValue.text
            else -> noteContentBlocksJson
        }
        
        val noteEntity = Note(
            id = if (noteId == -1) 0 else noteId,
            title = noteTitle,
            content = finalContent,
            folderId = noteFolderId,
            isPinned = noteIsPinned,
            color = noteColor,
            isLocked = noteIsLocked,
            reminderTime = noteReminderTime,
            drawingData = noteDrawingData,
            noteType = noteType,
            lastModified = System.currentTimeMillis()
        )
        
        repository.updateNote(noteEntity)
        delay(600)
        isSaving = false
        lastSavedText = "Saved at ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}"
    }

    val selectedFold = folders.find { it.id == noteFolderId }

    if (isBiometricCheckingBeforeShowingLockedContent && noteIsLocked) {
        BiometricLockScreen(
            onUnlocked = {
                isBiometricCheckingBeforeShowingLockedContent = false
            }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        TextField(
                            value = noteTitle,
                            onValueChange = { noteTitle = it },
                            textStyle = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            ),
                            placeholder = {
                                Text(
                                    "Note Title...",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                    )
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("note_title_input"),
                            singleLine = true
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                saveDirectSynchronous()
                                onBack()
                            },
                            modifier = Modifier.testTag("editor_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { noteIsPinned = !noteIsPinned },
                            modifier = Modifier.testTag("action_pin_note")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Pin Note",
                                tint = if (noteIsPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (prefs.pin.isEmpty()) {
                                    noteIsLocked = false
                                } else {
                                    noteIsLocked = !noteIsLocked
                                }
                            },
                            modifier = Modifier.testTag("action_lock_note")
                        ) {
                            Icon(
                                imageVector = if (noteIsLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = "Lock content protection",
                                tint = if (noteIsLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                            )
                        }

                        IconButton(
                            onClick = {
                                saveDirectSynchronous()
                                onBack()
                            },
                            modifier = Modifier.testTag("action_save_note")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Save Note",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                // Persistent status line at bottom
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = lastSavedText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Text(
                            text = noteType,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
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
                // Secondary Controls Row: Folder, alarm, and color accents
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        FilterChip(
                            selected = noteFolderId != null,
                            onClick = { showFolderSelector = true },
                            label = { Text(selectedFold?.name ?: "No Category") },
                            leadingIcon = {
                                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.testTag("folder_picker_chip")
                        )

                        DropdownMenu(
                            expanded = showFolderSelector,
                            onDismissRequest = { showFolderSelector = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("None") },
                                onClick = {
                                    noteFolderId = null
                                    showFolderSelector = false
                                }
                            )
                            folders.forEach { fOption ->
                                DropdownMenuItem(
                                    text = { Text(fOption.name) },
                                    onClick = {
                                        noteFolderId = fOption.id
                                        showFolderSelector = false
                                    }
                                )
                            }
                        }
                    }

                    val alarmFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                    FilterChip(
                        selected = noteReminderTime != null,
                        onClick = {
                            val calendar = Calendar.getInstance()
                            noteReminderTime?.let { calendar.timeInMillis = it }
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    calendar.set(Calendar.YEAR, year)
                                    calendar.set(Calendar.MONTH, month)
                                    calendar.set(Calendar.DAY_OF_MONTH, day)
                                    TimePickerDialog(
                                        context,
                                        { _, hour, minute ->
                                            calendar.set(Calendar.HOUR_OF_DAY, hour)
                                            calendar.set(Calendar.MINUTE, minute)
                                            calendar.set(Calendar.SECOND, 0)
                                            noteReminderTime = calendar.timeInMillis
                                        },
                                        calendar.get(Calendar.HOUR_OF_DAY),
                                        calendar.get(Calendar.MINUTE),
                                        true
                                    ).show()
                                },
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        label = { Text(noteReminderTime?.let { alarmFormat.format(Date(it)) } ?: "Alert") },
                        leadingIcon = {
                            Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        trailingIcon = if (noteReminderTime != null) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear Alert",
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { noteReminderTime = null }
                                )
                            }
                        } else null,
                        modifier = Modifier.testTag("reminder_picker_chip")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        NoteColorColors.take(4).forEach { colInt ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(colInt))
                                    .border(
                                        width = if (noteColor == colInt.toInt()) 2.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    )
                                    .clickable { noteColor = colInt.toInt() }
                                    .testTag("color_picker_$colInt")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Render Active Editor according to Selected Mode
                when (noteType) {
                    "TEXT" -> {
                        // Horizontal toolbar for styling
                        var showStylePicker by remember { mutableStateOf(false) }
                        var showSizePicker by remember { mutableStateOf(false) }
                        var showFamilyPicker by remember { mutableStateOf(false) }
                        var showAlignPicker by remember { mutableStateOf(false) }
                        var showColorGrid by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    val previous = textEditorValue
                                    textEditorValue = RichTextEditingSuite.applySpanStyle(textEditorValue, SpanStyle(fontWeight = FontWeight.Bold))
                                    richUndoStack.add(previous)
                                },
                                modifier = Modifier.testTag("rich_bold_button")
                            ) {
                                Icon(Icons.Default.FormatBold, contentDescription = "Bold")
                            }

                            IconButton(
                                onClick = {
                                    val previous = textEditorValue
                                    textEditorValue = RichTextEditingSuite.applySpanStyle(textEditorValue, SpanStyle(fontStyle = FontStyle.Italic))
                                    richUndoStack.add(previous)
                                },
                                modifier = Modifier.testTag("rich_italic_button")
                            ) {
                                Icon(Icons.Default.FormatItalic, contentDescription = "Italic")
                            }

                            IconButton(
                                onClick = {
                                    val previous = textEditorValue
                                    textEditorValue = RichTextEditingSuite.applySpanStyle(textEditorValue, SpanStyle(textDecoration = TextDecoration.Underline))
                                    richUndoStack.add(previous)
                                },
                                modifier = Modifier.testTag("rich_underline_button")
                            ) {
                                Icon(Icons.Default.FormatUnderlined, contentDescription = "Underline")
                            }

                            IconButton(
                                onClick = {
                                    val previous = textEditorValue
                                    textEditorValue = RichTextEditingSuite.applySpanStyle(textEditorValue, SpanStyle(textDecoration = TextDecoration.LineThrough))
                                    richUndoStack.add(previous)
                                },
                                modifier = Modifier.testTag("rich_strikethrough_button")
                            ) {
                                Icon(Icons.Default.FormatStrikethrough, contentDescription = "Strikethrough")
                            }

                            // Headings
                            Box {
                                IconButton(onClick = { showStylePicker = true }) {
                                    Icon(Icons.Default.Title, contentDescription = "Heading")
                                }
                                DropdownMenu(expanded = showStylePicker, onDismissRequest = { showStylePicker = false }) {
                                    DropdownMenuItem(text = { Text("Normal Text") }, onClick = {
                                        val previous = textEditorValue
                                        textEditorValue = RichTextEditingSuite.applyHeading(textEditorValue, "normal")
                                        richUndoStack.add(previous)
                                        showStylePicker = false
                                    })
                                    DropdownMenuItem(text = { Text("Heading 1") }, onClick = {
                                        val previous = textEditorValue
                                        textEditorValue = RichTextEditingSuite.applyHeading(textEditorValue, "h1")
                                        richUndoStack.add(previous)
                                        showStylePicker = false
                                    })
                                    DropdownMenuItem(text = { Text("Heading 2") }, onClick = {
                                        val previous = textEditorValue
                                        textEditorValue = RichTextEditingSuite.applyHeading(textEditorValue, "h2")
                                        richUndoStack.add(previous)
                                        showStylePicker = false
                                    })
                                    DropdownMenuItem(text = { Text("Heading 3") }, onClick = {
                                        val previous = textEditorValue
                                        textEditorValue = RichTextEditingSuite.applyHeading(textEditorValue, "h3")
                                        richUndoStack.add(previous)
                                        showStylePicker = false
                                    })
                                }
                            }

                            IconButton(
                                onClick = {
                                    val previous = textEditorValue
                                    textEditorValue = RichTextEditingSuite.insertListMarkup(textEditorValue, false)
                                    richUndoStack.add(previous)
                                },
                                modifier = Modifier.testTag("rich_bullet_button")
                            ) {
                                Icon(Icons.Default.FormatListBulleted, contentDescription = "Bullet List")
                            }

                            IconButton(
                                onClick = {
                                    val previous = textEditorValue
                                    textEditorValue = RichTextEditingSuite.insertListMarkup(textEditorValue, true)
                                    richUndoStack.add(previous)
                                },
                                modifier = Modifier.testTag("rich_number_button")
                            ) {
                                Icon(Icons.Default.FormatListNumbered, contentDescription = "Numbered List")
                            }

                            IconButton(
                                onClick = {
                                    val previous = textEditorValue
                                    textEditorValue = RichTextEditingSuite.insertQuoteMarkup(textEditorValue)
                                    richUndoStack.add(previous)
                                },
                                modifier = Modifier.testTag("rich_quote_button")
                            ) {
                                Icon(Icons.Default.FormatQuote, contentDescription = "Quote Block")
                            }

                            // Font size picker
                            Box {
                                IconButton(onClick = { showSizePicker = true }) {
                                    Icon(Icons.Filled.FormatSize, contentDescription = "Font Size")
                                }
                                DropdownMenu(expanded = showSizePicker, onDismissRequest = { showSizePicker = false }) {
                                    listOf(12, 14, 16, 18, 20, 24).forEach { size ->
                                        DropdownMenuItem(text = { Text("${size}sp") }, onClick = {
                                            val previous = textEditorValue
                                            textEditorValue = RichTextEditingSuite.applySpanStyle(textEditorValue, SpanStyle(fontSize = size.sp))
                                            richUndoStack.add(previous)
                                            showSizePicker = false
                                        })
                                    }
                                }
                            }

                            // Font family picker
                            Box {
                                IconButton(onClick = { showFamilyPicker = true }) {
                                    Icon(Icons.Default.FontDownload, contentDescription = "Fonts")
                                }
                                DropdownMenu(expanded = showFamilyPicker, onDismissRequest = { showFamilyPicker = false }) {
                                    DropdownMenuItem(text = { Text("Serif") }, onClick = {
                                        val previous = textEditorValue
                                        textEditorValue = RichTextEditingSuite.applySpanStyle(textEditorValue, SpanStyle(fontFamily = FontFamily.Serif))
                                        richUndoStack.add(previous)
                                        showFamilyPicker = false
                                    })
                                    DropdownMenuItem(text = { Text("Monospace") }, onClick = {
                                        val previous = textEditorValue
                                        textEditorValue = RichTextEditingSuite.applySpanStyle(textEditorValue, SpanStyle(fontFamily = FontFamily.Monospace))
                                        richUndoStack.add(previous)
                                        showFamilyPicker = false
                                    })
                                    DropdownMenuItem(text = { Text("Cursive") }, onClick = {
                                        val previous = textEditorValue
                                        textEditorValue = RichTextEditingSuite.applySpanStyle(textEditorValue, SpanStyle(fontFamily = FontFamily.Cursive))
                                        richUndoStack.add(previous)
                                        showFamilyPicker = false
                                    })
                                    DropdownMenuItem(text = { Text("System Default") }, onClick = {
                                        val previous = textEditorValue
                                        textEditorValue = RichTextEditingSuite.applySpanStyle(textEditorValue, SpanStyle(fontFamily = FontFamily.Default))
                                        richUndoStack.add(previous)
                                        showFamilyPicker = false
                                    })
                                }
                            }

                            // Text Align Dropdown
                            Box {
                                IconButton(onClick = { showAlignPicker = true }) {
                                    Icon(Icons.Default.FormatAlignLeft, contentDescription = "Alignment")
                                }
                                DropdownMenu(expanded = showAlignPicker, onDismissRequest = { showAlignPicker = false }) {
                                    DropdownMenuItem(text = { Text("Align Left") }, onClick = {
                                        val previous = textEditorValue
                                        textEditorValue = RichTextEditingSuite.applyParagraphAlignment(textEditorValue, TextAlign.Left)
                                        richUndoStack.add(previous)
                                        showAlignPicker = false
                                    })
                                    DropdownMenuItem(text = { Text("Align Center") }, onClick = {
                                        val previous = textEditorValue
                                        textEditorValue = RichTextEditingSuite.applyParagraphAlignment(textEditorValue, TextAlign.Center)
                                        richUndoStack.add(previous)
                                        showAlignPicker = false
                                    })
                                    DropdownMenuItem(text = { Text("Align Right") }, onClick = {
                                        val previous = textEditorValue
                                        textEditorValue = RichTextEditingSuite.applyParagraphAlignment(textEditorValue, TextAlign.Right)
                                        richUndoStack.add(previous)
                                        showAlignPicker = false
                                    })
                                }
                            }

                            // Text Color picker Grid PopUp
                            Box {
                                IconButton(onClick = { showColorGrid = true }) {
                                    Icon(Icons.Default.Palette, contentDescription = "Text Color")
                                }
                                DropdownMenu(expanded = showColorGrid, onDismissRequest = { showColorGrid = false }) {
                                    val colorsGrid = listOf(
                                        Color.Red, Color.Blue, Color.Green, Color.Yellow,
                                        Color.Magenta, Color.Cyan, Color.Black, Color.Gray,
                                        Color.DarkGray, Color.LightGray, Color(0xFF8B4513), Color(0xFF008080)
                                    )
                                    Column(modifier = Modifier.padding(8.dp).width(160.dp)) {
                                        Text("Select Text Color", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 6.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            colorsGrid.take(6).forEach { c ->
                                                Box(modifier = Modifier.size(20.dp).background(c, CircleShape).clickable {
                                                    val previous = textEditorValue
                                                    textEditorValue = RichTextEditingSuite.applySpanStyle(textEditorValue, SpanStyle(color = c))
                                                    richUndoStack.add(previous)
                                                    showColorGrid = false
                                                })
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            colorsGrid.drop(6).forEach { c ->
                                                Box(modifier = Modifier.size(20.dp).background(c, CircleShape).clickable {
                                                    val previous = textEditorValue
                                                    textEditorValue = RichTextEditingSuite.applySpanStyle(textEditorValue, SpanStyle(color = c))
                                                    richUndoStack.add(previous)
                                                    showColorGrid = false
                                                })
                                            }
                                        }
                                    }
                                }
                            }

                            // Undo / Redo
                            IconButton(
                                onClick = {
                                    if (richUndoStack.isNotEmpty()) {
                                        val last = richUndoStack.removeAt(richUndoStack.size - 1)
                                        richRedoStack.add(textEditorValue)
                                        textEditorValue = last
                                    }
                                },
                                enabled = richUndoStack.isNotEmpty(),
                                modifier = Modifier.testTag("rich_undo_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                            }

                            IconButton(
                                onClick = {
                                    if (richRedoStack.isNotEmpty()) {
                                        val next = richRedoStack.removeAt(richRedoStack.size - 1)
                                        richUndoStack.add(textEditorValue)
                                        textEditorValue = next
                                    }
                                },
                                enabled = richRedoStack.isNotEmpty(),
                                modifier = Modifier.testTag("rich_redo_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Scrollable TextField Editor Canvas
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                .padding(12.dp)
                        ) {
                            BasicTextField(
                                value = textEditorValue,
                                onValueChange = {
                                    val prev = textEditorValue
                                    if (it.text != prev.text || it.annotatedString != prev.annotatedString) {
                                        richUndoStack.add(prev)
                                        richRedoStack.clear()
                                    }
                                    textEditorValue = it
                                },
                                textStyle = TextStyle(
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("note_text_editor"),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences
                                )
                            )
                        }
                    }

                    "MARKDOWN" -> {
                        // Markdown utility toolbar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            fun wrapSelected(p: String, s: String) {
                                val text = markdownEditorValue.text
                                val sel = markdownEditorValue.selection
                                val chosen = text.substring(sel.start, sel.end)
                                val updatedText = text.substring(0, sel.start) + p + chosen + s + text.substring(sel.end)
                                val newCur = sel.start + p.length + chosen.length + s.length
                                markdownEditorValue = TextFieldValue(updatedText, TextRange(newCur, newCur))
                            }

                            IconButton(onClick = { wrapSelected("**", "**") }) {
                                Icon(Icons.Default.FormatBold, contentDescription = "Markdown Bold")
                            }
                            IconButton(onClick = { wrapSelected("*", "*") }) {
                                Icon(Icons.Default.FormatItalic, contentDescription = "Markdown Italic")
                            }
                            IconButton(onClick = {
                                val text = markdownEditorValue.text
                                val sel = markdownEditorValue.selection
                                val updatedText = text.substring(0, sel.start) + "\n# " + text.substring(sel.start)
                                markdownEditorValue = TextFieldValue(updatedText, TextRange(sel.start + 3, sel.start + 3))
                            }) {
                                Icon(Icons.Default.Title, contentDescription = "Markdown Header")
                            }
                            IconButton(onClick = { wrapSelected("[", "](url)") }) {
                                Icon(Icons.Default.Link, contentDescription = "Markdown Link")
                            }
                            IconButton(onClick = { wrapSelected("![alt](", ")") }) {
                                Icon(Icons.Default.Image, contentDescription = "Markdown Image")
                            }
                            IconButton(onClick = { wrapSelected("`", "`") }) {
                                Icon(Icons.Default.Code, contentDescription = "Markdown Code")
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // Preview Toggle Switch
                            Text("Preview", style = MaterialTheme.typography.labelSmall)
                            Switch(
                                checked = showMarkdownPreview,
                                onCheckedChange = { showMarkdownPreview = it },
                                modifier = Modifier.scale(0.8f).testTag("markdown_preview_toggle")
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val configuration = LocalConfiguration.current
                        val isLandscapeTablet = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE && configuration.screenWidthDp >= 600

                        if (isLandscapeTablet) {
                            // Split View layout on wide devices
                            Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                        .padding(12.dp)
                                ) {
                                    BasicTextField(
                                        value = markdownEditorValue,
                                        onValueChange = { markdownEditorValue = it },
                                        textStyle = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .testTag("note_markdown_editor")
                                    )
                                }

                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    MarkdownRenderer(
                                        markdown = markdownEditorValue.text.ifBlank { "*No preview available*" },
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                            .padding(12.dp)
                                    )
                                }
                            }
                        } else {
                            // Adaptive View layout toggled/split dynamically on non-tablet
                            if (showMarkdownPreview) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    MarkdownRenderer(
                                        markdown = markdownEditorValue.text.ifBlank { "*No markdown text typed*" },
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                            .padding(12.dp)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                        .padding(12.dp)
                                ) {
                                    BasicTextField(
                                        value = markdownEditorValue,
                                        onValueChange = { markdownEditorValue = it },
                                        textStyle = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .testTag("note_markdown_editor")
                                    )
                                }
                            }
                        }
                    }

                    "SKETCH" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            DrawingCanvas(
                                initialData = noteDrawingData,
                                onDrawingSaved = { updatedDrawingJson ->
                                    noteDrawingData = updatedDrawingJson
                                    if (!updatedDrawingJson.isNullOrBlank()) {
                                        try {
                                            val lists = kotlinx.serialization.json.Json.decodeFromString<List<DrawPathData>>(
                                                updatedDrawingJson
                                            )
                                            val pngPath = savePathsAsPng(context, lists)
                                            noteContentBlocksJson = pngPath
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    } else {
                                        noteContentBlocksJson = ""
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

// Simple modifier scale helper for switch
private fun Modifier.scale(s: Float): Modifier = this
