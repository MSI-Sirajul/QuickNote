package com.msi.quicknote.ui.screens

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import com.msi.quicknote.QuickNoteApp
import com.msi.quicknote.data.Note
import com.msi.quicknote.receiver.ReminderBroadcastReceiver
import com.msi.quicknote.ui.components.*
import com.msi.quicknote.ui.theme.NoteColorColors
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

    // Markdown properties undo/redo stacks
    val markdownUndoStack = remember { mutableStateListOf<TextFieldValue>() }
    val markdownRedoStack = remember { mutableStateListOf<TextFieldValue>() }

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
        // Ensure atomic completion of the database save before screen popped/disposed
        kotlinx.coroutines.runBlocking {
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
                        var showSizePicker by remember { mutableStateOf(false) }
                        var showStylePicker by remember { mutableStateOf(false) }
                        var showColorGrid by remember { mutableStateOf(false) }
                        var textAlignState by remember { mutableStateOf(TextAlign.Left) }
                        var currentSelectedSizeIndex by remember { mutableStateOf(1) } // 16sp
                        val fontSizes = listOf(14, 16, 20, 24)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
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

                            Spacer(modifier = Modifier.height(8.dp))

                            // Horizontal robust Editing Toolbar beneath the note content or at the bottom
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // 1. Toggle bold text
                                IconButton(
                                    onClick = {
                                        val previous = textEditorValue
                                        textEditorValue = RichTextEditingSuite.applySpanStyle(textEditorValue, SpanStyle(fontWeight = FontWeight.Bold))
                                        richUndoStack.add(previous)
                                        richRedoStack.clear()
                                    },
                                    modifier = Modifier.testTag("rich_bold_button")
                                ) {
                                    Icon(Icons.Default.FormatBold, contentDescription = "Bold")
                                }

                                // 2. Toggle italic text
                                IconButton(
                                    onClick = {
                                        val previous = textEditorValue
                                        textEditorValue = RichTextEditingSuite.applySpanStyle(textEditorValue, SpanStyle(fontStyle = FontStyle.Italic))
                                        richUndoStack.add(previous)
                                        richRedoStack.clear()
                                    },
                                    modifier = Modifier.testTag("rich_italic_button")
                                ) {
                                    Icon(Icons.Default.FormatItalic, contentDescription = "Italic")
                                }

                                // 3. Toggle underline text
                                IconButton(
                                    onClick = {
                                        val previous = textEditorValue
                                        textEditorValue = RichTextEditingSuite.applySpanStyle(textEditorValue, SpanStyle(textDecoration = TextDecoration.Underline))
                                        richUndoStack.add(previous)
                                        richRedoStack.clear()
                                    },
                                    modifier = Modifier.testTag("rich_underline_button")
                                ) {
                                    Icon(Icons.Default.FormatUnderlined, contentDescription = "Underline")
                                }

                                // 4. Toggle code block
                                IconButton(
                                    onClick = {
                                        val previous = textEditorValue
                                        textEditorValue = RichTextEditingSuite.applyCodeStyle(textEditorValue)
                                        richUndoStack.add(previous)
                                        richRedoStack.clear()
                                    },
                                    modifier = Modifier.testTag("rich_code_toggle_button")
                                ) {
                                    Icon(Icons.Default.Code, contentDescription = "Code Block")
                                }

                                // 5. Cycle alignments: Left, Center, Right
                                IconButton(
                                    onClick = {
                                        val previous = textEditorValue
                                        val (updated, nextAlign) = RichTextEditingSuite.cycleParagraphAlignment(textEditorValue, textAlignState)
                                        textEditorValue = updated
                                        textAlignState = nextAlign
                                        richUndoStack.add(previous)
                                        richRedoStack.clear()
                                    },
                                    modifier = Modifier.testTag("rich_align_button")
                                ) {
                                    val alignIcon = when (textAlignState) {
                                        TextAlign.Center -> Icons.Default.FormatAlignCenter
                                        TextAlign.Right -> Icons.Default.FormatAlignRight
                                        else -> Icons.Default.FormatAlignLeft
                                    }
                                    Icon(alignIcon, contentDescription = "Cycle Alignment")
                                }

                                // 6. Clear styles
                                IconButton(
                                    onClick = {
                                        val previous = textEditorValue
                                        textEditorValue = RichTextEditingSuite.clearStyles(textEditorValue)
                                        richUndoStack.add(previous)
                                        richRedoStack.clear()
                                    },
                                    modifier = Modifier.testTag("rich_clear_button")
                                ) {
                                    Icon(Icons.Default.FormatClear, contentDescription = "Clear Styles")
                                }

                                // 7. Format list bullet
                                IconButton(
                                    onClick = {
                                        val previous = textEditorValue
                                        textEditorValue = RichTextEditingSuite.insertListMarkup(textEditorValue, false)
                                        richUndoStack.add(previous)
                                        richRedoStack.clear()
                                    },
                                    modifier = Modifier.testTag("rich_bullet_button")
                                ) {
                                    Icon(Icons.Default.FormatListBulleted, contentDescription = "Bullet List")
                                }

                                // 8. Format checklist task item
                                IconButton(
                                    onClick = {
                                        val previous = textEditorValue
                                        textEditorValue = RichTextEditingSuite.insertChecklistMarkup(textEditorValue)
                                        richUndoStack.add(previous)
                                        richRedoStack.clear()
                                    },
                                    modifier = Modifier.testTag("rich_checklist_button")
                                ) {
                                    Icon(Icons.Outlined.CheckBox, contentDescription = "Checklist Item")
                                }

                                // 9. Increase text size
                                IconButton(
                                    onClick = {
                                        val previous = textEditorValue
                                        currentSelectedSizeIndex = (currentSelectedSizeIndex + 1) % fontSizes.size
                                        val size = fontSizes[currentSelectedSizeIndex]
                                        textEditorValue = RichTextEditingSuite.applySpanStyle(textEditorValue, SpanStyle(fontSize = size.sp))
                                        richUndoStack.add(previous)
                                        richRedoStack.clear()
                                    },
                                    modifier = Modifier.testTag("rich_size_button")
                                ) {
                                    Icon(Icons.Default.FormatSize, contentDescription = "Increase Text Size")
                                }

                                // 10. Color picker
                                Box {
                                    IconButton(
                                        onClick = { showColorGrid = true },
                                        modifier = Modifier.testTag("rich_color_button")
                                    ) {
                                        Icon(Icons.Default.Palette, contentDescription = "Text Color")
                                    }
                                    DropdownMenu(expanded = showColorGrid, onDismissRequest = { showColorGrid = false }) {
                                        val colorsGrid = listOf(
                                            Pair("Default", Color.Unspecified),
                                            Pair("Red", Color(0xFFE53935)),
                                            Pair("Orange", Color(0xFFFB8C00)),
                                            Pair("Yellow", Color(0xFFFDD835)),
                                            Pair("Green", Color(0xFF43A047)),
                                            Pair("Blue", Color(0xFF1E88E5)),
                                            Pair("Purple", Color(0xFF8E24AA)),
                                            Pair("Outline-Variant", Color(0xFF757575))
                                        )
                                        Column(modifier = Modifier.padding(8.dp).width(180.dp).testTag("rich_color_grid")) {
                                            Text(
                                                text = "Select Text Color",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(bottom = 8.dp)
                                            )
                                            colorsGrid.chunked(4).forEach { rowColors ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    rowColors.forEach { (name, c) ->
                                                        val displayColor = if (c == Color.Unspecified) MaterialTheme.colorScheme.onSurface else c
                                                        Box(
                                                            modifier = Modifier
                                                                .size(24.dp)
                                                                .clip(CircleShape)
                                                                .background(displayColor)
                                                                .clickable {
                                                                    val previous = textEditorValue
                                                                    textEditorValue = RichTextEditingSuite.applySpanStyle(textEditorValue, SpanStyle(color = c))
                                                                    richUndoStack.add(previous)
                                                                    richRedoStack.clear()
                                                                    showColorGrid = false
                                                                }
                                                                .border(1.dp, Color.LightGray, CircleShape)
                                                                .testTag("text_color_item_$name")
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                            }
                                        }
                                    }
                                }

                                // 11. Undo / Redo
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
                                val oldVal = markdownEditorValue
                                val chosen = text.substring(sel.start, sel.end)
                                val updatedText = text.substring(0, sel.start) + p + chosen + s + text.substring(sel.end)
                                val newCur = sel.start + p.length + chosen.length + s.length
                                markdownUndoStack.add(oldVal)
                                markdownRedoStack.clear()
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
                                val oldVal = markdownEditorValue
                                val updatedText = text.substring(0, sel.start) + "\n# " + text.substring(sel.start)
                                markdownUndoStack.add(oldVal)
                                markdownRedoStack.clear()
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

                            // Undo and Redo operations
                            IconButton(
                                onClick = {
                                    if (markdownUndoStack.isNotEmpty()) {
                                        val last = markdownUndoStack.removeAt(markdownUndoStack.size - 1)
                                        markdownRedoStack.add(markdownEditorValue)
                                        markdownEditorValue = last
                                    }
                                },
                                enabled = markdownUndoStack.isNotEmpty(),
                                modifier = Modifier.testTag("markdown_undo_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                            }

                            IconButton(
                                onClick = {
                                    if (markdownRedoStack.isNotEmpty()) {
                                        val next = markdownRedoStack.removeAt(markdownRedoStack.size - 1)
                                        markdownUndoStack.add(markdownEditorValue)
                                        markdownEditorValue = next
                                    }
                                },
                                enabled = markdownRedoStack.isNotEmpty(),
                                modifier = Modifier.testTag("markdown_redo_button")
                                ) {
                                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
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
                                        onValueChange = {
                                            val oldVal = markdownEditorValue
                                            if (it.text != oldVal.text) {
                                                markdownUndoStack.add(oldVal)
                                                markdownRedoStack.clear()
                                            }
                                            markdownEditorValue = it
                                        },
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
                                        onValueChange = {
                                            val oldVal = markdownEditorValue
                                            if (it.text != oldVal.text) {
                                                markdownUndoStack.add(oldVal)
                                                markdownRedoStack.clear()
                                            }
                                            markdownEditorValue = it
                                        },
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
