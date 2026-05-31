package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Environment
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream

data class DrawPath(
    val path: Path,
    val color: Color,
    val strokeWidth: Float,
    val rawPoints: List<android.graphics.PointF> // Needed to output into native saving mechanism
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrawingCanvas(
    onSketchSaved: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val paths = remember { mutableStateListOf<DrawPath>() }
    val redoPaths = remember { mutableStateListOf<DrawPath>() }

    var currentColor by remember { mutableStateOf(Color.Black) }
    var currentStrokeWidth by remember { mutableStateOf(10f) }

    var currentPathPoints = remember { mutableStateListOf<android.graphics.PointF>() }
    var currentPath = remember { Path() }

    val colors = listOf(
        Color.Black, Color.Red, Color.Blue, Color.Green, Color.Yellow, 
        Color(0xFFFF5722), Color(0xFF9C27B0), Color(0xFF00BCD4)
    )

    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header Action controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hand-Drawing Canvas",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Row {
                    IconButton(
                        onClick = {
                            if (paths.isNotEmpty()) {
                                val removed = paths.removeAt(paths.lastIndex)
                                redoPaths.add(removed)
                            }
                        },
                        enabled = paths.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo")
                    }

                    IconButton(
                        onClick = {
                            if (redoPaths.isNotEmpty()) {
                                val restored = redoPaths.removeAt(redoPaths.lastIndex)
                                paths.add(restored)
                            }
                        },
                        enabled = redoPaths.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Redo")
                    }

                    IconButton(onClick = { paths.clear(); redoPaths.clear() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear all")
                    }
                }
            }

            // Canvas drawing pane
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentPath = Path()
                                currentPath.moveTo(offset.x, offset.y)
                                currentPathPoints.clear()
                                currentPathPoints.add(android.graphics.PointF(offset.x, offset.y))
                                redoPaths.clear()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val position = change.position
                                currentPath.lineTo(position.x, position.y)
                                currentPathPoints.add(android.graphics.PointF(position.x, position.y))

                                // Dynamic list refresh
                                val tempPath = DrawPath(
                                    path = Path().apply { addPath(currentPath) },
                                    color = currentColor,
                                    strokeWidth = currentStrokeWidth,
                                    rawPoints = currentPathPoints.toList()
                                )
                                if (paths.isNotEmpty() && currentPathPoints.size > 1) {
                                    paths.removeAt(paths.lastIndex)
                                }
                                paths.add(tempPath)
                            },
                            onDragEnd = {
                                currentPathPoints.clear()
                            }
                        )
                    }
            ) {
                ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                    paths.forEach { drawPath ->
                        drawPath(
                            path = drawPath.path,
                            color = drawPath.color,
                            style = Stroke(
                                width = drawPath.strokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stroke and paint controls
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 8
            ) {
                colors.forEach { color ->
                    val isSelected = currentColor == color
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                shape = CircleShape
                            )
                            .clickable { currentColor = color }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Stroke size slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Create, contentDescription = "Stroke width")
                Spacer(modifier = Modifier.width(8.dp))
                Slider(
                    value = currentStrokeWidth,
                    onValueChange = { currentStrokeWidth = it },
                    valueRange = 2f..40f,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors()
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cancel")
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = {
                        // EXPORT TO BITMAP AND FILE
                        val directory = File(context.filesDir, "attachments").apply { mkdirs() }
                        val file = File(directory, "sketch_${System.currentTimeMillis()}.png")

                        // Render onto Bitmap
                        val defaultWidth = 1080
                        val defaultHeight = 1440
                        val bitmap = Bitmap.createBitmap(defaultWidth, defaultHeight, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(android.graphics.Color.WHITE)

                        val paint = Paint().apply {
                            isAntiAlias = true
                            style = Paint.Style.STROKE
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                        }

                        paths.forEach { drawPath ->
                            paint.color = drawPath.color.toArgb()
                            paint.strokeWidth = drawPath.strokeWidth * 2.5f // scale to bitmap resolution
                            canvas.drawPath(drawPath.path.asAndroidPath(), paint)
                        }

                        try {
                            FileOutputStream(file).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                            }
                            onSketchSaved(file.absolutePath)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                ) {
                    Icon(Icons.Default.Done, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save Sketch")
                }
            }
        }
    }
}
