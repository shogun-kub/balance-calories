package com.balancecalories.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp

/**
 * How a legend swatch is drawn. Shape is a second channel beside colour: a fill, a line and a dotted line stay
 * distinguishable in greyscale and for a red-green colour-blind reader, which a hue alone does not.
 */
enum class SwatchShape {
    /** A filled square: a measured quantity, drawn as an area. */
    Square,

    /** A solid stroke: a series drawn as a line. */
    Line,

    /** A dashed stroke: a reference the person set, not a measurement. */
    Dashed,

    /** A dot: individual observations. */
    Dot,

    /**
     * Nothing, just the label: for a series that is named but not drawn — a goal line that fell outside the
     * range the data asked for, for instance. Drawing a swatch anyway would promise a line that is not there.
     */
    None,
}

/**
 * One legend entry: a small swatch and its label, in one row.
 *
 * The swatch is always 16 dp wide whatever its shape, so a legend of mixed shapes keeps its labels aligned.
 */
@Composable
fun LegendSwatch(
    color: Color,
    shape: SwatchShape = SwatchShape.Square,
    label: @Composable () -> String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.width(16.dp).height(10.dp)) {
            val middle = size.height / 2f
            when (shape) {
                SwatchShape.Square -> {
                    val side = 10.dp.toPx()
                    drawRect(
                        color = color,
                        topLeft = Offset((size.width - side) / 2f, middle - side / 2f),
                        size = Size(side, side),
                    )
                }
                SwatchShape.Dot -> drawCircle(color = color, radius = 3.dp.toPx(), center = Offset(size.width / 2f, middle))
                SwatchShape.Line -> drawLine(
                    color = color,
                    start = Offset(0f, middle),
                    end = Offset(size.width, middle),
                    strokeWidth = 2.dp.toPx(),
                )
                SwatchShape.Dashed -> drawLine(
                    color = color,
                    start = Offset(0f, middle),
                    end = Offset(size.width, middle),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f)),
                )
                SwatchShape.None -> Unit
            }
        }
        Text(
            text = label(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
