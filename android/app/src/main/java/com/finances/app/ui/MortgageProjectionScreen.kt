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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.finances.app.data.MortgageProjection
import com.finances.app.data.MortgageScenario
import kotlin.math.roundToInt

private fun ltvPercent(ltv: Double) = "${(ltv * 100).roundToInt()}%"

@Composable
private fun HeroCard(proj: MortgageProjection, scenario: MortgageScenario?, retirementAge: Int?) {
    val onTrack = proj.onTrack
    val statusColor = if (onTrack) GreenStatus else AmberStatus
    val hasProperty = proj.propertyValue > 0
    val equity = if (hasProperty) ((proj.propertyValue - proj.currentBalance) / proj.propertyValue).toFloat() else 0f

    val months = kotlin.math.abs(proj.monthsAheadBehind)
    val summary = when {
        proj.neverPaysOff -> "Your payment doesn't cover the interest — the mortgage never clears at these figures."
        retirementAge == null -> "Mortgage clear at age %.1f.".format(proj.projectedAge)
        onTrack -> "On track — clear at age %.1f, $months months before you stop work at $retirementAge.".format(proj.projectedAge)
        else -> "Behind — clear at age %.1f, $months months after you stop work at $retirementAge.".format(proj.projectedAge)
    }

    val milestones = buildList {
        if (proj.monthlyOverpayment > 0 && proj.stopOverpaymentAge > 0) {
            add("%.0f".format(proj.stopOverpaymentAge) to "Stop overpay")
        }
        if (proj.fixedTermEnd.isNotEmpty() && proj.ltvStopOverpaymentAge > 0) {
            add("%.0f".format(proj.ltvStopOverpaymentAge) to "${ltvPercent(proj.targetLtv)} LTV")
        }
        if (!proj.neverPaysOff) add("%.0f".format(proj.projectedAge) to "Paid off")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.10f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(summary, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                if (hasProperty) FundingRing(equity, onTrack, "equity")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    MiniStat("Balance owed", fmtPot(proj.currentBalance))
                    if (hasProperty) MiniStat("Current LTV", "%.1f%%".format(proj.currentLtv * 100))
                    scenario?.let {
                        MiniStat(
                            "Paid off — ${it.label.lowercase()}",
                            if (it.neverPaysOff) "Never" else "age %.1f".format(it.payoffAge),
                            statusColor
                        )
                    }
                }
            }
            if (milestones.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                MilestoneTimeline(milestones)
            }
        }
    }
}

