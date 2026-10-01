package com.finances.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.finances.app.data.IsaBridgeProjection
import com.finances.app.data.MortgageProjection
import com.finances.app.data.NetWorthPoint
import com.finances.app.data.NetWorthSummary
import com.finances.app.data.PensionProjection
import com.finances.app.data.UserProfile
import kotlin.math.abs

@Composable
fun DashboardScreen(
    vm: FinancesViewModel,
    onMortgageTap: () -> Unit,
    onPensionTap: () -> Unit,
    onIsaTap: () -> Unit
) {
    val profile  by vm.profile.collectAsState()
    val netWorth by vm.netWorth.collectAsState()
    val history  by vm.netWorthHistory.collectAsState()
    val mortgage by vm.mortgageProjection.collectAsState()
    val pension  by vm.pensionProjection.collectAsState()
    val isa      by vm.isaProjection.collectAsState()
    val offline  by vm.offline.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Dashboard",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (offline) {
            WarningCard(listOf("Can't reach the server — showing balances saved on this phone, which may be out of date."))
        }
        NetWorthCard(netWorth, history)
        RetirementCard(profile, pension)
        Text("Goals", style = MaterialTheme.typography.titleMedium)
        val m = mortgage
        GoalStatusCard(
            title = "Mortgage",
            status = mortgageStatusLine(m) ?: "Not configured",
            onTrack = m?.takeIf { it.status == "ok" || it.status == "never_pays_off" || it.status == "paid_off" }?.onTrack,
            onClick = onMortgageTap
        )
        val p = pension
        GoalStatusCard(
            title = "Pension",
            status = when {
                p == null || p.accountId == null -> "Not configured"
                p.profileIncomplete -> "Add your date of birth in Settings"
                p.onTrack -> "On track — ${fmtPot(p.projectedAtDraw)} at ${p.drawAge} (≈${fmtPot(p.projectedAtDrawToday)} today)"
                else -> "Off track — ${fmtPot(p.projectedAtDraw)} projected vs ${fmtPot(p.inflatedTarget)} needed at ${p.drawAge}"
            },
            onTrack = p?.takeIf { it.accountId != null && !it.profileIncomplete }?.onTrack,
            onClick = onPensionTap
        )
        val i = isa
        GoalStatusCard(
            title = "ISA Bridge",
            status = when {
                i == null || i.accountId == null -> "Not configured"
                i.profileIncomplete -> "Add your date of birth in Settings"
                i.onTrack -> "On track — pot lasts to ${i.bridgeEndAge}"
                else -> "Off track — pot runs out at %.1f, before ${i.bridgeEndAge}".format(i.runsOutAge)
            },
            onTrack = i?.takeIf { it.accountId != null && !it.profileIncomplete }?.onTrack,
            onClick = onIsaTap
        )
    }
}

