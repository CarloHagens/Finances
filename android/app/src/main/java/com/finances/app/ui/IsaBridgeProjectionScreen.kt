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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.finances.app.data.IsaBridgeProjection

@Composable
private fun HeroCard(proj: IsaBridgeProjection) {
    val needed = proj.requiredAtRetirement
    val projected = proj.projectedAtRetirement
    val onTrack = proj.onTrack
    val progress = if (needed > 0) (projected / needed).toFloat() else 1f
    val statusColor = if (onTrack) GreenStatus else AmberStatus
    val gap = needed - projected
    val start = if (proj.inBridge) "now" else "at ${proj.bridgeStartAge}"

    val summary = if (onTrack)
        "On track — ${fmtPot(projected)} $start covers ${fmtMonthly(proj.targetMonthlyIncome)} (today's money) through to ${proj.bridgeEndAge}."
    else
        "${fmtPot(gap.coerceAtLeast(0.0))} short $start — the pot runs dry at %.1f, before ${proj.bridgeEndAge}.".format(proj.runsOutAge)

    val milestones = buildList {
        add("${proj.bridgeStartAge}" to "Bridge starts")
        if (!proj.survivesToBridgeEnd && proj.runsOutAge > 0) add("%.0f".format(proj.runsOutAge) to "Runs dry")
        add("${proj.bridgeEndAge}" to "Pension starts")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.10f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(summary, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                FundingRing(progress, onTrack, "funded")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    MiniStat("Projected at ${proj.bridgeStartAge} · ≈${fmtPot(proj.projectedAtRetirementToday)} today", fmtPot(projected))
                    MiniStat("Pot needed · ≈${fmtPot(proj.requiredAtRetirementToday)} today", fmtPot(needed))
                    val label = if (gap <= 0) "Surplus" else "Shortfall"
                    val text = if (gap <= 0) "+${fmtPot(-gap)}" else "−${fmtPot(gap)}"
                    MiniStat(label, text, statusColor)
                }
            }
            NoteText("Large figures are future £ at ${proj.bridgeStartAge}, after inflation.")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            MilestoneTimeline(milestones)
        }
    }
}

