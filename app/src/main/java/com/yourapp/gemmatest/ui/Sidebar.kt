package com.yourapp.gemmatest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
fun SubjectRail(subjects: List<String>, selected: String, onSelect: (String) -> Unit) {
    val colors = LocalNovaColors.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(subjects) { s ->
            val active = s == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (active) colors.Blue500 else colors.Surface)
                    .clickable { onSelect(s) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(s, color = if (active) Color.White else colors.Text1, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
                        Text(c.title, color = colors.Text0, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (c.summary.isNotEmpty()) {
                            Text(c.summary, color = colors.Text2, fontSize = 11.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
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
