package com.finances.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.finances.app.data.IsaBridgeGoal

@Composable
fun IsaBridgeEditScreen(vm: FinancesViewModel, onBack: () -> Unit) {
    val accounts by vm.accounts.collectAsState()
    val projection by vm.isaProjection.collectAsState()
    val loaded by vm.isaLoaded.collectAsState()
    LaunchedEffect(Unit) { vm.loadIsaBridgeGoal() }

    val p = projection
    var accountId by remember(loaded) { mutableStateOf(p?.accountId?.toString() ?: "") }
    var monthlyContrib by remember(loaded) { mutableStateOf(p?.monthlyContribution?.toInputString() ?: "") }
    var growthRate by remember(loaded) { mutableStateOf(p?.annualGrowthRate?.toPercentString() ?: "7") }
    var inflationRate by remember(loaded) { mutableStateOf(p?.inflationRate?.toPercentString() ?: "3") }
    var glidepathYears by remember(loaded) { mutableStateOf(p?.glidepathYears?.toString() ?: "5") }
    var glidepathRate by remember(loaded) { mutableStateOf(p?.glidepathRate?.toPercentString() ?: "4") }
    var showErrors by remember { mutableStateOf(false) }

    val contribution = parseNumber(monthlyContrib, NumKind.Money)

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EditHeader("Edit ISA Bridge", onBack)
        if (!loaded) {
            GoalNotLoaded { vm.loadIsaBridgeGoal() }
            return@Column
        }
        HelpText("The bridge runs from your stop-work age to your pension draw age, with the income target and partner's pension from Settings → Profile.")

        AccountPicker("ISA Account", accounts.filter { it.category == "isa" }, accountId) { accountId = it }
        if (showErrors && accountId.isEmpty()) {
            Text("Select an account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        NumberField(
            monthlyContrib, { monthlyContrib = it }, "Monthly Contribution (£)", NumKind.Money, showErrors, Modifier.fillMaxWidth(),
            supporting = if (contribution != null && contribution > 20_000.0 / 12 + 0.005)
                "Over the £20,000 a year ISA allowance (£1,666.67/mo)" else null
        )
        NumberField(
            growthRate, { growthRate = it }, "Annual Growth Rate (%)", NumKind.Percent, showErrors, Modifier.fillMaxWidth(),
            supporting = "Before inflation, after fees"
        )
        NumberField(inflationRate, { inflationRate = it }, "Inflation Rate (%)", NumKind.Percent, showErrors, Modifier.fillMaxWidth())

        HorizontalDivider()
        Text("Glidepath", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HelpText("Reduced return for the last N years before the bridge starts and throughout it.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(glidepathYears, { glidepathYears = it }, "Years", NumKind.Whole, showErrors, Modifier.weight(1f))
            NumberField(glidepathRate, { glidepathRate = it }, "Reduced rate (%)", NumKind.Percent, showErrors, Modifier.weight(1f))
        }

        Button(
            onClick = {
                val goal = runCatching {
                    IsaBridgeGoal(
                        accountId = accountId.toIntOrNull()!!,
                        monthlyContribution = parseNumber(monthlyContrib, NumKind.Money)!!,
                        annualGrowthRate = parseNumber(growthRate, NumKind.Percent)!!,
                        inflationRate = parseNumber(inflationRate, NumKind.Percent)!!,
                        glidepathYears = parseNumber(glidepathYears, NumKind.Whole)!!.toInt(),
                        glidepathRate = parseNumber(glidepathRate, NumKind.Percent)!!
                    )
                }.getOrNull()
                if (goal == null || !fieldValid(monthlyContrib, NumKind.Money)) {
                    showErrors = true
                    return@Button
                }
                vm.saveIsaBridgeGoal(goal)
                onBack()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save") }
    }
}
