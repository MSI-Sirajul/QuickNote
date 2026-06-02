package com.msi.quicknote.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.io.FileOutputStream

@Serializable
data class DrawPoint(val x: Float, val y: Float)

@Serializable
data class DrawPathData(val color: Int, val width: Float, val points: List<DrawPoint>)

// Utility function to convert Drawing Coordinates into a physical PNG in InternalStorage
fun savePathsAsPng(context: Context, paths: List<DrawPathData>): String {
    val bitmap = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    // Dark professional background
    canvas.drawColor(0xFF0F172A.toInt())
    
    val paint = AndroidPaint().apply {
        isAntiAlias = true
        style = AndroidPaint.Style.STROKE
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
    }
    
    paths.forEach { pathData ->
        if (pathData.points.size > 1) {
            val path = AndroidPath()
            path.moveTo(pathData.points[0].x, pathData.points[0].y)
            for (i in 1 until pathData.points.size) {
                path.lineTo(pathData.points[i].x, pathData.points[i].y)
            }
            paint.color = pathData.color
            paint.strokeWidth = pathData.width
            canvas.drawPath(path, paint)
        }
    }
    
    val dir = File(context.filesDir, "drawings")
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, "sketch_${System.currentTimeMillis()}.png")
    try {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        bitmap.recycle()
    }
    return file.absolutePath
}

@Composable
fun DrawingCanvas(
    initialData: String?,
    onDrawingSaved: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val paths = remember { mutableStateListOf<DrawPathData>() }
    val redoPaths = remember { mutableStateListOf<DrawPathData>() }

    // Load initial drawing data points
    LaunchedEffect(initialData) {
        if (!initialData.isNullOrBlank()) {
            try {
                val decoded = Json.decodeFromString(ListSerializer(DrawPathData.serializer()), initialData)
                paths.clear()
                paths.addAll(decoded)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    var currentColor by remember { mutableStateOf(0xFF38BDF8.toInt()) }
    var currentWidth by remember { mutableStateOf(8f) }
    var currentPathPoints = remember { mutableStateListOf<DrawPoint>() }

    val palette = listOf(
        0xFF0F172A.toInt(), // Eraser / Board Color
        0xFF38BDF8.toInt(), // Sky blue
        0xFFF87171.toInt(), // Coral
        0xFF34D399.toInt(), // Emerald
        0xFFFBBF24.toInt(), // Amber
        0xFFA78BFA.toInt(), // Purple
        0xFFFFFFFF.toInt()  // White
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                .testTag("drawing_canvas_card"),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentPathPoints.clear()
                                currentPathPoints.add(DrawPoint(offset.x, offset.y))
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                currentPathPoints.add(DrawPoint(change.position.x, change.position.y))
                            },
                            onDragEnd = {
                                if (currentPathPoints.isNotEmpty()) {
                                    val newPath = DrawPathData(
                                        color = currentColor,
                                        width = currentWidth,
                                        points = currentPathPoints.toList()
                                    )
                                    paths.add(newPath)
                                    redoPaths.clear() // Clear redo history upon drawing new line
                                    currentPathPoints.clear()
                                    
                                    val serialized = Json.encodeToString(ListSerializer(DrawPathData.serializer()), paths.toList())
                                    onDrawingSaved(serialized)
                                }
                            }
                        )
                    }
                    .testTag("drawing_canvas")
            ) {
                paths.forEach { pathData ->
                    if (pathData.points.size > 1) {
                        val path = Path().apply {
                            moveTo(pathData.points[0].x, pathData.points[0].y)
                            for (i in 1 until pathData.points.size) {
                                lineTo(pathData.points[i].x, pathData.points[i].y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = Color(pathData.color),
                            style = Stroke(
                                width = pathData.width,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }

                if (currentPathPoints.size > 1) {
                    val path = Path().apply {
                        moveTo(currentPathPoints[0].x, currentPathPoints[0].y)
                        for (i in 1 until currentPathPoints.size) {
                            lineTo(currentPathPoints[i].x, currentPathPoints[i].y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = Color(currentColor),
                        style = Stroke(
                            width = currentWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Colors list
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                palette.forEach { colorInt ->
                    val isSelected = currentColor == colorInt
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 36.dp else 28.dp)
                            .clip(CircleShape)
                            .background(Color(colorInt))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                shape = CircleShape
                            )
                            .clickable(onClickLabel = "Select brush color") {
                                currentColor = colorInt
                            }
                    )
                }
            }

            // Undo / Redo / Clear Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (paths.isNotEmpty()) {
                            val removed = paths.removeAt(paths.size - 1)
                            redoPaths.add(removed)
                            val serialized = if (paths.isEmpty()) null else Json.encodeToString(
                                ListSerializer(DrawPathData.serializer()),
                                paths.toList()
                            )
                            onDrawingSaved(serialized)
                        }
                    },
                    modifier = Modifier.testTag("canvas_undo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo stroke"
                    )
                }

                IconButton(
                    onClick = {
                        if (redoPaths.isNotEmpty()) {
                            val restored = redoPaths.removeAt(redoPaths.size - 1)
                            paths.add(restored)
                            val serialized = Json.encodeToString(
                                ListSerializer(DrawPathData.serializer()),
                                paths.toList()
                            )
                            onDrawingSaved(serialized)
                        }
                    },
                    modifier = Modifier.testTag("canvas_redo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo stroke"
                    )
                }

                IconButton(
                    onClick = {
                        paths.clear()
                        redoPaths.clear()
                        onDrawingSaved(null)
                    },
                    modifier = Modifier.testTag("canvas_clear_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear Canvas"
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Brush Size:", style = MaterialTheme.typography.labelSmall)
            Slider(
                value = currentWidth,
                onValueChange = { currentWidth = it },
                valueRange = 2f..32f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp).testTag("brush_width_slider")
            )
        }
    }
}