@Composable
fun MortgageProjectionScreen(vm: FinancesViewModel, onBack: () -> Unit, onEdit: () -> Unit) {
    val projection by vm.mortgageProjection.collectAsState()
    val profile by vm.profile.collectAsState()

    var menuExpanded by remember { mutableStateOf(false) }
    var selectedKey by remember { mutableStateOf("overpay") }
    var showPayments by remember { mutableStateOf(false) }
    var showLtv by remember { mutableStateOf(false) }

    val currentAge: Double? = remember(profile) { profile?.dateOfBirth?.let(::ageFromIsoDate) }
    val retirementAge = profile?.retirementAge

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(
                    "Mortgage Goal",
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
        val statusMessage = when (proj?.status) {
            null, "not_configured" -> "Not configured yet — tap ⋮ → Edit parameters."
            "account_missing" -> "The linked mortgage account no longer exists — pick another in Edit parameters."
            "paid_off" -> "Paid off 🎉 — the mortgage account balance is £0."
            "invalid_balance" -> "The mortgage balance is negative. Enter the amount owed as a positive number."
            else -> null
        }
        if (proj == null || statusMessage != null) {
            item { NoteText(statusMessage ?: "") }
            return@LazyColumn
        }

        val scenario = proj.scenarios.firstOrNull { it.key == selectedKey } ?: proj.scenarios.firstOrNull()

        val warnings = buildList {
            if (proj.profileIncomplete) add("Add your date of birth in Settings → Profile to compare payoff with your stop-work age.")
            if (proj.fixedTermEnd.isNotEmpty()) {
                if (proj.followOnRateAssumed) {
                    add("After your fixed term ends (${formatGoalDate(proj.fixedTermEnd)}) the rate is assumed to be ${fmtRate(proj.effectiveFollowOnRate)} (current + 1%). Set it in Edit parameters and try a few values.")
                } else {
                    add("After ${formatGoalDate(proj.fixedTermEnd)} the rate is assumed to be ${fmtRate(proj.effectiveFollowOnRate)}.")
                }
                if (!proj.paymentRecalculated) add("Add the mortgage end date so the payment is recalculated at remortgage; until then it stays at ${fmtMonthly(proj.monthlyPayment)}.")
            } else {
                add("No fixed-term end date set: today's rate is assumed for the whole mortgage.")
            }
            if (proj.overpaymentAllowanceExceededYear > 0) {
                add("Overpayments exceed your ${fmtRate(proj.overpaymentAllowancePct)} penalty-free allowance from ${proj.overpaymentAllowanceExceededYear} (this year's limit ≈ ${fmtPot(proj.overpaymentAllowance)}). Early repayment charges may apply.")
            }
            if (proj.propertyAccountAmbiguous) add("You have more than one property account — pick the one this mortgage is on in Edit parameters to see LTV.")
        }
        item { WarningCard(warnings) }

        // Scenario — drives the chart and the payoff figures below.
        if (proj.scenarios.size > 1) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    proj.scenarios.forEach { s ->
                        val label = when (s.key) {
                            "overpay" -> "Overpay"
                            "stop" -> "Stop at %.0f".format(s.overpayUntilAge)
                            else -> "Regular only"
                        }
                        FilterChip(scenario?.key == s.key, { selectedKey = s.key }, label = { Text(label) })
                    }
                }
            }
        }

        item { HeroCard(proj, scenario, retirementAge) }

        // ── Balance chart ──
        if (scenario != null && scenario.points.isNotEmpty() && currentAge != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Balance until payoff", style = MaterialTheme.typography.titleSmall)
                        val points = buildList {
                            add(currentAge.toFloat() to proj.currentBalance.toFloat())
                            scenario.points.forEach { add(it.age.toFloat() to it.balance.toFloat()) }
                        }
                        val endAge = points.last().first
                        val overpayUntil = when (scenario.key) {
                            "overpay" -> endAge
                            "stop" -> scenario.overpayUntilAge.toFloat()
                            else -> currentAge.toFloat()
                        }
                        PhaseChart(
                            points = points,
                            bands = listOf(
                                ChartBand(currentAge.toFloat(), overpayUntil, GreenStatus.copy(alpha = 0.08f)),
                                ChartBand(overpayUntil, endAge, BlueStatus.copy(alpha = 0.07f))
                            ),
                            markers = listOfNotNull(retirementAge?.toFloat())
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            if (scenario.key != "regular") LegendDot(GreenStatus, "Overpaying")
                            LegendDot(BlueStatus, "Regular only")
                            if (retirementAge != null) {
                                LegendDot(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), "Stop work at $retirementAge")
                            }
                        }
                    }
                }
            }
        }

        item {
            ExpandableCard("Payments & interest", showPayments, { showPayments = !showPayments }) {
                InfoRow("Current balance", fmtPot(proj.currentBalance))
                InfoRow("Monthly payment", fmtMonthly(proj.monthlyPayment))
                if (proj.monthlyOverpayment > 0) InfoRow("Overpayment", fmtMonthly(proj.monthlyOverpayment))
                InfoRow("Interest rate", fmtRate(proj.annualInterestRate))
                if (proj.fixedTermEnd.isNotEmpty()) {
                    InfoRow("From ${formatGoalDate(proj.fixedTermEnd)}", fmtRate(proj.effectiveFollowOnRate) + if (proj.followOnRateAssumed) " (assumed)" else "")
                }
                scenario?.let { s ->
                    Spacer(Modifier.height(2.dp))
                    SectionHeader(s.label)
                    if (s.neverPaysOff) {
                        InfoRow("Payoff", "Never at these figures")
                    } else {
                        InfoRow("Payoff date", formatGoalDate(s.payoffDate))
                        if (s.payoffAge > 0) InfoRow("Age at payoff", "%.1f".format(s.payoffAge))
                        InfoRow("Total interest to payoff", fmtPot(s.totalInterest))
                    }
                }
                if (proj.interestSaved > 0) {
                    InfoRow("Interest saved by overpaying", fmtPot(proj.interestSaved))
                }
                if (!proj.neverPaysOff && retirementAge != null) {
                    InfoRow(
                        "With full overpayment vs stopping work",
                        if (proj.monthsAheadBehind >= 0) "${proj.monthsAheadBehind} months ahead"
                        else "${-proj.monthsAheadBehind} months behind"
                    )
                }
                if (proj.monthlyOverpayment > 0) {
                    Spacer(Modifier.height(2.dp))
                    val stopLine = when {
                        proj.stopOverpaymentReached -> "Now — regular payments clear it in time 🎉"
                        proj.stopOverpaymentDate.isNotEmpty() ->
                            "Age %.1f · %s".format(proj.stopOverpaymentAge, formatGoalDate(proj.stopOverpaymentDate))
                        else -> "Not possible — overpaying throughout still misses it"
                    }
                    InfoRow("Can stop overpaying and still clear by $retirementAge", stopLine)
                }
            }
        }

        if (proj.fixedTermEnd.isNotEmpty() && proj.propertyValue > 0 && proj.targetLtvBalance > 0) {
            item {
                val ltvPct = ltvPercent(proj.targetLtv)
                ExpandableCard("Remortgage at $ltvPct LTV", showLtv, { showLtv = !showLtv }) {
                    InfoRow("Property value", fmtPot(proj.propertyValue))
                    InfoRow("Current LTV", "%.1f%%".format(proj.currentLtv * 100))
                    InfoRow("Target balance", fmtPot(proj.targetLtvBalance))
                    InfoRow("By fixed-term end", formatGoalDate(proj.fixedTermEnd))
                    val ltvStopLine = when {
                        proj.ltvAlreadyBelow -> "Already below $ltvPct LTV 🎉"
                        proj.ltvStopOverpaymentReached -> "Regular payments alone reach it 🎉"
                        proj.ltvStopOverpaymentDate.isNotEmpty() ->
                            "Age %.1f · %s".format(proj.ltvStopOverpaymentAge, formatGoalDate(proj.ltvStopOverpaymentDate))
                        else -> "Cannot reach $ltvPct LTV by term end"
                    }
                    InfoRow(if (proj.monthlyOverpayment > 0) "Can stop overpaying" else "Reached?", ltvStopLine)
                    NoteText("Uses today's property value — no house-price change is assumed.")
                }
            }
        }
    }
}
