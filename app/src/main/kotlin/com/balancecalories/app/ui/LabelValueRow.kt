package com.balancecalories.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A label and its value, side by side when they fit and stacked when they do not.
 *
 * A plain `Row` puts the value in whatever width the label leaves, and at a 200% font scale that is a narrow
 * column: "поднята до безопасного минимума" came out as `безопасн` / `ого`, breaking inside words, because a
 * squeezed `Text` has nowhere else to break. `FlowRow` moves the whole value to the next line instead, where it
 * gets the full width and breaks between words like prose.
 *
 * This exists as one composable rather than a habit because the same mistake is easy to make three times, and the
 * failure only appears at a font scale nobody tests by eye.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LabelValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    labelStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    valueStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = label, style = labelStyle)
        Text(text = value, style = valueStyle)
    }
}

/**
 * A row that wraps instead of squeezing.
 *
 * Drop-in for `Row(horizontalArrangement = Arrangement.spacedBy(n.dp))` when the children are labels, values or
 * buttons rather than a flexible column: at one line it looks identical, and when the text no longer fits it moves
 * whole children to the next line. A plain `Row` instead gives the last child whatever width is left, which at a
 * 200% font scale is a few tens of dp — enough for one letter, so "Настройки" became a vertical column of glyphs.
 *
 * The opt-in for `FlowRow` lives here so that every screen does not have to carry an experimental annotation for a
 * layout primitive that has been stable in behaviour for years.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WrappingRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(2.dp),
    content: @Composable () -> Unit,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
    ) {
        content()
    }
}