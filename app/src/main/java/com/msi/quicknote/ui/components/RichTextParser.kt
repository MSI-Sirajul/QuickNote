package com.msi.quicknote.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

object RichTextParser {
    
    // Converts HTML string back into a Jetpack Compose AnnotatedString with all spans
    fun htmlToAnnotatedString(html: String): AnnotatedString {
        if (html.isBlank()) return AnnotatedString("")
        
        // Replace br tags with inline newlines for consistent text formatting
        val normalizedHtml = html.replace("(?i)<br\\s*/?>".toRegex(), "\n")
        val doc = Jsoup.parseBodyFragment(normalizedHtml)
        val builder = AnnotatedString.Builder()
        
        fun parseElement(element: Element) {
            for (node in element.childNodes()) {
                when (node) {
                    is TextNode -> {
                        builder.append(node.text())
                    }
                    is Element -> {
                        val start = builder.length
                        val tagName = node.tagName().lowercase()
                        
                        var inlineColor: Color? = null
                        var inlineSize: Float? = null
                        var inlineFamily: FontFamily? = null
                        
                        val styleAttr = node.attr("style")
                        if (styleAttr.isNotBlank()) {
                            val declarations = styleAttr.split(";")
                            for (dec in declarations) {
                                val parts = dec.split(":")
                                if (parts.size == 2) {
                                    val key = parts[0].trim().lowercase()
                                    val value = parts[1].trim().lowercase()
                                    when (key) {
                                        "color" -> {
                                            try {
                                                if (value.startsWith("#")) {
                                                    val colorValue = value.substring(1).toLong(16)
                                                    inlineColor = Color((0xFF000000 or colorValue).toInt())
                                                }
                                            } catch (e: Exception) { }
                                        }
                                        "font-size" -> {
                                            try {
                                                val sizeVal = value.replace("px", "").replace("sp", "").trim().toFloat()
                                                inlineSize = sizeVal
                                            } catch (e: Exception) {}
                                        }
                                        "font-family" -> {
                                            when {
                                                value.contains("serif") -> inlineFamily = FontFamily.Serif
                                                value.contains("monospace") -> inlineFamily = FontFamily.Monospace
                                                value.contains("cursive") -> inlineFamily = FontFamily.Cursive
                                                else -> inlineFamily = FontFamily.Default
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        parseElement(node)
                        val end = builder.length
                        if (end > start) {
                            val styleTask = when (tagName) {
                                "b", "strong" -> SpanStyle(fontWeight = FontWeight.Bold)
                                "i", "em" -> SpanStyle(fontStyle = FontStyle.Italic)
                                "u" -> SpanStyle(textDecoration = TextDecoration.Underline)
                                "s", "strike", "del" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                                "h1" -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp)
                                "h2" -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                "h3" -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                else -> SpanStyle()
                            }
                            
                            val finalStyle = styleTask.copy(
                                color = inlineColor ?: styleTask.color,
                                fontSize = inlineSize?.sp ?: styleTask.fontSize,
                                fontFamily = inlineFamily ?: styleTask.fontFamily
                            )
                            builder.addStyle(finalStyle, start, end)
                        }
                    }
                }
            }
        }
        
        parseElement(doc.body())
        return builder.toAnnotatedString()
    }

    // Converts an AnnotatedString into static clean inline CSS HTML markup
    fun annotatedStringToHtml(annotatedString: AnnotatedString): String {
        val text = annotatedString.text
        if (text.isBlank()) return ""
        
        val htmlBuilder = StringBuilder()
        
        for (i in text.indices) {
            val char = text[i]
            val activeStyles = annotatedString.spanStyles.filter { it.start <= i && i < it.end }
            
            for (styleRange in activeStyles) {
                if (styleRange.start == i) {
                    val s = styleRange.item
                    if (s.fontWeight == FontWeight.Bold) htmlBuilder.append("<b>")
                    if (s.fontStyle == FontStyle.Italic) htmlBuilder.append("<i>")
                    if (s.textDecoration == TextDecoration.Underline) htmlBuilder.append("<u>")
                    if (s.textDecoration == TextDecoration.LineThrough) htmlBuilder.append("<s>")
                    
                    val hasColor = s.color != Color.Unspecified
                    val hasSize = s.fontSize != Float.NaN.sp && s.fontSize.isSp
                    val hasFamily = s.fontFamily != null
                    
                    if (hasColor || hasSize || hasFamily) {
                        htmlBuilder.append("<span style=\"")
                        if (hasColor) {
                            val colorHex = String.format("#%06X", 0xFFFFFF and s.color.value.toLong().toInt())
                            htmlBuilder.append("color:$colorHex;")
                        }
                        if (hasSize) {
                            htmlBuilder.append("font-size:${s.fontSize.value}px;")
                        }
                        if (hasFamily) {
                            val fam = when (s.fontFamily) {
                                FontFamily.Serif -> "serif"
                                FontFamily.Monospace -> "monospace"
                                FontFamily.Cursive -> "cursive"
                                else -> "default"
                            }
                            htmlBuilder.append("font-family:$fam;")
                        }
                        htmlBuilder.append("\">")
                    }
                }
            }
            
            when (char) {
                '\n' -> htmlBuilder.append("<br/>")
                '<' -> htmlBuilder.append("&lt;")
                '>' -> htmlBuilder.append("&gt;")
                '&' -> htmlBuilder.append("&amp;")
                else -> htmlBuilder.append(char)
            }
            
            for (styleRange in activeStyles.reversed()) {
                if (styleRange.end - 1 == i) {
                    val s = styleRange.item
                    val hasColor = s.color != Color.Unspecified
                    val hasSize = s.fontSize != Float.NaN.sp && s.fontSize.isSp
                    val hasFamily = s.fontFamily != null
                    if (hasColor || hasSize || hasFamily) {
                        htmlBuilder.append("</span>")
                    }
                    if (s.textDecoration == TextDecoration.LineThrough) htmlBuilder.append("</s>")
                    if (s.textDecoration == TextDecoration.Underline) htmlBuilder.append("</u>")
                    if (s.fontStyle == FontStyle.Italic) htmlBuilder.append("</i>")
                    if (s.fontWeight == FontWeight.Bold) htmlBuilder.append("</b>")
                }
            }
        }
        
        return htmlBuilder.toString()
    }
}