@Composable
fun IsaBridgeProjectionScreen(vm: FinancesViewModel, onBack: () -> Unit, onEdit: () -> Unit) {
    val projection by vm.isaProjection.collectAsState()
    val profile by vm.profile.collectAsState()

    var menuExpanded by remember { mutableStateOf(false) }
    var showPosition by remember { mutableStateOf(false) }
    var showIncome by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }

    val currentAge: Double? = remember(profile) { profile?.dateOfBirth?.let(::ageFromIsoDate) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(
                    "ISA Bridge Goal",
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
            if (proj.accountMissing) add("The linked ISA account no longer exists — pick another in Edit parameters.")
            if (proj.inBridge) add("You're inside the bridge: the pot needed covers only the months left until ${proj.bridgeEndAge}.")
            if (proj.partnerPensionAssumed) add("Your partner's state pension is assumed to be paid throughout the bridge. Add their date of birth in Settings to start it at their State Pension age.")
            if (proj.contributionExceedsAllowance) add("Your contribution is over the £20,000 a year ISA allowance (£1,666.67/mo).")
        }
        item { WarningCard(warnings) }
        if (proj.profileIncomplete) return@LazyColumn

        item { HeroCard(proj) }

        // ── Bridge chart ──
        if (proj.schedule.size >= 2 && currentAge != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ISA through the bridge (future £)", style = MaterialTheme.typography.titleSmall)
                        val points = buildList {
                            add(currentAge.toFloat() to proj.currentValue.toFloat())
                            proj.schedule.forEach { add(it.age.toFloat() to it.balance.toFloat()) }
                        }
                        PhaseChart(
                            points = points,
                            bands = listOf(
                                ChartBand(currentAge.toFloat(), proj.bridgeStartAge.toFloat(), GreenStatus.copy(alpha = 0.08f)),
                                ChartBand(proj.bridgeStartAge.toFloat(), proj.bridgeEndAge.toFloat(), AmberStatus.copy(alpha = 0.08f))
                            ),
                            markers = listOf(proj.bridgeStartAge.toFloat(), proj.bridgeEndAge.toFloat())
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            LegendDot(GreenStatus, "Saving")
                            LegendDot(AmberStatus, "Drawing down")
                        }
                    }
                }
            }
        }

        // ── What it takes ──
        if (!proj.onTrack || proj.requiredLumpSumNoContrib > 0) {
            item {
                ExpandableCard("To hit the goal", showActions, { showActions = !showActions }) {
                    if (!proj.onTrack) {
                        if (proj.requiredMonthlyContribution > 0) {
                            InfoRow("Monthly contribution", fmtMonthly(proj.requiredMonthlyContribution))
                            val extra = proj.requiredMonthlyContribution - proj.monthlyContribution
                            if (extra > 0) InfoRow("vs your ${fmtMonthly(proj.monthlyContribution)}", "+${fmtMonthly(extra)}")
                            if (proj.requiredContributionExceedsAllowance) {
                                NoteText("Over the £20,000 a year ISA allowance — the excess would need another account.", AmberStatus)
                            }
                        }
                        if (proj.requiredLumpSum > 0) InfoRow("Lump sum today (keep contributing)", fmtPot(proj.requiredLumpSum))
                    }
                    InfoRow(
                        "Lump sum today (stop contributing)",
                        if (proj.requiredLumpSumNoContrib > 0) fmtPot(proj.requiredLumpSumNoContrib) else "Already there 🎉"
                    )
                    if (proj.lumpSumExceedsAllowance) {
                        NoteText("A lump sum over £20,000 can't all go into an ISA in one tax year.", AmberStatus)
                    }
                }
            }
        }

        item {
            ExpandableCard("Contributions & position", showPosition, { showPosition = !showPosition }) {
                InfoRow("Current value", fmtPot(proj.currentValue))
                InfoRow("Monthly contribution", fmtMonthly(proj.monthlyContribution))
                InfoRow("Growth rate", fmtRate(proj.annualGrowthRate))
                InfoRow("Glidepath", "${fmtRate(proj.glidepathRate)} for the last ${proj.glidepathYears} yrs and in the bridge")
                Spacer(Modifier.height(2.dp))
                SectionHeader("At age ${proj.bridgeStartAge} (future £)")
                InfoRow("Projected ISA value", fmtPot(proj.projectedAtRetirement))
                InfoRow("Pot needed", fmtPot(proj.requiredAtRetirement))
            }
        }

        item {
            ExpandableCard("Bridge income", showIncome, { showIncome = !showIncome }) {
                InfoRow("Target income (today's £)", fmtMonthly(proj.targetMonthlyIncome))
                InfoRow("Target income at ${proj.bridgeStartAge}", fmtMonthly(proj.inflatedTargetMonthlyIncome))
                if (proj.partnerStatePensionMonthly > 0) {
                    InfoRow("Partner pension (today's £)", fmtMonthly(proj.partnerStatePensionMonthly))
                    InfoRow("Partner pension at ${proj.bridgeStartAge}", fmtMonthly(proj.inflatedPartnerStatePension))
                }
                InfoRow("First ISA withdrawal", fmtMonthly(proj.inflatedMonthlyWithdrawal))
                NoteText("Withdrawals rise with inflation every month of the bridge; ISA withdrawals are tax-free.")
                Spacer(Modifier.height(2.dp))
                InfoRow("Survives to ${proj.bridgeEndAge}", if (proj.survivesToBridgeEnd) "✓ Yes" else "✗ No")
                if (proj.monthlyShortfall > 0) {
                    InfoRow("Shortfall at ${proj.bridgeStartAge}", fmtMonthly(proj.monthlyShortfall))
                }
            }
        }
    }
}
