package com.example.omni.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.omni.ui.LocalDesignWindow

/**
 * How many columns a reflow should use in the current window.
 *
 * 1 in portrait (pixel-identical to before), 2 in landscape. Keyed to
 * [LocalDesignWindow.isLandscape] so the breakpoint is exactly the orientation flip and portrait
 * never changes.
 */
@Composable
fun adaptiveColumns(): Int = if (LocalDesignWindow.current.isLandscape) 2 else 1

/**
 * Lays a list of items into [adaptiveColumns] balanced columns, each column a vertical stack.
 *
 * Portrait → one column, which is byte-identical to a plain `Column { items.forEach { ... } }`.
 * Landscape → two side-by-side columns that together fill the width, so the page reads as a real
 * two-column layout rather than a centred letterbox. Items are dealt round-robin across the columns
 * (0,2,4… left; 1,3,5… right) so both columns grow at the same rate and neither runs long.
 *
 * This is *not* a scroller — the caller owns the scroll (a `verticalScroll` Column around this), which
 * keeps the whole page one scroll region in both orientations and avoids the nested-scroll pitfall.
 *
 * @param verticalSpacing gap between stacked items within a column (the list's own row gap).
 * @param horizontalSpacing gap between the two columns in landscape (ignored in portrait).
 */
@Composable
fun <T> AdaptiveColumnGrid(
    items: List<T>,
    verticalSpacing: Dp,
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 16.dp,
    itemContent: @Composable (T) -> Unit,
) {
    val columns = adaptiveColumns()
    if (columns <= 1) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        ) {
            items.forEach { itemContent(it) }
        }
        return
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
    ) {
        for (col in 0 until columns) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            ) {
                // Round-robin: this column gets every item whose index mod columns == col.
                items.forEachIndexed { index, item ->
                    if (index % columns == col) {
                        itemContent(item)
                    }
                }
            }
        }
    }
}

/**
 * A single block laid out in [adaptiveColumns] columns, where the caller supplies the per-column
 * content by index. Use when the two columns hold *different* content (a dashboard's gauge on the
 * left, its log on the right) rather than a split list. Portrait renders only column 0 stacked under
 * nothing — i.e. it collapses to the single portrait column, so portrait is unchanged.
 */
@Composable
fun AdaptiveRow(
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 24.dp,
    columnContent: @Composable (columnIndex: Int, columnCount: Int) -> Unit,
) {
    val columns = adaptiveColumns()
    if (columns <= 1) {
        Box(modifier = modifier.fillMaxWidth()) {
            columnContent(0, 1)
        }
        return
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
    ) {
        for (col in 0 until columns) {
            Column(modifier = Modifier.weight(1f)) {
                columnContent(col, columns)
            }
        }
    }
}
