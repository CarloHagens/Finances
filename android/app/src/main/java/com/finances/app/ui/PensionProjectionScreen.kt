package com.finances.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.finances.app.data.PensionProjection
import com.finances.app.data.PensionScheduleRow

/** Balance of a schedule row under the selected contribution scenario. */
private fun PensionScheduleRow.balanceFor(scenario: Int) = when (scenario) {
    1 -> balanceMin
    2 -> balanceNone
    else -> balance
}

@Composable
private fun HeroCard(proj: PensionProjection, scenario: Int, depletionAge: Double) {
    val projected = when (scenario) {
        1 -> proj.projectedAtDrawMinContrib
        2 -> proj.projectedAtDrawNoContrib
        else -> proj.projectedAtDraw
    }
    val needed = proj.inflatedTarget
    val surplus = projected - needed
    val onTrack = projected >= needed
    val progress = if (needed > 0) (projected / needed).toFloat() else 1f
    val statusColor = if (onTrack) GreenStatus else AmberStatus
    // Today's-money equivalents use the same deflator as the server.
    val deflator = if (proj.inflatedTargetToday > 0) proj.inflatedTarget / proj.inflatedTargetToday else 1.0

    val summary = if (onTrack)
        "On track to draw from ${proj.drawAge}: ${fmtPot(projected)} funds ${fmtMonthly(proj.targetMonthlyIncome)} (today's money, after tax) to age 100."
    else
        "${fmtPot(-surplus)} short at ${proj.drawAge}: ${fmtPot(projected)} projected vs ${fmtPot(needed)} needed."

    val milestones = buildList {
        if (proj.stopContributionAge < proj.drawAge) add(proj.stopContributionAge.toDouble() to "Stop")
        add(proj.drawAge.toDouble() to "Draw")
        if (proj.ownStatePensionMonthly > 0) add(proj.ownStatePensionAge.toDouble() to "State pension")
        if (depletionAge > 0) add(depletionAge to "Pot ends") else add(100.0 to "Still going")
    }.sortedBy { it.first }.map { "%.0f".format(it.first) to it.second }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.10f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(summary, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                FundingRing(progress, onTrack, "funded")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    MiniStat("Projected at ${proj.drawAge} · ≈${fmtPot(projected / deflator)} today", fmtPot(projected))
                    MiniStat("Needed at ${proj.drawAge} (lasts to 100) · ≈${fmtPot(proj.inflatedTargetToday)} today", fmtPot(needed))
                    val surplusLabel = if (surplus >= 0) "Surplus" else "Shortfall"
                    val surplusText = if (surplus >= 0) "+${fmtPot(surplus)}" else "−${fmtPot(-surplus)}"
                    MiniStat(surplusLabel, surplusText, statusColor)
                }
            }
            NoteText("Large figures are future £ at age ${proj.drawAge}, after inflation; “today” converts them back to today's money.")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            MilestoneTimeline(milestones)
        }
    }
}

