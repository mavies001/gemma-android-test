package com.yourapp.gemmatest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourapp.gemmatest.data.ConversationEntity
import com.yourapp.gemmatest.theme.LocalNovaColors

@Composable
fun SubjectDropdown(subjects: List<String>, selected: String, onSelect: (String) -> Unit) {
    val colors = LocalNovaColors.current
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(colors.Surface)
                .border(1.dp, colors.Line, RoundedCornerShape(12.dp))
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(selected, color = colors.Text0, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            Text("\u25BE", color = colors.Text2, fontSize = 12.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            subjects.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s) },
                    onClick = {
                        expanded = false
                        onSelect(s)
                    }
                )
            }
        }
    }
}

@Composable
fun Sidebar(
    conversations: List<ConversationEntity>,
    activeId: Long?,
    onSelect: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val colors = LocalNovaColors.current
    Column(modifier = Modifier.width(280.dp).fillMaxHeight().background(colors.Bg1)) {
        Text(
            "HISTORY", color = colors.Text1, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(18.dp, 18.dp, 18.dp, 10.dp)
        )
        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
            itemsIndexed(conversations) { _, c ->
                val active = activeId == c.id
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) colors.Surface2 else Color.Transparent)
                        .clickable { onSelect(c.id) }
                        .padding(horizontal = 12.dp, vertical = 11.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.Surface2)
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(c.subject, color = colors.Text2, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        val primaryText = c.summary.ifEmpty { c.title }
                        Text(primaryText, color = colors.Text0, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(
                        Icons.Filled.Delete, contentDescription = "Delete",
                        tint = colors.Text2, modifier = Modifier.size(16.dp).clickable { onDelete(c.id) }
                    )
                }
            }
        }
    }
}
