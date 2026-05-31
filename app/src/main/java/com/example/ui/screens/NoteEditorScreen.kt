package com.example.ui.screens

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.QuickNoteApp
import com.example.data.Note
import com.example.receiver.ReminderBroadcastReceiver
import com.example.ui.components.DrawingCanvas
import com.example.ui.components.RichTextEditorComponent
import com.example.ui.theme.NoteColorColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: Int, // Pass -1 if creating a new note
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as QuickNoteApp
    val repository = app.repository
    val prefs = app.preferencesManager
    val scope = rememberCoroutineScope()

    var initialized by remember { mutableStateOf(false) }

    // Forms fields
    var noteTitle by remember { mutableStateOf("") }
    var noteContentBlocksJson by remember { mutableStateOf("") }
    var noteFolderId by remember { mutableStateOf<Int?>(null) }
    var noteIsPinned by remember { mutableStateOf(false) }
    var noteColor by remember { mutableStateOf(0xFF1E293B.toInt()) }
    var noteIsLocked by remember { mutableStateOf(false) }
    var noteReminderTime by remember { mutableStateOf<Long?>(null) }
    var noteDrawingData by remember { mutableStateOf<String?>(null) }

    // UI States
    var currentMode by remember { mutableStateOf("Text") } // "Text" vs "Draw"
    var showFolderSelector by remember { mutableStateOf(false) }
    var isBiometricCheckingBeforeShowingLockedContent by remember { mutableStateOf(false) }

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

                // If note is locked, require auth first
                if (retrievedNote.isLocked) {
                    isBiometricCheckingBeforeShowingLockedContent = true
                }
            }
            initialized = true
        } else if (noteId == -1) {
            initialized = true
        }
    }

    // Alarm scheduler helpers
    fun scheduleAndroidReminder(timeInMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_SHOW_REMINDER
            putExtra(ReminderBroadcastReceiver.EXTRA_NOTE_ID, noteId)
            putExtra(ReminderBroadcastReceiver.EXTRA_NOTE_TITLE, noteTitle.ifBlank { "Task reminder" })
            putExtra(ReminderBroadcastReceiver.EXTRA_NOTE_CONTENT, "Open QuickNote to view note details.")
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            if (noteId == -1) 9999 else noteId, // Request Code
            intent,
            pendingIntentFlags
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Save notes
    fun saveNoteFlow() {
        val noteEntity = Note(
            id = if (noteId == -1) 0 else noteId,
            title = noteTitle,
            content = noteContentBlocksJson,
            folderId = noteFolderId,
            isPinned = noteIsPinned,
            color = noteColor,
            isLocked = noteIsLocked,
            reminderTime = noteReminderTime,
            drawingData = noteDrawingData,
            lastModified = System.currentTimeMillis()
        )

        scope.launch {
            if (noteId == -1) {
                val newId = repository.insertNote(noteEntity)
                // If reminder scheduled, schedule alarm with real new ID
                noteReminderTime?.let { time ->
                    val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
                        action = ReminderBroadcastReceiver.ACTION_SHOW_REMINDER
                        putExtra(ReminderBroadcastReceiver.EXTRA_NOTE_ID, newId.toInt())
                        putExtra(ReminderBroadcastReceiver.EXTRA_NOTE_TITLE, noteTitle.ifBlank { "Task reminder" })
                        putExtra(ReminderBroadcastReceiver.EXTRA_NOTE_CONTENT, "Open QuickNote to view details.")
                    }
                    val pi = PendingIntent.getBroadcast(
                        context,
                        newId.toInt(),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
                    } else {
                        am.setExact(AlarmManager.RTC_WAKEUP, time, pi)
                    }
                }
            } else {
                repository.updateNote(noteEntity)
                noteReminderTime?.let { scheduleAndroidReminder(it) }
            }
            onBack()
        }
    }

    val selectedFold = folders.find { it.id == noteFolderId }

    if (isBiometricCheckingBeforeShowingLockedContent && noteIsLocked) {
        // Force authentication before showing editor
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
                            onClick = onBack,
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
                                imageVector = if (noteIsPinned) Icons.Default.PushPin else Icons.Default.PushPin,
                                contentDescription = "Pin Note",
                                tint = if (noteIsPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (prefs.pin.isEmpty()) {
                                    // Let user set up a pin first
                                    noteIsLocked = false
                                } else {
                                    noteIsLocked = !noteIsLocked
                                }
                            },
                            modifier = Modifier.testTag("action_lock_note")
                        ) {
                            Icon(
                                imageVector = if (noteIsLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = "Lock Note Book",
                                tint = if (noteIsLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                            )
                        }

                        IconButton(
                            onClick = { saveNoteFlow() },
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
            containerColor = MaterialTheme.colorScheme.background,
            modifier = modifier
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                // Secondary Controls Row: Folder picker, Alarm Reminder, Color picker, mode switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Folder select button
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

                    // Schedule alarm date/timepicker
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
                                    contentDescription = "Clear Reminder",
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { noteReminderTime = null }
                                )
                            }
                        } else null,
                        modifier = Modifier.testTag("reminder_picker_chip")
                    )

                    // Card background colors picker
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

                // Editor Segment Toggler
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = currentMode == "Text",
                            onClick = { currentMode = "Text" },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Markdown Style")
                            }
                        }
                        SegmentedButton(
                            selected = currentMode == "Draw",
                            onClick = { currentMode = "Draw" },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sketches Canvas")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Mode Contents
                if (currentMode == "Text") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        RichTextEditorComponent(
                            initialBlocksJson = noteContentBlocksJson,
                            onBlocksChanged = { updatedJson ->
                                noteContentBlocksJson = updatedJson
                            }
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        DrawingCanvas(
                            initialData = noteDrawingData,
                            onDrawingSaved = { updatedDrawingJson ->
                                noteDrawingData = updatedDrawingJson
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
