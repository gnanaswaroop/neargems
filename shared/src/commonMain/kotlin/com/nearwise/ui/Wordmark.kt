package com.nearwise.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The app's title: the Nearwise mark next to the wordmark in Fraunces. The mark is the same as the
 * launcher icon (a pin with a north arrow and a read-aloud arc), drawn on a 108-unit grid.
 */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NearwiseMark(modifier = Modifier.size(30.dp))
        Text(
            "Nearwise",
            modifier = Modifier.padding(start = 10.dp),
            fontFamily = displayFont(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            letterSpacing = (-0.3).sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** The pin mark, drawn in the accent colour with a paper-coloured arrow and arc. */
@Composable
fun NearwiseMark(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val paper = MaterialTheme.colorScheme.background
    Canvas(modifier = modifier) {
        val s = size.width / GRID
        fun at(x: Float, y: Float) = Offset(x * s, y * s)

        // Pin: head circle plus a tail pointing down.
        val head = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(at(29f, 28f), Size(50f * s, 50f * s)))
        }
        val tail = Path().apply {
            moveTo(at(31f, 67f).x, at(31f, 67f).y)
            lineTo(at(77f, 67f).x, at(77f, 67f).y)
            lineTo(at(54f, 93f).x, at(54f, 93f).y)
            close()
        }
        drawPath(head, color = accent)
        drawPath(tail, color = accent)

        // Well with the north-pointing arrow inside.
        drawCircle(color = paper, radius = 11f * s, center = at(54f, 53f))
        val arrow = Path().apply {
            moveTo(at(54f, 44f).x, at(54f, 44f).y)
            lineTo(at(59.5f, 58f).x, at(59.5f, 58f).y)
            lineTo(at(54f, 54.5f).x, at(54f, 54.5f).y)
            lineTo(at(48.5f, 58f).x, at(48.5f, 58f).y)
            close()
        }
        drawPath(arrow, color = accent)

        // Read-aloud arc on the right.
        drawArc(
            color = accent,
            startAngle = -40f,
            sweepAngle = 80f,
            useCenter = false,
            topLeft = at(20f, 19f),
            size = Size(68f * s, 68f * s),
            style = Stroke(width = 3.5f * s, cap = StrokeCap.Round),
        )
    }
}

private const val GRID = 108f
