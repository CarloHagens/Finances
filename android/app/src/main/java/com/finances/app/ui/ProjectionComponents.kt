package com.finances.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Shared visual language for the three goal projection screens.

val GreenStatus = Color(0xFF2E7D32)
val AmberStatus = Color(0xFFE65100)
val BlueStatus  = Color(0xFF1565C0)

/** Money for headline figures: "£1.25M", "£12,345", "−£500". */
fun fmtPot(v: Double): String {
    val a = kotlin.math.abs(v)
    val body = when {
        a >= 1_000_000 -> "£%.2fM".format(java.util.Locale.UK, a / 1_000_000)
        else           -> "£%,.0f".format(java.util.Locale.UK, a)
    }
    return if (v < -0.5) "−$body" else body
}

/** Monthly amounts: "£1,234/mo". */
fun fmtMonthly(v: Double): String = "${fmtPot(v)}/mo"

fun fmtAxis(v: Float): String = when {
    v >= 1_000_000f -> "£%.1fM".format(java.util.Locale.UK, v / 1_000_000f)
    v >= 1_000f     -> "£%.0fk".format(java.util.Locale.UK, v / 1_000f)
    else            -> "£%.0f".format(java.util.Locale.UK, v)
}

/** A rate as a percentage with only the decimals it needs: 0.04125 → "4.125%". */
fun fmtRate(r: Double): String = "${r.toPercentString()}%"

fun formatGoalDate(iso: String): String = runCatching {
    java.time.LocalDate.parse(iso.take(10))
        .format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy", java.util.Locale.UK))
}.getOrDefault(iso)

/** Small explanatory line under a card or figure. */
@Composable
fun NoteText(text: String, color: Color = Color.Unspecified) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color
    )
}

/** A highlighted warning card used for assumptions and limits the user should know about. */
@Composable
fun WarningCard(lines: List<String>) {
    if (lines.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = AmberStatus.copy(alpha = 0.10f))
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lines.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun MiniStat(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ExpandableCard(title: String, expanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null)
            }
            if (expanded) {
                Column(
                    modifier = Modifier.padding(bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) { content() }
            }
        }
    }
}

/** Circular gauge showing [progress] (0..1) with a caption underneath the percentage. */
@Composable
fun FundingRing(progress: Float, positive: Boolean, caption: String) {
    val ringColor = if (positive) GreenStatus else AmberStatus
    val track = ringColor.copy(alpha = 0.15f)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 13.dp.toPx()
            val d = size.minDimension - stroke
            val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            val arcSize = Size(d, d)
            drawArc(track, -90f, 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(ringColor, -90f, 360f * progress.coerceIn(0f, 1f), false, topLeft, arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.headlineSmall, color = ringColor)
            Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Evenly spaced dots with an age above and a short label below. */
@Composable
fun MilestoneTimeline(milestones: List<Pair<String, String>>) {
    if (milestones.isEmpty()) return
    val lineColor = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            val y = size.height / 2f
            val n = milestones.size
            drawLine(lineColor.copy(alpha = 0.30f), Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
            for (i in 0 until n) {
                drawCircle(lineColor, 5.dp.toPx(), Offset(size.width * (i + 0.5f) / n, y))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            milestones.forEach { (age, name) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(age, style = MaterialTheme.typography.labelMedium)
                    Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(9.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A shaded background region of a [PhaseChart], spanning [start]..[end] on the x (age) axis. */
data class ChartBand(val start: Float, val end: Float, val color: Color)

/**
 * Line chart of [points] (x = age, y = value) with a zero-based y axis, optional
 * shaded [bands] behind the line and dashed vertical [markers] at key ages.
 */
@Composable
fun PhaseChart(
    points: List<Pair<Float, Float>>,
    bands: List<ChartBand> = emptyList(),
    markers: List<Float> = emptyList(),
    chartHeight: Dp = 170.dp
) {
    if (points.size < 2) return
    val lineColor = MaterialTheme.colorScheme.primary
    val axisArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val markerColor = lineColor.copy(alpha = 0.45f)

    Canvas(Modifier.fillMaxWidth().height(chartHeight)) {
        val leftPad = 46.dp.toPx(); val rightPad = 8.dp.toPx()
        val topPad = 10.dp.toPx(); val botPad = 22.dp.toPx()
        val cw = size.width - leftPad - rightPad
        val ch = size.height - topPad - botPad
        val minX = points.first().first
        val maxX = points.last().first
        val xRange = (maxX - minX).coerceAtLeast(0.0001f)
        val maxVal = (points.maxOfOrNull { it.second } ?: 1f).coerceAtLeast(1f)

        fun xOf(x: Float) = leftPad + cw * (x - minX) / xRange
        fun yOf(v: Float) = topPad + ch - ch * (v / maxVal)

        bands.forEach { b ->
            val x0 = xOf(b.start.coerceIn(minX, maxX))
            val x1 = xOf(b.end.coerceIn(minX, maxX))
            drawRect(b.color, Offset(x0, topPad), Size((x1 - x0).coerceAtLeast(0f), ch))
        }

        val path = Path().apply {
            points.forEachIndexed { i, (x, v) -> if (i == 0) moveTo(xOf(x), yOf(v)) else lineTo(xOf(x), yOf(v)) }
        }
        val fill = Path().apply {
            addPath(path); lineTo(xOf(maxX), topPad + ch); lineTo(xOf(minX), topPad + ch); close()
        }
        drawPath(fill, lineColor.copy(alpha = 0.10f))
        drawPath(path, lineColor, style = Stroke(2.5.dp.toPx()))

        val dash = PathEffect.dashPathEffect(floatArrayOf(7f, 7f))
        markers.forEach { m ->
            if (m in minX..maxX) {
                val x = xOf(m)
                drawLine(markerColor, Offset(x, topPad), Offset(x, topPad + ch),
                    strokeWidth = 1.dp.toPx(), pathEffect = dash)
            }
        }

        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                color = axisArgb; textSize = 10.sp.toPx(); isAntiAlias = true
            }
            paint.textAlign = android.graphics.Paint.Align.RIGHT
            canvas.nativeCanvas.drawText(fmtAxis(maxVal), leftPad - 5.dp.toPx(), topPad + paint.textSize, paint)
            canvas.nativeCanvas.drawText("£0", leftPad - 5.dp.toPx(), topPad + ch, paint)

            paint.textAlign = android.graphics.Paint.Align.CENTER
            val labels = (listOf(minX, (minX + maxX) / 2f, maxX) + markers)
                .filter { it in minX..maxX }
                .map { "%.0f".format(it).toFloat() }
                .distinct()
            labels.forEach { a ->
                canvas.nativeCanvas.drawText("%.0f".format(a), xOf(a), size.height - 4.dp.toPx(), paint)
            }
        }
    }
}
