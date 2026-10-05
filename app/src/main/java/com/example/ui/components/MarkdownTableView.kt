package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MayraCyan
import com.example.ui.theme.MayraDarkSurface
import com.example.ui.theme.MayraDarkSurfaceBorder
import com.example.ui.theme.MayraDarkSurfaceElevated
import kotlin.math.max

data class TableUiModel(
    val headers: List<TableUiCell>,
    val rows: List<List<TableUiCell>>
)

data class TableUiCell(
    val content: AnnotatedString,
    val alignment: TextAlign = TextAlign.Start,
    val isHeader: Boolean = false
)

/**
 * Responsive, horizontally scrollable Markdown Table Composable for Jetpack Compose.
 *
 * Supports GitHub Flavored Markdown (GFM) tables with:
 * - Dynamic aligned column widths
 * - Left, Center, and Right cell alignments
 * - Distinct header styling with accent borders
 * - Subtle alternating row zebra-striping
 * - Full inline formatting inside table cells (bold, italic, code, links)
 */
@Composable
fun MarkdownTableView(
    table: TableUiModel,
    modifier: Modifier = Modifier,
    borderColor: Color = MayraDarkSurfaceBorder,
    headerBgColor: Color = MayraDarkSurfaceElevated,
    rowEvenBgColor: Color = MayraDarkSurface,
    rowOddBgColor: Color = MayraDarkSurfaceElevated.copy(alpha = 0.4f),
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    if (table.headers.isEmpty() && table.rows.isEmpty()) return

    val columnCount = max(table.headers.size, table.rows.maxOfOrNull { it.size } ?: 0)
    if (columnCount == 0) return

    val scrollState = rememberScrollState()

    // Determine optimal width for each column based on content length
    val columnWidths = remember(table) {
        (0 until columnCount).map { colIndex ->
            val headerLen = table.headers.getOrNull(colIndex)?.content?.length ?: 0
            val maxRowLen = table.rows.maxOfOrNull { it.getOrNull(colIndex)?.content?.length ?: 0 } ?: 0
            val maxLen = max(headerLen, maxRowLen)
            // Allocate between 110.dp and 340.dp based on character volume
            (maxLen * 9 + 36).coerceIn(110, 340).dp
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .horizontalScroll(scrollState)
            .testTag("markdown_table_view")
    ) {
        Column(modifier = Modifier.width(IntrinsicSize.Max)) {
            // 1. Header Row
            if (table.headers.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerBgColor)
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (col in 0 until columnCount) {
                        val cell = table.headers.getOrNull(col)
                        Box(
                            modifier = Modifier
                                .width(columnWidths[col])
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            contentAlignment = when (cell?.alignment) {
                                TextAlign.Center -> Alignment.Center
                                TextAlign.End, TextAlign.Right -> Alignment.CenterEnd
                                else -> Alignment.CenterStart
                            }
                        ) {
                            Text(
                                text = cell?.content ?: AnnotatedString(""),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = MayraCyan
                                ),
                                textAlign = cell?.alignment ?: TextAlign.Start
                            )
                        }
                        if (col < columnCount - 1) {
                            VerticalDivider(
                                modifier = Modifier.fillMaxHeight(),
                                color = borderColor.copy(alpha = 0.7f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }

                HorizontalDivider(
                    thickness = 1.5.dp,
                    color = MayraCyan.copy(alpha = 0.35f)
                )
            }

            // 2. Data Rows
            table.rows.forEachIndexed { rowIndex, rowCells ->
                val bg = if (rowIndex % 2 == 0) rowEvenBgColor else rowOddBgColor
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bg)
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (col in 0 until columnCount) {
                        val cell = rowCells.getOrNull(col)
                        Box(
                            modifier = Modifier
                                .width(columnWidths[col])
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            contentAlignment = when (cell?.alignment) {
                                TextAlign.Center -> Alignment.Center
                                TextAlign.End, TextAlign.Right -> Alignment.CenterEnd
                                else -> Alignment.CenterStart
                            }
                        ) {
                            Text(
                                text = cell?.content ?: AnnotatedString(""),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = textColor
                                ),
                                textAlign = cell?.alignment ?: TextAlign.Start
                            )
                        }
                        if (col < columnCount - 1) {
                            VerticalDivider(
                                modifier = Modifier.fillMaxHeight(),
                                color = borderColor.copy(alpha = 0.5f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }

                if (rowIndex < table.rows.size - 1) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = borderColor.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