@Composable
private fun NetWorthCard(summary: NetWorthSummary?, history: List<NetWorthPoint>) {
    val lineColor   = MaterialTheme.colorScheme.primary
    val fillColor   = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val tooltipBg   = MaterialTheme.colorScheme.inverseSurface.toArgb()
    val tooltipFg   = MaterialTheme.colorScheme.inverseOnSurface.toArgb()
    val tooltipFg2  = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f).toArgb()

    var canvasSize by remember { mutableStateOf(Size.Zero) }
    val density = LocalDensity.current

    // Time-range selection, persisted as fractions of the full history so it
    // survives relaunch and adapts as new months are added.
    val prefs = LocalContext.current
        .getSharedPreferences("finances_prefs", android.content.Context.MODE_PRIVATE)
    var range by remember {
        val s = prefs.getFloat("nw_range_start", 0f).coerceIn(0f, 1f)
        val e = prefs.getFloat("nw_range_end", 1f).coerceIn(0f, 1f)
        mutableStateOf(if (s < e) s..e else 0f..1f)
    }

    // Visible slice of history under the selected range (always ≥ 2 points).
    val visible = remember(history, range) {
        if (history.size < 2) history
        else {
            val last = history.lastIndex
            var startIdx = (range.start * last).roundToInt().coerceIn(0, last - 1)
            var endIdx = (range.endInclusive * last).roundToInt().coerceIn(1, last)
            if (endIdx <= startIdx) endIdx = startIdx + 1
            history.subList(startIdx, endIdx + 1)
        }
    }

    var selectedIndex by remember(visible) { mutableStateOf<Int?>(null) }

    // Position of each point along the x axis (0..1), by date, so a gap in the
    // data shows as a gap rather than being squeezed out.
    val xFractions = remember(visible) {
        val times = visible.map { chartMillis(it.recordedAt) }
        val t0 = times.firstOrNull() ?: 0L
        val span = ((times.lastOrNull() ?: 0L) - t0).coerceAtLeast(1L).toFloat()
        times.map { (it - t0) / span }
    }

    fun nearestIndex(touchX: Float): Int? {
        if (visible.size < 2) return null
        val leftPad = with(density) { 62.dp.toPx() }
        val chartW  = canvasSize.width - leftPad - with(density) { 8.dp.toPx() }
        if (chartW <= 0f) return null
        val f = ((touchX - leftPad) / chartW).coerceIn(0f, 1f)
        return xFractions.indices.minByOrNull { abs(xFractions[it] - f) }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Net Worth", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                formatMoney(summary?.netWorth ?: 0.0),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Assets", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatMoney(summary?.totalAssets ?: 0.0), style = MaterialTheme.typography.bodyMedium)
                }
                Column {
                    Text("Liabilities", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatMoney(summary?.totalLiabilities ?: 0.0), style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Net worth change indicators
            if (history.size >= 2) {
                val monthChange = history.last().netWorth - history[history.size - 2].netWorth
                val sinceChange = if (history.size >= 3) history.last().netWorth - history.first().netWorth else null
                val sinceLabel  = if (history.size >= 3) "since ${formatChartDate(history.first().recordedAt)}" else null
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ChangeChip("this month", monthChange)
                    if (sinceChange != null && sinceLabel != null) {
                        ChangeChip(sinceLabel, sinceChange)
                    }
                }
            }

            if (history.size >= 2) {
                Spacer(Modifier.height(12.dp))
                val labelColor = lineColor.toArgb()
                val gridColor  = lineColor.copy(alpha = 0.15f)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
                        .pointerInput(visible) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                selectedIndex = nearestIndex(down.position.x)
                                drag(down.id) { change ->
                                    selectedIndex = nearestIndex(change.position.x)
                                }
                            }
                        }
                ) {
                    val values = visible.map { it.netWorth.toFloat() }
                    val minVal = values.min()
                    val maxVal = values.max()
                    val ySpan  = (maxVal - minVal).coerceAtLeast(1f)

                    val leftPad   = 62.dp.toPx()
                    val rightPad  = 8.dp.toPx()
                    val topPad    = 8.dp.toPx()
                    val bottomPad = 28.dp.toPx()
                    val chartW = size.width - leftPad - rightPad
                    val chartH = size.height - topPad - bottomPad

                    fun xOf(i: Int) = if (values.size == 1) leftPad + chartW / 2
                                      else leftPad + chartW * xFractions[i]
                    fun yOf(v: Float) = topPad + chartH - chartH * (v - minVal) / ySpan

                    // Grid lines
                    drawLine(gridColor, Offset(leftPad, topPad), Offset(leftPad, topPad + chartH), strokeWidth = 1.dp.toPx())
                    drawLine(gridColor, Offset(leftPad, topPad), Offset(leftPad + chartW, topPad), strokeWidth = 1.dp.toPx())
                    drawLine(gridColor, Offset(leftPad, topPad + chartH), Offset(leftPad + chartW, topPad + chartH), strokeWidth = 1.dp.toPx())

                    // Fill + line
                    val linePath = Path().apply {
                        values.forEachIndexed { i, v ->
                            if (i == 0) moveTo(xOf(i), yOf(v)) else lineTo(xOf(i), yOf(v))
                        }
                    }
                    val fillPath = Path().apply {
                        addPath(linePath)
                        lineTo(xOf(values.lastIndex), topPad + chartH)
                        lineTo(xOf(0), topPad + chartH)
                        close()
                    }
                    drawPath(fillPath, color = fillColor)
                    drawPath(linePath, color = lineColor, style = Stroke(width = 2.dp.toPx()))

                    // Dots — larger for selected
                    values.forEachIndexed { i, v ->
                        val radius = if (i == selectedIndex) 5.dp.toPx() else 3.dp.toPx()
                        drawCircle(lineColor, radius = radius, center = Offset(xOf(i), yOf(v)))
                    }

                    // Axis labels
                    drawIntoCanvas { canvas ->
                        val paint = android.graphics.Paint().apply {
                            color = labelColor
                            textSize = 10.sp.toPx()
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.DEFAULT
                        }

                        paint.textAlign = android.graphics.Paint.Align.RIGHT
                        canvas.nativeCanvas.drawText(formatChartValue(maxVal), leftPad - 6.dp.toPx(), topPad + paint.textSize, paint)
                        canvas.nativeCanvas.drawText(formatChartValue(minVal), leftPad - 6.dp.toPx(), topPad + chartH + 2.dp.toPx(), paint)

                        paint.textAlign = android.graphics.Paint.Align.CENTER
                        val labelIndices = when {
                            values.size <= 4 -> (0 until values.size).toList()
                            else -> listOf(0, values.size / 3, 2 * values.size / 3, values.size - 1).distinct()
                        }
                        labelIndices.forEach { idx ->
                            canvas.nativeCanvas.drawText(
                                formatChartDate(visible[idx].recordedAt),
                                xOf(idx), topPad + chartH + bottomPad - 4.dp.toPx(), paint
                            )
                        }
                    }

                    // Selection: crosshair + tooltip
                    selectedIndex?.let { idx ->
                        val sx = xOf(idx)
                        val sv = values[idx]
                        val sy = yOf(sv)

                        // Vertical crosshair
                        drawLine(
                            lineColor.copy(alpha = 0.35f),
                            Offset(sx, topPad), Offset(sx, topPad + chartH),
                            strokeWidth = 1.dp.toPx()
                        )

                        // Highlighted dot with white centre
                        drawCircle(lineColor, radius = 6.dp.toPx(), center = Offset(sx, sy))
                        drawCircle(Color.White, radius = 3.dp.toPx(), center = Offset(sx, sy))

                        // Tooltip
                        drawIntoCanvas { canvas ->
                            val valuePaint = android.graphics.Paint().apply {
                                color = tooltipFg
                                textSize = 12.sp.toPx()
                                isAntiAlias = true
                                isFakeBoldText = true
                            }
                            val datePaint = android.graphics.Paint().apply {
                                color = tooltipFg2
                                textSize = 10.sp.toPx()
                                isAntiAlias = true
                            }
                            val bgPaint = android.graphics.Paint().apply {
                                color = tooltipBg
                                isAntiAlias = true
                            }

                            val valueText = formatMoney(visible[idx].netWorth)
                            val dateText  = formatChartDate(visible[idx].recordedAt)

                            val pad   = 8.dp.toPx()
                            val boxW  = maxOf(valuePaint.measureText(valueText), datePaint.measureText(dateText)) + pad * 2
                            val boxH  = 14.sp.toPx() + 10.sp.toPx() + pad * 2 + 2.dp.toPx()
                            val boxX  = (sx - boxW / 2f)
                                .coerceIn(leftPad, leftPad + chartW - boxW)
                            val boxY  = (sy - boxH - 10.dp.toPx())
                                .coerceAtLeast(topPad)

                            canvas.nativeCanvas.drawRoundRect(
                                boxX, boxY, boxX + boxW, boxY + boxH,
                                6.dp.toPx(), 6.dp.toPx(), bgPaint
                            )
                            valuePaint.textAlign = android.graphics.Paint.Align.CENTER
                            datePaint.textAlign  = android.graphics.Paint.Align.CENTER
                            val cx = boxX + boxW / 2f
                            canvas.nativeCanvas.drawText(valueText, cx, boxY + pad + 12.sp.toPx(), valuePaint)
                            canvas.nativeCanvas.drawText(dateText,  cx, boxY + pad + 12.sp.toPx() + 2.dp.toPx() + 10.sp.toPx(), datePaint)
                        }
                    }
                }

                // Time-range slider — window start/end labels + persisted range
                if (history.size >= 3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            formatChartDate(visible.first().recordedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            formatChartDate(visible.last().recordedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    RangeSlider(
                        value = range,
                        onValueChange = { range = it },
                        onValueChangeFinished = {
                            prefs.edit()
                                .putFloat("nw_range_start", range.start)
                                .putFloat("nw_range_end", range.endInclusive)
                                .apply()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalStatusCard(title: String, status: String, onTrack: Boolean?, onClick: () -> Unit) {
    val containerColor = when (onTrack) {
        true -> Color(0xFF1B5E20).copy(alpha = 0.12f)
        false -> Color(0xFFB71C1C).copy(alpha = 0.12f)
        null -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChangeChip(label: String, change: Double) {
    val positive = change >= 0
    val color = if (positive) Color(0xFF1B5E20) else Color(0xFFB71C1C)
    val arrow = if (positive) "↑" else "↓"
    Column {
        Text(
            "$arrow ${formatMoney(abs(change))}",
            style = MaterialTheme.typography.bodyMedium,
            color = color
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Returns Pair(years, months) until retirement, or null if already past. */
private fun retirementCountdown(dateOfBirth: String, retirementAge: Int): Pair<Int, Int>? = runCatching {
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.UK)
        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
    val dob = fmt.parse(dateOfBirth) ?: return null
    val retirementCal = java.util.Calendar.getInstance().apply {
        time = dob
        add(java.util.Calendar.YEAR, retirementAge)
    }
    val nowCal = java.util.Calendar.getInstance()
    if (!retirementCal.after(nowCal)) return null
    var years  = retirementCal.get(java.util.Calendar.YEAR)  - nowCal.get(java.util.Calendar.YEAR)
    var months = retirementCal.get(java.util.Calendar.MONTH) - nowCal.get(java.util.Calendar.MONTH)
    if (months < 0) { years--; months += 12 }
    Pair(years, months)
}.getOrNull()

@Composable
private fun RetirementCard(profile: UserProfile?, pension: PensionProjection?) {
    if (profile == null || profile.dateOfBirth.isEmpty()) return

    val countdown = remember(profile.dateOfBirth, profile.retirementAge) {
        retirementCountdown(profile.dateOfBirth, profile.retirementAge)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Retirement",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (countdown != null) {
                val (years, months) = countdown
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        buildString {
                            if (years > 0)  append("$years yr${if (years  != 1) "s" else ""} ")
                            if (months > 0 || years == 0) append("$months mo")
                        }.trim(),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "to age ${profile.retirementAge}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            } else {
                Text(
                    "Retirement age reached 🎉",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Coast FIRE status chip
            pension?.let { proj ->
                if (proj.coastFireNumber <= 0) return@let
                val reached = proj.coastFireReached
                val pct = if (!reached && proj.coastFireNumber > 0)
                    (proj.currentValue / proj.coastFireNumber * 100).toInt().coerceIn(0, 99)
                else 100
                val chipText  = if (reached) "Coast FIRE reached ✓" else "Coast FIRE $pct% there"
                val chipColor = if (reached) Color(0xFF1B5E20).copy(alpha = 0.15f)
                                else         MaterialTheme.colorScheme.surfaceVariant
                Surface(
                    color = chipColor,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        chipText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (reached) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatChartValue(value: Float): String = when {
    value >= 1_000_000f -> "£%.1fM".format(value / 1_000_000f)
    value >= 1_000f     -> "£%.0fk".format(value / 1_000f)
    else                -> "£%.0f".format(value)
}

private val ukZone = java.time.ZoneId.of("Europe/London")

/** Epoch millis of a server timestamp (RFC 3339 with offset). */
private fun chartMillis(iso: String): Long =
    runCatching { java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrDefault(0L)

/** "Sep 26" in UK time — the server's month-end points fall in the right month there. */
private fun formatChartDate(iso: String): String = runCatching {
    java.time.format.DateTimeFormatter.ofPattern("MMM yy", java.util.Locale.UK)
        .withZone(ukZone)
        .format(java.time.OffsetDateTime.parse(iso).toInstant())
}.getOrDefault(iso.take(7))

/** "£1.25M", "£12,345", "£512.40", with a leading minus for negatives ("−£12,345"). */
private fun formatMoney(value: Double): String {
    val a = abs(value)
    val body = when {
        a >= 1_000_000 -> "£%.2fM".format(java.util.Locale.UK, a / 1_000_000)
        a >= 1_000 -> "£%,.0f".format(java.util.Locale.UK, a)
        else -> "£%,.2f".format(java.util.Locale.UK, a)
    }
    return if (value < 0 && a >= 0.005) "−$body" else body
}
