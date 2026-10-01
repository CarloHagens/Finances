package com.finances.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.finances.app.data.Account
import com.finances.app.data.PensionGoal

/** Header row shared by the goal edit screens. */
@Composable
internal fun EditHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** Shown instead of the form until the saved goal has loaded, so defaults can never overwrite it. */
@Composable
internal fun GoalNotLoaded(onRetry: () -> Unit) {
    Text(
        "Loading your saved goal… If this doesn't finish, check the server connection.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Button(onClick = onRetry) { Text("Retry") }
}

@Composable
internal fun HelpText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountPicker(label: String, accounts: List<Account>, selectedId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = accounts.find { it.id.toString() == selectedId }?.name ?: "Select account",
            onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { acct ->
                DropdownMenuItem(text = { Text(acct.name) }, onClick = { onSelect(acct.id.toString()); expanded = false })
            }
        }
    }
}

@Composable
fun PensionEditScreen(vm: FinancesViewModel, onBack: () -> Unit) {
    val accounts by vm.accounts.collectAsState()
    val projection by vm.pensionProjection.collectAsState()
    val loaded by vm.pensionLoaded.collectAsState()
    LaunchedEffect(Unit) { vm.loadPensionGoal() }

    // Initialised once the saved goal has loaded; later refreshes don't wipe edits.
    val p = projection
    var accountId by remember(loaded) { mutableStateOf(p?.accountId?.toString() ?: "") }
    var monthlyContrib by remember(loaded) { mutableStateOf(p?.monthlyContribution?.toInputString() ?: "") }
    var growthRate by remember(loaded) { mutableStateOf(p?.annualGrowthRate?.toPercentString() ?: "7") }
    var inflationRate by remember(loaded) { mutableStateOf(p?.inflationRate?.toPercentString() ?: "3") }
    var ownPension by remember(loaded) { mutableStateOf(p?.ownStatePensionMonthly?.toInputString() ?: "0") }
    var ownPensionAge by remember(loaded) { mutableStateOf(p?.ownStatePensionAge?.toString() ?: "68") }
    var minSalary by remember(loaded) { mutableStateOf(p?.minContribSalary?.toInputString() ?: "0") }
    var minRate by remember(loaded) { mutableStateOf(p?.minContribRate?.toPercentString() ?: "8") }
    var glidepathYears by remember(loaded) { mutableStateOf(p?.glidepathYears?.toString() ?: "5") }
    var glidepathRate by remember(loaded) { mutableStateOf(p?.glidepathRate?.toPercentString() ?: "4") }
    var showErrors by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EditHeader("Edit Pension", onBack)
        if (!loaded) {
            GoalNotLoaded { vm.loadPensionGoal() }
            return@Column
        }
        HelpText("Your stop-work and draw ages, income target and partner's pension are set in Settings → Profile.")

        AccountPicker("Pension Account", accounts.filter { it.category == "pension" }, accountId) { accountId = it }
        if (showErrors && accountId.isEmpty()) {
            Text("Select an account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        NumberField(monthlyContrib, { monthlyContrib = it }, "Monthly Contribution (£)", NumKind.Money, showErrors, Modifier.fillMaxWidth())
        NumberField(
            growthRate, { growthRate = it }, "Annual Growth Rate (%)", NumKind.Percent, showErrors, Modifier.fillMaxWidth(),
            supporting = "Before inflation, after fees"
        )
        NumberField(inflationRate, { inflationRate = it }, "Inflation (%)", NumKind.Percent, showErrors, Modifier.fillMaxWidth())

        HelpText("Your own state pension (today's £) — reduces pension drawdown from the age below. Enter whatever you're comfortable counting on.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(ownPension, { ownPension = it }, "Your pension £/mo", NumKind.Money, showErrors, Modifier.weight(2f), placeholder = "e.g. 1,046")
            NumberField(ownPensionAge, { ownPensionAge = it }, "From age", NumKind.Whole, showErrors, Modifier.weight(1f))
        }

        HorizontalDivider()
        Text("Minimum-contribution coast", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HelpText("At what salary + minimum contribution % would you coast instead of stopping entirely?")
        NumberField(minSalary, { minSalary = it }, "Salary (£/year)", NumKind.Money, showErrors, Modifier.fillMaxWidth())
        NumberField(minRate, { minRate = it }, "Minimum contribution (%)", NumKind.Percent, showErrors, Modifier.fillMaxWidth())

        HorizontalDivider()
        Text("Glidepath", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HelpText("Reduced return for the last N years before draw age and throughout drawdown (shift from equity to bonds).")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(glidepathYears, { glidepathYears = it }, "Years", NumKind.Whole, showErrors, Modifier.weight(1f))
            NumberField(glidepathRate, { glidepathRate = it }, "Reduced rate (%)", NumKind.Percent, showErrors, Modifier.weight(1f))
        }

        Button(
            onClick = {
                val goal = runCatching {
                    PensionGoal(
                        accountId = accountId.toIntOrNull()!!,
                        monthlyContribution = parseNumber(monthlyContrib, NumKind.Money)!!,
                        annualGrowthRate = parseNumber(growthRate, NumKind.Percent)!!,
                        inflationRate = parseNumber(inflationRate, NumKind.Percent)!!,
                        ownStatePensionMonthly = parseNumber(ownPension, NumKind.Money)!!,
                        ownStatePensionAge = parseNumber(ownPensionAge, NumKind.Whole)!!.toInt(),
                        minContribSalary = parseNumber(minSalary, NumKind.Money)!!,
                        minContribRate = parseNumber(minRate, NumKind.Percent)!!,
                        glidepathYears = parseNumber(glidepathYears, NumKind.Whole)!!.toInt(),
                        glidepathRate = parseNumber(glidepathRate, NumKind.Percent)!!
                    )
                }.getOrNull()
                val amountsValid = listOf(monthlyContrib, ownPension, minSalary).all { fieldValid(it, NumKind.Money) }
                if (goal == null || !amountsValid) {
                    showErrors = true
                    return@Button
                }
                vm.savePensionGoal(goal)
                onBack()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save") }
    }
}
