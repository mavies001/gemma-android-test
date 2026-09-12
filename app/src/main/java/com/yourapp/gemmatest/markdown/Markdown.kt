package com.yourapp.gemmatest.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourapp.gemmatest.theme.LocalNovaColors

sealed class MdBlock {
    data class Code(val text: String) : MdBlock()
    data class Header(val level: Int, val text: String) : MdBlock()
    data class Bullet(val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
}

fun parseMarkdownBlocks(raw: String): List<MdBlock> {
    val lines = raw.split("\n")
    val blocks = mutableListOf<MdBlock>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        when {
            trimmed.startsWith("```") -> {
                val buffer = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    buffer.appendLine(lines[i])
                    i++
                }
                blocks.add(MdBlock.Code(buffer.toString().trimEnd()))
                if (i < lines.size) i++
            }
            trimmed.startsWith("### ") -> { blocks.add(MdBlock.Header(3, trimmed.removePrefix("### "))); i++ }
            trimmed.startsWith("## ") -> { blocks.add(MdBlock.Header(2, trimmed.removePrefix("## "))); i++ }
            trimmed.startsWith("# ") -> { blocks.add(MdBlock.Header(1, trimmed.removePrefix("# "))); i++ }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                blocks.add(MdBlock.Bullet(trimmed.removePrefix("- ").removePrefix("* "))); i++
            }
            trimmed.isEmpty() -> { i++ }
            else -> { blocks.add(MdBlock.Paragraph(line)); i++ }
        }
    }
    return blocks
}

fun parseInlineMarkdown(text: String) = buildAnnotatedString {
    val regex = Regex("(\\*\\*.+?\\*\\*)|(`[^`]+`)|(\\*[^*\\n]+?\\*)")
    var lastIndex = 0
    for (match in regex.findAll(text)) {
        if (match.range.first > lastIndex) append(text.substring(lastIndex, match.range.first))
        val token = match.value
        when {
            token.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(token.removeSurrounding("**"))
            }
            token.startsWith("`") -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x22808080))
            ) { append(" " + token.removeSurrounding("`") + " ") }
            token.startsWith("*") -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(token.removeSurrounding("*"))
            }
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) append(text.substring(lastIndex))
}

@Composable
fun MarkdownText(raw: String, textColor: Color, streaming: Boolean = false) {
    val colors = LocalNovaColors.current
    val blocks = remember(raw) { parseMarkdownBlocks(raw) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEachIndexed { index, block ->
            val isLast = index == blocks.lastIndex
            when (block) {
                is MdBlock.Code -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.CodeBg)
                            .border(1.dp, colors.Line, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            block.text + if (isLast && streaming) " \u258C" else "",
                            color = textColor, fontFamily = FontFamily.Monospace,
                            fontSize = 12.5.sp, lineHeight = 18.sp
                        )
                    }
                }
                is MdBlock.Header -> {
                    Text(
                        parseInlineMarkdown(block.text), color = textColor,
                        fontSize = when (block.level) { 1 -> 19.sp; 2 -> 17.sp; else -> 15.5.sp },
                        fontWeight = FontWeight.Bold
                    )
                }
                is MdBlock.Bullet -> {
                    Row {
                        Text("\u2022  ", color = textColor, fontSize = 14.5.sp)
                        Text(
                            parseInlineMarkdown(block.text + (if (isLast && streaming) " \u258C" else "")),
                            color = textColor, fontSize = 14.5.sp, lineHeight = 21.sp
                        )
                    }
                }
                is MdBlock.Paragraph -> {
                    Text(
                        parseInlineMarkdown(block.text + (if (isLast && streaming) " \u258C" else "")),
                        color = textColor, fontSize = 14.5.sp, lineHeight = 22.sp
                    )
                }
            }
        }
        if (blocks.isEmpty() && streaming) {
            Text("\u258C", color = textColor, fontSize = 14.5.sp)
        }
    }
}
