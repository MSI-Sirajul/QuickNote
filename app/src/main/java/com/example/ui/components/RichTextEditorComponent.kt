package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.EditorBlock
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID

fun serializeBlocks(blocks: List<EditorBlock>): String {
    return try {
        Json.encodeToString(ListSerializer(EditorBlock.serializer()), blocks)
    } catch (e: Exception) {
        ""
    }
}

fun deserializeBlocks(serialized: String): List<EditorBlock> {
    if (serialized.isBlank()) return emptyList()
    return try {
        Json.decodeFromString(ListSerializer(EditorBlock.serializer()), serialized)
    } catch (e: Exception) {
        // Fallback: treat as plain paragraph note content
        listOf(EditorBlock(id = UUID.randomUUID().toString(), type = "paragraph", text = serialized))
    }
}

@Composable
fun RichTextEditorComponent(
    initialBlocksJson: String,
    onBlocksChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var blocks = remember { 
        mutableStateListOf<EditorBlock>().apply {
            addAll(deserializeBlocks(initialBlocksJson))
            if (isEmpty()) {
                add(EditorBlock(UUID.randomUUID().toString(), "paragraph", ""))
            }
        }
    }

    // Trigger update handler
    fun triggerUpdate() {
        val json = serializeBlocks(blocks.toList())
        onBlocksChanged(json)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Toolbar for adding block types
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    blocks.add(EditorBlock(UUID.randomUUID().toString(), "paragraph", ""))
                    triggerUpdate()
                },
                modifier = Modifier.testTag("add_para_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Notes,
                    contentDescription = "Add Text Paragraph",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = {
                    blocks.add(EditorBlock(UUID.randomUUID().toString(), "heading", ""))
                    triggerUpdate()
                },
                modifier = Modifier.testTag("add_heading_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Title,
                    contentDescription = "Add Large Heading",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = {
                    blocks.add(EditorBlock(UUID.randomUUID().toString(), "bullet", ""))
                    triggerUpdate()
                },
                modifier = Modifier.testTag("add_bullet_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FormatListBulleted,
                    contentDescription = "Add Bullet Point",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = {
                    blocks.add(EditorBlock(UUID.randomUUID().toString(), "todo", ""))
                    triggerUpdate()
                },
                modifier = Modifier.testTag("add_todo_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = "Add Checkbox Item",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Blocks container
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            blocks.forEachIndexed { index, block ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Block indicator (e.g. checkbox state or bullet or big T for heading)
                    when (block.type) {
                        "heading" -> {
                            Box(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "H",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        "bullet" -> {
                            Box(
                                modifier = Modifier
                                    .padding(end = 12.dp, start = 6.dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                        "todo" -> {
                            Checkbox(
                                checked = block.isChecked,
                                onCheckedChange = { checked ->
                                    blocks[index] = block.copy(isChecked = checked)
                                    triggerUpdate()
                                },
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .testTag("todo_block_checkbox_${block.id}")
                            )
                        }
                        else -> {
                            // Default paragraph: silent spacer
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }

                    // Content Field
                    TextField(
                        value = block.text,
                        onValueChange = { newText ->
                            blocks[index] = block.copy(text = newText)
                            triggerUpdate()
                        },
                        placeholder = {
                            Text(
                                text = when (block.type) {
                                    "heading" -> "Heading..."
                                    "bullet" -> "Bullet list item..."
                                    "todo" -> "Task list item..."
                                    else -> "Write text here..."
                                },
                                style = if (block.type == "heading") MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
                            )
                        },
                        textStyle = if (block.type == "heading") {
                            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("block_text_field_${block.id}"),
                        singleLine = block.type == "heading"
                    )

                    // Remove block button
                    IconButton(
                        onClick = {
                            blocks.removeAt(index)
                            if (blocks.isEmpty()) {
                                blocks.add(EditorBlock(UUID.randomUUID().toString(), "paragraph", ""))
                            }
                            triggerUpdate()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("remove_block_button_${block.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete this block",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
