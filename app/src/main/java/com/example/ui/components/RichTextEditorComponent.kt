package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

enum class BlockType {
    PARAGRAPH, H1, H2, H3, BULLET, NUMBERED, CHECKLIST, CODE, QUOTE
}

data class EditorBlock(
    val type: BlockType,
    var text: String,
    var isChecked: Boolean = false
)

fun serializeBlocks(blocks: List<EditorBlock>): String {
    val arr = JSONArray()
    for (b in blocks) {
        val obj = JSONObject().apply {
            put("type", b.type.name)
            put("text", b.text)
            put("isChecked", b.isChecked)
        }
        arr.put(obj)
    }
    return arr.toString()
}

fun deserializeBlocks(rawString: String): List<EditorBlock> {
    val list = mutableListOf<EditorBlock>()
    if (rawString.isBlank()) {
        list.add(EditorBlock(BlockType.PARAGRAPH, ""))
        return list
    }
    try {
        if (rawString.startsWith("[")) {
            val arr = JSONArray(rawString)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    EditorBlock(
                        type = BlockType.valueOf(obj.getString("type")),
                        text = obj.getString("text"),
                        isChecked = obj.optBoolean("isChecked", false)
                    )
                )
            }
        } else {
            // Unstructured plain-text legacy import
            list.add(EditorBlock(BlockType.PARAGRAPH, rawString))
        }
    } catch (e: Exception) {
        list.add(EditorBlock(BlockType.PARAGRAPH, rawString))
    }
    if (list.isEmpty()) {
        list.add(EditorBlock(BlockType.PARAGRAPH, ""))
    }
    return list
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RichTextEditorComponent(
    modifier: Modifier = Modifier,
    blocks: List<EditorBlock>,
    onBlocksChanged: (List<EditorBlock>) -> Unit,
    fontSizeMultiplier: Float = 1.0f
) {
    val localBlocks = remember(blocks) { mutableStateListOf<EditorBlock>().apply { addAll(blocks) } }

    fun notifyChange() {
        onBlocksChanged(localBlocks.toList())
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Selection toolbar triggers
        Text(
            text = "Editor Block Type Selection Shortcuts:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
        )

        ScrollableTabRow(
            selectedTabIndex = 0,
            edgePadding = 0.dp,
            divider = {},
            indicator = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            val options = listOf(
                Pair(BlockType.PARAGRAPH, "Paragraph"),
                Pair(BlockType.H1, "H1 Heading"),
                Pair(BlockType.H2, "H2 Heading"),
                Pair(BlockType.H3, "H3 Heading"),
                Pair(BlockType.BULLET, "• Bullet"),
                Pair(BlockType.NUMBERED, "1. Numbered"),
                Pair(BlockType.CHECKLIST, "☑ Checklist"),
                Pair(BlockType.CODE, "🔧 Code"),
                Pair(BlockType.QUOTE, "“ Quote")
            )

            options.forEach { (type, label) ->
                AssistChip(
                    onClick = {
                        // Insert or alter selected block type
                        localBlocks.add(EditorBlock(type, ""))
                        notifyChange()
                    },
                    label = { Text(label) },
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }

        Divider(modifier = Modifier.padding(bottom = 12.dp))

        // Render editor items
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            localBlocks.forEachIndexed { index, block ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Block indicator prefix
                    when (block.type) {
                        BlockType.BULLET -> {
                            Text(
                                " • ",
                                style = LocalTextStyle.current.copy(
                                    fontSize = (18f * fontSizeMultiplier).sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                        BlockType.NUMBERED -> {
                            Text(
                                " ${index + 1}. ",
                                style = LocalTextStyle.current.copy(
                                    fontSize = (16f * fontSizeMultiplier).sp,
                                    fontStyle = FontStyle.Italic
                                ),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                        BlockType.CHECKLIST -> {
                            Checkbox(
                                checked = block.isChecked,
                                onCheckedChange = { checked ->
                                    val updated = block.copy(isChecked = checked)
                                    localBlocks[index] = updated
                                    notifyChange()
                                }
                            )
                        }
                        BlockType.QUOTE -> {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(40.dp)
                                    .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(2.dp))
                                    .padding(end = 8.dp)
                            )
                        }
                        else -> {
                            // No prefix needed
                        }
                    }

                    // Block Input Field
                    val textStyle = when (block.type) {
                        BlockType.H1 -> MaterialTheme.typography.headlineLarge.copy(
                            fontSize = (28f * fontSizeMultiplier).sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        BlockType.H2 -> MaterialTheme.typography.headlineMedium.copy(
                            fontSize = (22f * fontSizeMultiplier).sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        BlockType.H3 -> MaterialTheme.typography.titleLarge.copy(
                            fontSize = (18f * fontSizeMultiplier).sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        BlockType.CODE -> MaterialTheme.typography.bodyMedium.copy(
                            fontSize = (14f * fontSizeMultiplier).sp,
                            fontFamily = FontFamily.Monospace,
                            background = MaterialTheme.colorScheme.surfaceVariant,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        BlockType.QUOTE -> MaterialTheme.typography.bodyLarge.copy(
                            fontSize = (16f * fontSizeMultiplier).sp,
                            fontStyle = FontStyle.Italic
                        )
                        else -> MaterialTheme.typography.bodyLarge.copy(
                            fontSize = (16f * fontSizeMultiplier).sp,
                            textDecoration = if (block.type == BlockType.CHECKLIST && block.isChecked) TextDecoration.LineThrough else TextDecoration.None
                        )
                    }

                    TextField(
                        value = block.text,
                        onValueChange = { textVal ->
                            localBlocks[index] = block.copy(text = textVal)
                            notifyChange()
                        },
                        placeholder = {
                            Text(
                                text = when (block.type) {
                                    BlockType.H1 -> "Heading 1"
                                    BlockType.H2 -> "Heading 2"
                                    BlockType.H3 -> "Heading 3"
                                    BlockType.CODE -> "System.out.println(\"Hello Code\");"
                                    BlockType.QUOTE -> "Your quote here..."
                                    else -> "Type note text..."
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                style = textStyle
                            )
                        },
                        textStyle = textStyle,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = {
                                // Double enter adds new empty paragraph block
                                localBlocks.add(index + 1, EditorBlock(BlockType.PARAGRAPH, ""))
                                notifyChange()
                            }
                        )
                    )

                    // Individual Block deleter
                    IconButton(
                        onClick = {
                            if (localBlocks.size > 1) {
                                localBlocks.removeAt(index)
                                notifyChange()
                            } else {
                                localBlocks[0] = EditorBlock(BlockType.PARAGRAPH, "")
                                notifyChange()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove block",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}
