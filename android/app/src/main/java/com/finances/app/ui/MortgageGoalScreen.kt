package com.finances.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.finances.app.data.MortgageGoal

@Composable
fun MortgageEditScreen(vm: FinancesViewModel, onBack: () -> Unit) {
    val accounts by vm.accounts.collectAsState()
    val projection by vm.mortgageProjection.collectAsState()
    val loaded by vm.mortgageLoaded.collectAsState()
    LaunchedEffect(Unit) { vm.loadMortgageGoal() }

    val p = projection
    var accountId by remember(loaded) { mutableStateOf(p?.accountId?.toString() ?: "") }
    var propertyId by remember(loaded) { mutableStateOf(p?.propertyAccountId?.toString() ?: "") }
    var monthlyPayment by remember(loaded) { mutableStateOf(p?.monthlyPayment?.toInputString() ?: "") }
    var monthlyOverpayment by remember(loaded) { mutableStateOf(p?.monthlyOverpayment?.toInputString() ?: "0") }
    var annualRate by remember(loaded) { mutableStateOf(p?.annualInterestRate?.toPercentString() ?: "") }
    var followOnRate by remember(loaded) { mutableStateOf(p?.followOnRate?.toPercentString() ?: "") }
    var targetLtv by remember(loaded) { mutableStateOf(p?.targetLtv?.toPercentString() ?: "60") }
    var allowance by remember(loaded) { mutableStateOf(p?.overpaymentAllowancePct?.toPercentString() ?: "10") }
    var fixedTermEnd by remember(loaded) { mutableStateOf(p?.fixedTermEnd ?: "") }
    var termEnd by remember(loaded) { mutableStateOf(p?.termEnd ?: "") }
    var showErrors by remember { mutableStateOf(false) }

    val properties = accounts.filter { it.category == "property" && it.type == "asset" }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EditHeader("Edit Mortgage", onBack)
        if (!loaded) {
            GoalNotLoaded { vm.loadMortgageGoal() }
            return@Column
        }

        AccountPicker("Mortgage Account", accounts.filter { it.category == "mortgage" }, accountId) { accountId = it }
        if (showErrors && accountId.isEmpty()) {
            Text("Select an account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        NumberField(monthlyPayment, { monthlyPayment = it }, "Monthly Payment (£)", NumKind.Money, showErrors, Modifier.fillMaxWidth())
        NumberField(monthlyOverpayment, { monthlyOverpayment = it }, "Monthly Overpayment (£)", NumKind.Money, showErrors, Modifier.fillMaxWidth())
        NumberField(
            allowance, { allowance = it }, "Penalty-free overpayment (% a year)", NumKind.Percent, showErrors, Modifier.fillMaxWidth(),
            supporting = "Of the balance on 1 January; you'll be warned if overpayments exceed it"
        )
        NumberField(annualRate, { annualRate = it }, "Interest Rate (%)", NumKind.Percent, showErrors, Modifier.fillMaxWidth())

        HorizontalDivider()
        Text("Fixed term & remortgage", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        DateField(fixedTermEnd, { fixedTermEnd = it }, "Fixed term ends", Modifier.fillMaxWidth(), clearable = true)
        NumberField(
            followOnRate, { followOnRate = it }, "Rate after the fixed term (%)", NumKind.Percent, showErrors, Modifier.fillMaxWidth(),
            optional = true, placeholder = "Blank = current rate + 1%",
            supporting = "Try a few values — the projection depends heavily on it"
        )
        DateField(
            termEnd, { termEnd = it }, "Mortgage ends (full term)", Modifier.fillMaxWidth(), clearable = true,
            supporting = "At remortgage the payment is recalculated to clear the balance by this date"
        )

        HorizontalDivider()
        Text("Remortgage LTV target", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HelpText("When can overpayments stop and still reach this loan-to-value band by the end of your fixed term?")
        AccountPicker("Property", properties, propertyId) { propertyId = it }
        NumberField(targetLtv, { targetLtv = it }, "Target LTV (%)", NumKind.Percent, showErrors, Modifier.fillMaxWidth(), placeholder = "e.g. 60")

        Button(
            onClick = {
                val goal = runCatching {
                    MortgageGoal(
                        accountId = accountId.toIntOrNull()!!,
                        propertyAccountId = propertyId.toIntOrNull(),
                        monthlyPayment = parseNumber(monthlyPayment, NumKind.Money)!!,
                        monthlyOverpayment = parseNumber(monthlyOverpayment, NumKind.Money)!!,
                        annualInterestRate = parseNumber(annualRate, NumKind.Percent)!!,
                        followOnRate = if (followOnRate.isBlank()) null else parseNumber(followOnRate, NumKind.Percent)!!,
                        targetLtv = parseNumber(targetLtv, NumKind.Percent)!!,
                        fixedTermEnd = fixedTermEnd,
                        termEnd = termEnd,
                        overpaymentAllowancePct = parseNumber(allowance, NumKind.Percent)!!
                    )
                }.getOrNull()
                val amountsValid = fieldValid(monthlyPayment, NumKind.Money) && fieldValid(monthlyOverpayment, NumKind.Money)
                if (goal == null || !amountsValid) {
                    showErrors = true
                    return@Button
                }
                vm.saveMortgageGoal(goal)
                onBack()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save") }
    }
}
