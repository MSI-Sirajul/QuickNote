package com.example.ui.components

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class DrawPoint(val x: Float, val y: Float)

@Serializable
data class DrawPathData(val color: Int, val width: Float, val points: List<DrawPoint>)

@Composable
fun DrawingCanvas(
    initialData: String?,
    onDrawingSaved: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var paths = remember {
        mutableStateListOf<DrawPathData>()
    }

    // Load initial data
    LaunchedEffect(initialData) {
        if (!initialData.isNullOrBlank() && paths.isEmpty()) {
            try {
                val decoded = Json.decodeFromString(ListSerializer(DrawPathData.serializer()), initialData)
                paths.addAll(decoded)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    var currentColor by remember { mutableStateOf(0xFF38BDF8.toInt()) }
    var currentWidth by remember { mutableStateOf(8f) }
    var currentPathPoints = remember { mutableStateListOf<DrawPoint>() }

    // Colors list for palette
    val palette = listOf(
        0xFFFFFFF.toInt(), // White / Eraser (or select clear drawing coordinate instead)
        0xFF38BDF8.toInt(), // Sky blue
        0xFFF87171.toInt(), // Coral
        0xFF34D399.toInt(), // Emerald
        0xFFFBBF24.toInt(), // Amber
        0xFFA78BFA.toInt(), // Purple
        0xFF000000.toInt()  // Black
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
                                    paths.add(
                                        DrawPathData(
                                            color = currentColor,
                                            width = currentWidth,
                                            points = currentPathPoints.toList()
                                        )
                                    )
                                    currentPathPoints.clear()
                                    // Callback
                                    val serialized = Json.encodeToString(ListSerializer(DrawPathData.serializer()), paths.toList())
                                    onDrawingSaved(serialized)
                                }
                            }
                        )
                    }
                    .testTag("drawing_canvas")
            ) {
                // Draw historical paths
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

                // Draw active path dynamically
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

        // Controllers row
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

            // Undo / Clear Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (paths.isNotEmpty()) {
                            paths.removeLast()
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
                        contentDescription = "Undo Drawing Stroke"
                    )
                }

                IconButton(
                    onClick = {
                        paths.clear()
                        onDrawingSaved(null)
                    },
                    modifier = Modifier.testTag("canvas_clear_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear Entire Drawing"
                    )
                }
            }
        }

        // Stroke Width Selector
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
