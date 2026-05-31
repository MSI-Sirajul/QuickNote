package com.example

import com.example.ui.components.BlockType
import com.example.ui.components.EditorBlock
import com.example.ui.components.deserializeBlocks
import com.example.ui.components.serializeBlocks
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testNoteBlockSerialization_correctFormat() {
        // Prepare list of blocks
        val blocks = listOf(
            EditorBlock(BlockType.H1, "Welcome to QuickNote!"),
            EditorBlock(BlockType.CHECKLIST, "Check off tasks", isChecked = true)
        )

        // Serialize
        val rawJson = serializeBlocks(blocks)
        assertTrue(rawJson.contains("\"type\":\"H1\""))
        assertTrue(rawJson.contains("\"text\":\"Welcome to QuickNote!\""))
        assertTrue(rawJson.contains("\"isChecked\":true"))

        // Deserialize
        val deserialized = deserializeBlocks(rawJson)
        assertEquals(2, deserialized.size)
        assertEquals(BlockType.H1, deserialized[0].type)
        assertEquals("Welcome to QuickNote!", deserialized[0].text)
        assertTrue(deserialized[1].isChecked)
    }

    @Test
    fun testLegacyPlaintextImport_correctParagraphConversion() {
        // Plain text simulation
        val plainText = "Hello this is raw unstructured plain text."
        val blocks = deserializeBlocks(plainText)

        // Verifies correct translation to single rich block
        assertEquals(1, blocks.size)
        assertEquals(BlockType.PARAGRAPH, blocks[0].type)
        assertEquals(plainText, blocks[0].text)
    }
}
