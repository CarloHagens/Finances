package com.finances.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun GoalsScreen(
    vm: FinancesViewModel,
    onMortgageTap: () -> Unit,
    onPensionTap: () -> Unit,
    onIsaTap: () -> Unit,
    onMortgageEdit: () -> Unit,
    onPensionEdit: () -> Unit,
    onIsaEdit: () -> Unit
) {
    val mortgage by vm.mortgageProjection.collectAsState()
    val pension by vm.pensionProjection.collectAsState()
    val isa by vm.isaProjection.collectAsState()
    val profile by vm.profile.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Goals",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        val m = mortgage
        GoalCard(
            title = "Mortgage",
            subtitle = profile?.let { "Clear by the time you stop work at ${it.retirementAge}" } ?: "Clear by the time you stop work",
            detail = mortgageStatusLine(m) ?: "Tap to configure",
            onTrack = m?.takeIf { it.status == "ok" || it.status == "never_pays_off" || it.status == "paid_off" }?.onTrack,
            onClick = onMortgageTap,
            onEdit = onMortgageEdit
        )
        val p = pension
        GoalCard(
            title = "Pension",
            subtitle = p?.takeIf { it.drawAge > 0 }?.let {
                "Draw from ${it.drawAge}: need ${fmtPot(it.inflatedTarget)} (≈${fmtPot(it.inflatedTargetToday)} today)"
            } ?: "Draw from your pension age",
            detail = when {
                p == null || p.accountId == null -> "Not configured"
                p.profileIncomplete -> "Add your date of birth in Settings"
                else -> "Projected ${fmtPot(p.projectedAtDraw)} · ${if (p.surplus >= 0) "surplus" else "shortfall"} ${fmtPot(kotlin.math.abs(p.surplus))}"
            },
            onTrack = p?.takeIf { it.accountId != null && !it.profileIncomplete }?.onTrack,
            onClick = onPensionTap,
            onEdit = onPensionEdit
        )
        val i = isa
        GoalCard(
            title = "ISA Bridge",
            subtitle = i?.takeIf { it.bridgeEndAge > 0 }?.let { "Bridge ages ${it.bridgeStartAge}–${it.bridgeEndAge}" }
                ?: profile?.let { "Bridge ages ${it.retirementAge}–${it.pensionAccessAge}" } ?: "Bridge to your pension",
            detail = when {
                i == null || i.accountId == null -> "Not configured"
                i.profileIncomplete -> "Add your date of birth in Settings"
                else -> "${fmtPot(i.projectedAtRetirement)} at ${i.bridgeStartAge} · " +
                    if (i.survivesToBridgeEnd) "lasts to ${i.bridgeEndAge}" else "runs out at %.1f".format(i.runsOutAge)
            },
            onTrack = i?.takeIf { it.accountId != null && !it.profileIncomplete }?.onTrack,
            onClick = onIsaTap,
            onEdit = onIsaEdit
        )
    }
}

/** One-line mortgage status shared by the Goals and Dashboard cards; null when there is no goal. */
internal fun mortgageStatusLine(m: com.finances.app.data.MortgageProjection?): String? = when (m?.status) {
    null -> null
    "not_configured" -> "Not configured"
    "account_missing" -> "Linked account missing — edit the goal"
    "paid_off" -> "Paid off 🎉"
    "invalid_balance" -> "Balance is negative — enter the amount owed"
    "never_pays_off" -> "Payment doesn't cover the interest — never clears"
    else -> when {
        m.profileIncomplete -> "Payoff ${formatGoalDate(m.projectedPayoffAt)} · ${fmtPot(m.currentBalance)} owed"
        m.onTrack -> "${m.monthsAheadBehind} months ahead · clear at %.1f".format(m.projectedAge)
        else -> "${-m.monthsAheadBehind} months behind · clear at %.1f".format(m.projectedAge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalCard(
    title: String,
    subtitle: String,
    detail: String,
    onTrack: Boolean?,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    val indicatorColor = when (onTrack) {
        true -> Color(0xFF2E7D32)
        false -> Color(0xFFC62828)
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    var menuExpanded by remember { mutableStateOf(false) }

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, "More options")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("View") },
                            leadingIcon = { Icon(Icons.Default.TrendingUp, null) },
                            onClick = { menuExpanded = false; onClick() }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit parameters") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { menuExpanded = false; onEdit() }
                        )
                    }
                }
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = indicatorColor)
        }
    }
}
