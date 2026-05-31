package com.example.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color

object RichTextEditingSuite {

    fun findParagraphStart(text: String, index: Int): Int {
        var i = (index - 1).coerceAtMost(text.length - 1)
        while (i >= 0 && text[i] != '\n') {
            i--
        }
        return i + 1
    }

    fun findParagraphEnd(text: String, index: Int): Int {
        var i = index.coerceAtLeast(0)
        while (i < text.length && text[i] != '\n') {
            i++
        }
        return i
    }

    // Applies a span style to the current selection in TextFieldValue
    fun applySpanStyle(value: TextFieldValue, style: SpanStyle): TextFieldValue {
        val selection = value.selection
        if (selection.collapsed) {
            // Apply style to the whole word or next character, but applying to current word is excellent UX
            val text = value.text
            if (text.isBlank()) return value
            val start = findParagraphStart(text, selection.start)
            val end = findParagraphEnd(text, selection.end)
            if (end > start) {
                val builder = AnnotatedString.Builder(value.annotatedString)
                builder.addStyle(style, start, end)
                return value.copy(annotatedString = builder.toAnnotatedString())
            }
            return value
        }
        val builder = AnnotatedString.Builder(value.annotatedString)
        builder.addStyle(style, selection.start, selection.end)
        return value.copy(annotatedString = builder.toAnnotatedString())
    }

    // Applies paragraph alignment styling
    fun applyParagraphAlignment(value: TextFieldValue, align: TextAlign): TextFieldValue {
        val text = value.text
        if (text.isBlank()) return value
        val selection = value.selection
        val start = findParagraphStart(text, selection.start)
        val end = findParagraphEnd(text, selection.end)
        
        val builder = AnnotatedString.Builder(value.annotatedString)
        builder.addStyle(ParagraphStyle(textAlign = align), start, end)
        return value.copy(annotatedString = builder.toAnnotatedString())
    }

    // Applies custom heading styles or clears heading styling if reset to normal
    fun applyHeading(value: TextFieldValue, level: String): TextFieldValue {
        val text = value.text
        if (text.isBlank()) return value
        val selection = value.selection
        val start = findParagraphStart(text, selection.start)
        val end = findParagraphEnd(text, selection.end)

        val builder = AnnotatedString.Builder(value.annotatedString)
        val style = when (level) {
            "h1" -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp)
            "h2" -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)
            "h3" -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp)
            else -> SpanStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp)
        }
        builder.addStyle(style, start, end)
        return value.copy(annotatedString = builder.toAnnotatedString())
    }

    // Inserts bullet markup or toggles list styling on active paragraph lines
    fun insertListMarkup(value: TextFieldValue, isNumbered: Boolean): TextFieldValue {
        val text = value.text
        val selection = value.selection
        val start = findParagraphStart(text, selection.start)
        
        val prefix = if (isNumbered) "1. " else "• "
        val hasPrefix = text.startsWith(prefix, start)
        
        val updatedText: String
        val newSelectionStart: Int
        val newSelectionEnd: Int
        
        if (hasPrefix) {
            // Remove prefix
            updatedText = text.substring(0, start) + text.substring(start + prefix.length)
            newSelectionStart = (selection.start - prefix.length).coerceAtLeast(0)
            newSelectionEnd = (selection.end - prefix.length).coerceAtLeast(0)
        } else {
            // Add prefix
            updatedText = text.substring(0, start) + prefix + text.substring(start)
            newSelectionStart = selection.start + prefix.length
            newSelectionEnd = selection.end + prefix.length
        }
        
        return TextFieldValue(
            annotatedString = AnnotatedString(updatedText),
            selection = TextRange(newSelectionStart, newSelectionEnd)
        )
    }

    // Inserts blockquote quote formatting
    fun insertQuoteMarkup(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val selection = value.selection
        val start = findParagraphStart(text, selection.start)
        val prefix = "“ "
        val hasPrefix = text.startsWith(prefix, start)

        val updatedText: String
        val newSelectionStart: Int
        val newSelectionEnd: Int

        if (hasPrefix) {
            updatedText = text.substring(0, start) + text.substring(start + prefix.length)
            newSelectionStart = (selection.start - prefix.length).coerceAtLeast(0)
            newSelectionEnd = (selection.end - prefix.length).coerceAtLeast(0)
        } else {
            updatedText = text.substring(0, start) + prefix + text.substring(start)
            newSelectionStart = selection.start + prefix.length
            newSelectionEnd = selection.end + prefix.length
        }

        val builder = AnnotatedString.Builder(updatedText)
        if (!hasPrefix) {
            // Apply italic block format to the quote
            val endParagraph = findParagraphEnd(updatedText, newSelectionEnd)
            builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic, color = Color.Gray), start, endParagraph)
        }

        return TextFieldValue(
            annotatedString = builder.toAnnotatedString(),
            selection = TextRange(newSelectionStart, newSelectionEnd)
        )
    }
}
