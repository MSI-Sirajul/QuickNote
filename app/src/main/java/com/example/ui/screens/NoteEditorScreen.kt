package com.example.ui.screens

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.QuickNoteApp
import com.example.data.*
import com.example.receiver.ReminderBroadcastReceiver
import com.example.ui.components.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.*

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as QuickNoteApp
    val repository = app.repository
    val scope = rememberCoroutineScope()
    val fontSizeMultiplier by app.preferencesManager.fontSizeScaleFlow.collectAsState(initial = 1.0f)

    // Database objects
    var note by remember { mutableStateOf<Note?>(null) }
    val allFolders by repository.getAllFolders().collectAsState(initial = emptyList())
    val allTags by repository.getAllTags().collectAsState(initial = emptyList())
    val noteTags by repository.getTagsForNoteFlow(noteId).collectAsState(initial = emptyList())
    val historySnapshots by repository.getSnapshotsForNote(noteId).collectAsState(initial = emptyList())

    // Local editor controller states
    var titleText by remember { mutableStateOf("") }
    var noteBlocks by remember { mutableStateOf<List<EditorBlock>>(emptyList()) }
    var selectedFolderId by remember { mutableStateOf<Long?>(null) }
    var isPinnedState by remember { mutableStateOf(false) }
    var isFavoriteState by remember { mutableStateOf(false) }
    var selectedColorHex by remember { mutableStateOf("#FFFFFF") }

    // Media & attachments states
    var attachedImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var audioNotePath by remember { mutableStateOf<String?>(null) }
    var fileAttachments by remember { mutableStateOf<List<String>>(emptyList()) }

    var ocrTextStr by remember { mutableStateOf<String?>(null) }

    // UI modals visibility
    var showFolderMenu by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var showSnapshotSheet by remember { mutableStateOf(false) }
    var showDrawingCanvas by remember { mutableStateOf(false) }

    // Audio recording controllers
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var isRecordingAudio by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlayingAudio by remember { mutableStateOf(false) }

    // Multi-select colors
    val noteColors = listOf("#FFFFFF", "#FFF9C4", "#FFCCBC", "#C8E6C9", "#B3E5FC", "#FFCDD2")

    // Loader core Note details on start
    LaunchedEffect(noteId) {
        if (noteId == 0L) {
            note = Note(id = 0, title = "", content = serializeBlocks(listOf(EditorBlock(BlockType.PARAGRAPH, ""))))
            titleText = ""
            noteBlocks = listOf(EditorBlock(BlockType.PARAGRAPH, ""))
            selectedFolderId = null
            isPinnedState = false
            isFavoriteState = false
            selectedColorHex = "#FFFFFF"
            attachedImages = emptyList()
            audioNotePath = null
            fileAttachments = emptyList()
            ocrTextStr = null
        } else {
            val dbNote = repository.getNoteById(noteId)
            if (dbNote != null) {
                note = dbNote
                titleText = dbNote.title
                noteBlocks = deserializeBlocks(dbNote.content)
                selectedFolderId = dbNote.folderId
                isPinnedState = dbNote.isPinned
                isFavoriteState = dbNote.isFavorite
                selectedColorHex = dbNote.colorHex
                ocrTextStr = dbNote.ocrText
                attachedImages = if (dbNote.imagePaths.isEmpty()) emptyList() else dbNote.imagePaths.split(",")
                audioNotePath = dbNote.audioPath
                fileAttachments = if (dbNote.fileAttachments.isEmpty()) emptyList() else dbNote.fileAttachments.split(",")
            }
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val directory = File(context.filesDir, "attachments").apply { mkdirs() }
            val file = File(directory, "camera_${System.currentTimeMillis()}.jpg")
            try {
                FileOutputStream(file).use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                }
                attachedImages = attachedImages + file.absolutePath
                Toast.makeText(context, "Photo attached successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                // Copy stream to sandboxed filesDir
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val directory = File(context.filesDir, "attachments").apply { mkdirs() }
                    val file = File(directory, "gallery_${System.currentTimeMillis()}.png")
                    FileOutputStream(file).use { out ->
                        stream.copyTo(out)
                    }
                    attachedImages = attachedImages + file.absolutePath
                    Toast.makeText(context, "Image imported successfully!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // File attachments picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val directory = File(context.filesDir, "attachments").apply { mkdirs() }
                    val file = File(directory, "doc_${System.currentTimeMillis()}.pdf")
                    FileOutputStream(file).use { out ->
                        stream.copyTo(out)
                    }
                    fileAttachments = fileAttachments + file.absolutePath
                    Toast.makeText(context, "Attachment added: ${file.name}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveNote(createSnapshotFlag: Boolean = true) {
        val sBlocks = serializeBlocks(noteBlocks)
        val currentNote = note ?: return

        val noteToSave = currentNote.copy(
            title = titleText,
            content = sBlocks,
            folderId = selectedFolderId,
            isPinned = isPinnedState,
            isFavorite = isFavoriteState,
            colorHex = selectedColorHex,
            imagePaths = attachedImages.joinToString(","),
            audioPath = audioNotePath,
            fileAttachments = fileAttachments.joinToString(","),
            ocrText = ocrTextStr,
            lastEdited = System.currentTimeMillis()
        )

        scope.launch {
            val savedId = repository.insertOrUpdateNote(noteToSave, createSnapshot = createSnapshotFlag)
            note = noteToSave.copy(id = savedId)
            Toast.makeText(context, "Note saved successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    // Audio recording actions
    fun startAudioRecording() {
        val directory = File(context.filesDir, "attachments").apply { mkdirs() }
        val audioFile = File(directory, "audio_note_${System.currentTimeMillis()}.3gp")
        audioNotePath = audioFile.absolutePath

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(audioFile.absolutePath)
            try {
                prepare()
                start()
                isRecordingAudio = true
                Toast.makeText(context, "Recording started...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Failed to capture microphone: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun stopAudioRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder = null
        isRecordingAudio = false
        Toast.makeText(context, "Recording saved!", Toast.LENGTH_SHORT).show()
        saveNote()
    }

    fun playAudioNote() {
        val path = audioNotePath ?: return
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(path)
                prepare()
                start()
                isPlayingAudio = true
                setOnCompletionListener {
                    isPlayingAudio = false
                    mediaPlayer?.release()
                    mediaPlayer = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Playback error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun stopPlayingAudioNote() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaPlayer = null
        isPlayingAudio = false
    }

    // Alarm Reminders scheduler
    fun scheduleAlarmReminder(year: Int, month: Int, day: Int, hour: Int, minute: Int) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }

        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            Toast.makeText(context, "Selected alarm lies in the past.", Toast.LENGTH_SHORT).show()
            return
        }

        val noteToSave = note?.copy(title = titleText, lastEdited = System.currentTimeMillis()) ?: return
        scope.launch {
            val savedId = if (noteToSave.id == 0L) {
                repository.insertOrUpdateNote(noteToSave)
            } else {
                noteToSave.id
            }

            // Save Reminder Row
            val reminder = Reminder(noteId = savedId, triggerTime = calendar.timeInMillis)
            repository.insertReminder(reminder)

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
                putExtra("note_id", savedId)
                putExtra("note_title", titleText.ifBlank { "Uncaptioned Note" })
                putExtra("note_content", "Reminder triggered for your task items!")
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                savedId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }

            Toast.makeText(context, "Reminder alarm scheduled cleanly!", Toast.LENGTH_LONG).show()
        }
    }

    if (showDrawingCanvas) {
        DrawingCanvas(
            onSketchSaved = { filePath ->
                attachedImages = attachedImages + filePath
                showDrawingCanvas = false
                saveNote()
            },
            onDismiss = { showDrawingCanvas = false }
        )
    } else {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Note Editor", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Favorite toggle
                        IconButton(onClick = { isFavoriteState = !isFavoriteState; saveNote() }) {
                            Icon(
                                imageVector = if (isFavoriteState) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Favorite note",
                                tint = if (isFavoriteState) Color.Red else Color.Unspecified
                            )
                        }

                        // Pin toggle
                        IconButton(onClick = { isPinnedState = !isPinnedState; saveNote() }) {
                            Icon(
                                imageVector = if (isPinnedState) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                tint = if (isPinnedState) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                contentDescription = "Pin note"
                            )
                        }

                        // Version history snapshot restore trigger
                        IconButton(onClick = { showSnapshotSheet = true }) {
                            Icon(Icons.Default.History, contentDescription = "Snapshots history")
                        }

                        // Save Note Action
                        IconButton(onClick = { saveNote() }) {
                            Icon(Icons.Default.Save, contentDescription = "Save Note data")
                        }
                    }
                )
            }
        ) { innerPadding ->
            val screenBg = if (selectedColorHex == "#FFFFFF") {
                Color.Transparent
            } else {
                try {
                    Color(android.graphics.Color.parseColor(selectedColorHex)).copy(alpha = 0.65f)
                } catch (e: Exception) {
                    Color.Transparent
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(screenBg)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // Title input field text
                TextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    placeholder = { Text("Give Note Title...", style = MaterialTheme.typography.headlineMedium) },
                    textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Row of Accent Color options picker
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Card Variant:", style = MaterialTheme.typography.labelMedium)
                    noteColors.forEach { hex ->
                        val rgb = Color(android.graphics.Color.parseColor(hex))
                        val isSelected = hex == selectedColorHex
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(rgb)
                                .clickable { selectedColorHex = hex; saveNote(false) }
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                    shape = CircleShape
                                )
                        )
                    }
                }

                // Assigned Folder Breadcrumb Dropdown Selection
                Box(modifier = Modifier.wrapContentSize()) {
                    val folderLabel = allFolders.find { it.id == selectedFolderId }?.name ?: "Assign to Folder Notebook"
                    AssistChip(
                        onClick = { showFolderMenu = true },
                        label = { Text(folderLabel) },
                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) }
                    )

                    DropdownMenu(
                        expanded = showFolderMenu,
                        onDismissRequest = { showFolderMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("[ No folder - root ]") },
                            onClick = {
                                selectedFolderId = null
                                showFolderMenu = false
                                saveNote()
                            }
                        )
                        allFolders.forEach { f ->
                            DropdownMenuItem(
                                text = { Text(f.name) },
                                onClick = {
                                    selectedFolderId = f.id
                                    showFolderMenu = false
                                    saveNote()
                                }
                            )
                        }
                    }
                }

                // Tags selection chips layout Row
                Text("Associated Tags:", style = MaterialTheme.typography.labelSmall)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    noteTags.forEach { tag ->
                        SuggestionChip(
                            onClick = {
                                scope.launch {
                                    repository.removeTagFromNote(noteId, tag.id)
                                    Toast.makeText(context, "Tag removed: ${tag.name}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            label = { Text(tag.name) }
                        )
                    }

                    AssistChip(
                        onClick = { showTagDialog = true },
                        label = { Text("+ Add Tag") },
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    )
                }

                Divider()

                // Tool drawer: camera, audio recording, sketch, files selection Row
                Text("Rich attachments toolkit:", style = MaterialTheme.typography.labelSmall)
                ScrollableTabRow(
                    selectedTabIndex = 0,
                    edgePadding = 0.dp,
                    divider = {},
                    indicator = {},
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AssistChip(
                        onClick = { cameraLauncher.launch(null) },
                        label = { Text("Take Camera") },
                        leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                        modifier = Modifier.padding(end = 4.dp)
                    )

                    AssistChip(
                        onClick = { galleryLauncher.launch("image/*") },
                        label = { Text("Add Gallery") },
                        leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                        modifier = Modifier.padding(end = 4.dp)
                    )

                    AssistChip(
                        onClick = { showDrawingCanvas = true },
                        label = { Text("Sketch Canvas") },
                        leadingIcon = { Icon(Icons.Default.Gesture, contentDescription = null) },
                        modifier = Modifier.padding(end = 4.dp)
                    )

                    AssistChip(
                        onClick = { filePickerLauncher.launch("application/pdf") },
                        label = { Text("Select Document") },
                        leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null) },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }

                // Audio recording panel widget card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Voice Dictation Note Recorder", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = if (isRecordingAudio) "🎤 Recording is active..." else if (audioNotePath != null) "✅ Pre-recorded audio clip ready" else "No voice memo attached",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isRecordingAudio) {
                                IconButton(onClick = { stopAudioRecording() }) {
                                    Icon(Icons.Default.Stop, contentDescription = "Stop mic input")
                                }
                            } else {
                                IconButton(onClick = { startAudioRecording() }) {
                                    Icon(Icons.Default.Mic, contentDescription = "Start microphone")
                                }
                            }

                            if (audioNotePath != null) {
                                if (isPlayingAudio) {
                                    IconButton(onClick = { stopPlayingAudioNote() }) {
                                        Icon(Icons.Default.Pause, contentDescription = "Pause play clip")
                                    }
                                } else {
                                    IconButton(onClick = { playAudioNote() }) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Audio note playback")
                                    }
                                }
                            }
                        }
                    }
                }

                // Alarm reminders panel widget
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Calendar Alarm Reminders Selector", style = MaterialTheme.typography.titleSmall)
                            Text("Set real-time schedule alarms & notification tasks", style = MaterialTheme.typography.bodySmall)
                        }

                        IconButton(
                            onClick = {
                                val c = Calendar.getInstance()
                                DatePickerDialog(context, { _, year, month, day ->
                                    TimePickerDialog(context, { _, hour, minute ->
                                        scheduleAlarmReminder(year, month, day, hour, minute)
                                    }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show()
                                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                            }
                        ) {
                            Icon(Icons.Default.AddAlarm, contentDescription = "Calendar Picker clock")
                        }
                    }
                }

                // Trigger Offline OCR simulation
                if (attachedImages.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("On-Device OCR Analyzer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (!ocrTextStr.isNullOrBlank()) "OCR analysis complete! Indexed for filters." else "Extract texts from attached sketches/photos",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            IconButton(
                                onClick = {
                                    val curNote = note
                                    if (curNote != null) {
                                        scope.launch {
                                            val ocrNote = repository.runOcrOnNoteImages(curNote)
                                            note = ocrNote
                                            ocrTextStr = ocrNote.ocrText
                                            Toast.makeText(context, "OCR index refreshed cleanly!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.DocumentScanner, contentDescription = "Run text recogniser")
                            }
                        }
                    }
                }

                // Document exporters widget
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(0.4f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Export Single Note Document", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("TXT", "Markdown", "PDF").forEach { label ->
                                FilledTonalButton(
                                    onClick = {
                                        val curNote = note
                                        if (curNote != null) {
                                            scope.launch {
                                                val file = repository.exportNoteAsFile(curNote, label)
                                                Toast.makeText(context, "Exported note file to Cache: ${file.name}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(label, fontSize = 11.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                // Attached images list view if present
                if (attachedImages.isNotEmpty()) {
                    Text("Media Attachments Checklist:", style = MaterialTheme.typography.labelSmall)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        attachedImages.forEach { path ->
                            Card(
                                modifier = Modifier.size(100.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(32.dp), tint = Color.Gray)
                                    }
                                    
                                    IconButton(
                                        onClick = {
                                            attachedImages = attachedImages - path
                                            saveNote()
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(24.dp)
                                            .background(Color.White, CircleShape)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Divider()

                // Render main RichText blocks
                RichTextEditorComponent(
                    blocks = noteBlocks,
                    onBlocksChanged = { updatedList ->
                        noteBlocks = updatedList
                        // Autominimize database saving delays
                    },
                    modifier = Modifier.fillMaxWidth(),
                    fontSizeMultiplier = fontSizeMultiplier
                )

                Spacer(modifier = Modifier.height(48.dp))
            }
        }

        // Tag management dialog
        if (showTagDialog) {
            var newTagName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showTagDialog = false },
                title = { Text("Assign Tags Category") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("List of all active labels:", style = MaterialTheme.typography.bodyMedium)
                        
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            allTags.forEach { parentTag ->
                                val joined = noteTags.any { it.id == parentTag.id }
                                FilterChip(
                                    selected = joined,
                                    onClick = {
                                        scope.launch {
                                            if (joined) {
                                                repository.removeTagFromNote(noteId, parentTag.id)
                                            } else {
                                                repository.addTagToNote(noteId, parentTag.id)
                                            }
                                        }
                                    },
                                    label = { Text(parentTag.name) }
                                )
                            }
                        }

                        Divider()

                        OutlinedTextField(
                            value = newTagName,
                            onValueChange = { newTagName = it },
                            label = { Text("Create custom tag label") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newTagName.isNotBlank()) {
                                scope.launch {
                                    val tagId = repository.insertTag(Tag(name = newTagName))
                                    repository.addTagToNote(noteId, tagId)
                                    newTagName = ""
                                    Toast.makeText(context, "New label attached!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Text("Create & Join")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTagDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        // Bottom Sheet Snapshot restorers
        if (showSnapshotSheet) {
            AlertDialog(
                onDismissRequest = { showSnapshotSheet = false },
                title = { Text("History Snapshots Rollbacks") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (historySnapshots.isEmpty()) {
                            Text("No past edits recorded for this document yet.", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            historySnapshots.forEachIndexed { i, snap ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            titleText = snap.title
                                            noteBlocks = deserializeBlocks(snap.content)
                                            showSnapshotSheet = false
                                            Toast
                                                .makeText(context, "Note contents reverted to past Snapshot!", Toast.LENGTH_SHORT)
                                                .show()
                                            saveNote(false) // Save note state
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Snapshot #${i + 1}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            "Saved: " + java.text.SimpleDateFormat.getInstance().format(snap.timestamp),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Preview title: \"${snap.title.ifBlank { "Untitled" }}\"",
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showSnapshotSheet = false }) {
                        Text("Dismiss")
                    }
                }
            )
        }
    }
}