@Composable
fun PensionProjectionScreen(vm: FinancesViewModel, onBack: () -> Unit, onEdit: () -> Unit) {
    val projection by vm.pensionProjection.collectAsState()
    val profile by vm.profile.collectAsState()

    var menuExpanded by remember { mutableStateOf(false) }
    var scenario by remember { mutableIntStateOf(0) } // 0 full, 1 min, 2 none

    var showProjections by remember { mutableStateOf(false) }
    var showIncome by remember { mutableStateOf(false) }
    var showCoast by remember { mutableStateOf(false) }

    val currentAge: Double? = remember(profile) { profile?.dateOfBirth?.let(::ageFromIsoDate) }

    // The pot runs dry under the selected scenario at the first drawdown year
    // whose balance reaches zero.
    val depletionAge = remember(projection, scenario) {
        projection?.schedule?.firstOrNull { it.phase == "draw" && it.balanceFor(scenario) <= 1.0 }?.age ?: 0.0
    }

    fun coastLine(reached: Boolean, date: String, age: Double): String = when {
        reached -> "Already reached 🎉"
        date.isNotEmpty() -> "Age %.1f · %s".format(age, formatGoalDate(date))
        else -> "Not reached before you stop work"
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(
                    "Pension Goal",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "More options") }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit parameters") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { menuExpanded = false; onEdit() }
                        )
                    }
                }
            }
        }

        val proj = projection
        if (proj == null) {
            item { NoteText("Not configured yet — tap ⋮ → Edit parameters.") }
            return@LazyColumn
        }

        val warnings = buildList {
            if (proj.profileIncomplete) add("Add your date of birth in Settings → Profile to see projections.")
            if (proj.accountMissing) add("The linked pension account no longer exists — pick another in Edit parameters.")
            if (proj.inDrawdown) add("You're past your pension draw age: the pot needed is sized from today to age 100.")
            if (proj.partnerPensionAssumed) add("Your partner's state pension is assumed to be paid already. Add their date of birth in Settings to start it at their State Pension age.")
        }
        item { WarningCard(warnings) }
        if (proj.profileIncomplete) return@LazyColumn

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(scenario == 0, { scenario = 0 }, label = { Text("Full") })
                if (proj.minMonthlyContrib > 0) FilterChip(scenario == 1, { scenario = 1 }, label = { Text("Min") })
                FilterChip(scenario == 2, { scenario = 2 }, label = { Text("None") })
            }
        }

        item { HeroCard(proj, scenario, depletionAge) }

        // ── Lifetime chart ──
        if (proj.schedule.isNotEmpty() && currentAge != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Pot over your lifetime (future £)", style = MaterialTheme.typography.titleSmall)
                        val points = buildList {
                            add(currentAge.toFloat() to proj.currentValue.toFloat())
                            proj.schedule.forEach { add(it.age.toFloat() to it.balanceFor(scenario).toFloat()) }
                        }
                        val lastAge = points.last().first
                        PhaseChart(
                            points = points,
                            bands = listOf(
                                ChartBand(currentAge.toFloat(), proj.stopContributionAge.toFloat(), GreenStatus.copy(alpha = 0.08f)),
                                ChartBand(proj.stopContributionAge.toFloat(), proj.drawAge.toFloat(), BlueStatus.copy(alpha = 0.07f)),
                                ChartBand(proj.drawAge.toFloat(), lastAge, AmberStatus.copy(alpha = 0.08f))
                            ),
                            markers = listOf(proj.drawAge.toFloat(), proj.ownStatePensionAge.toFloat())
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            LegendDot(GreenStatus, "Contributing")
                            LegendDot(BlueStatus, "Growing")
                            LegendDot(AmberStatus, "Drawing down")
                        }
                    }
                }
            }
        }

        item {
            ExpandableCard("Contributions & projections", showProjections, { showProjections = !showProjections }) {
                InfoRow("Current value", fmtPot(proj.currentValue))
                InfoRow("Growth", "${fmtRate(proj.annualGrowthRate)} then ${fmtRate(proj.glidepathRate)} for the last ${proj.glidepathYears} yrs and in drawdown")
                Spacer(Modifier.height(2.dp))
                SectionHeader("Projected at ${proj.drawAge} (future £)")
                InfoRow("Full contrib (${fmtMonthly(proj.monthlyContribution)})", fmtPot(proj.projectedAtDraw))
                if (proj.minMonthlyContrib > 0) {
                    InfoRow("Min contrib (${fmtMonthly(proj.minMonthlyContrib)})", fmtPot(proj.projectedAtDrawMinContrib))
                }
                InfoRow("No contributions", fmtPot(proj.projectedAtDrawNoContrib))
                NoteText("Contributions stay at the same £ amount until you stop at ${proj.stopContributionAge} — update them when they change.")
            }
        }

        item {
            ExpandableCard("Retirement income & tax", showIncome, { showIncome = !showIncome }) {
                SectionHeader("Household target (today's £)")
                InfoRow("Income after tax", fmtMonthly(proj.targetMonthlyIncome))
                if (proj.partnerStatePensionMonthly > 0) {
                    InfoRow("Partner's state pension", fmtMonthly(proj.partnerStatePensionMonthly))
                }
                if (proj.ownStatePensionMonthly > 0) {
                    InfoRow("Your state pension from ${proj.ownStatePensionAge}", fmtMonthly(proj.ownStatePensionMonthly))
                }
                InfoRow("Pot needed at ${proj.drawAge}", "${fmtPot(proj.inflatedTarget)} (≈${fmtPot(proj.inflatedTargetToday)} today)")

                Spacer(Modifier.height(4.dp))
                val hasOwn = proj.ownStatePensionMonthly > 0 && proj.ownStatePensionAge > proj.drawAge
                SectionHeader(
                    (if (hasOwn) "Drawdown ${proj.drawAge}–${proj.ownStatePensionAge}" else "Drawdown from ${proj.drawAge}") + " · today's £"
                )
                InfoRow("Gross withdrawal", fmtMonthly(proj.grossMonthlyDrawdown))
                InfoRow("Income tax", fmtMonthly(proj.taxMonthlyDrawdown))
                InfoRow("Net from pension", fmtMonthly(proj.netMonthlyDrawdown))
                if (hasOwn) {
                    Spacer(Modifier.height(2.dp))
                    SectionHeader("Drawdown ${proj.ownStatePensionAge}+ · today's £")
                    InfoRow("Gross withdrawal", fmtMonthly(proj.grossMonthlyDrawdownLate))
                    InfoRow("Income tax (incl. on state pension)", fmtMonthly(proj.taxMonthlyDrawdownLate))
                    InfoRow("Net from pension", fmtMonthly(proj.netMonthlyDrawdownLate))
                }
                if (proj.lumpSumCapAge > 0) {
                    InfoRow("25% tax-free cap used up at", "age %.1f".format(proj.lumpSumCapAge))
                }
                NoteText(
                    "Tax bands stay frozen until April 2031 and rise with inflation after that; the £100k " +
                        "allowance taper threshold is assumed never to rise. Withdrawals rise with inflation " +
                        "every month; the pot earns ${fmtRate(proj.glidepathRate)} a year in drawdown."
                )
            }
        }

        item {
            ExpandableCard("Coast FIRE", showCoast, { showCoast = !showCoast }) {
                fun lumpSum(coastNumber: Double, reached: Boolean): String =
                    if (reached) "Already there 🎉"
                    else fmtPot((coastNumber - proj.currentValue).coerceAtLeast(0.0))

                InfoRow("Required now (no contrib)", fmtPot(proj.coastFireNumber))
                InfoRow("Deposit today to coast now", lumpSum(proj.coastFireNumber, proj.coastFireReached))
                InfoRow("Reach date", coastLine(proj.coastFireReached, proj.coastFireDate, proj.coastFireAge))
                if (proj.minMonthlyContrib > 0) {
                    Spacer(Modifier.height(2.dp))
                    InfoRow("Required now (${fmtMonthly(proj.minMonthlyContrib)} min)", fmtPot(proj.minCoastFireNumber))
                    InfoRow("Deposit today to coast now", lumpSum(proj.minCoastFireNumber, proj.minCoastFireReached))
                    InfoRow("Reach date", coastLine(proj.minCoastFireReached, proj.minCoastFireDate, proj.minCoastFireAge))
                }
            }
        }
    }
}

/** Age in years today for an ISO date of birth, or null if it isn't one. */
fun ageFromIsoDate(iso: String): Double? = runCatching {
    val dob = java.time.LocalDate.parse(iso.take(10))
    java.time.temporal.ChronoUnit.DAYS.between(dob, java.time.LocalDate.now()) / 365.25
}.getOrNull()
