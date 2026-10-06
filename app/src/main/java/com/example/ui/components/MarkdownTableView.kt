package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MayraCyan
import com.example.ui.theme.MayraDarkBorder
import com.example.ui.theme.MayraDarkSurface
import com.example.ui.theme.MayraDarkSurfaceVariant
import com.example.ui.theme.MayraTextPrimary
import kotlin.math.max

data class TableUiModel(
    val headers: List<String>,
    val rows: List<List<String>>
)

@Composable
fun MarkdownTableView(
    table: TableUiModel,
    modifier: Modifier = Modifier
) {
    if (table.headers.isEmpty() && table.rows.isEmpty()) return

    val colCount = max(table.headers.size, table.rows.maxOfOrNull { it.size } ?: 0)
    if (colCount == 0) return

    val columnWidth = 140.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MayraDarkBorder, RoundedCornerShape(8.dp))
            .horizontalScroll(rememberScrollState())
    ) {
        Column {
            // Header Row
            if (table.headers.isNotEmpty()) {
                Row(
                    modifier = Modifier.background(MayraDarkSurfaceVariant)
                ) {
                    for (i in 0 until colCount) {
                        val headerText = table.headers.getOrNull(i) ?: ""
                        Box(
                            modifier = Modifier
                                .width(columnWidth)
                                .border(0.5.dp, MayraDarkBorder)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = headerText,
                                fontWeight = FontWeight.Bold,
                                color = MayraCyan,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }
            }

            // Data Rows
            table.rows.forEachIndexed { rowIndex, rowCells ->
                val rowBg = if (rowIndex % 2 == 0) MayraDarkSurface else MayraDarkSurfaceVariant.copy(alpha = 0.5f)
                Row(modifier = Modifier.background(rowBg)) {
                    for (i in 0 until colCount) {
                        val cellText = rowCells.getOrNull(i) ?: ""
                        Box(
                            modifier = Modifier
                                .width(columnWidth)
                                .border(0.5.dp, MayraDarkBorder)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = cellText,
                                color = MayraTextPrimary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }
            }
        }
    }
}
