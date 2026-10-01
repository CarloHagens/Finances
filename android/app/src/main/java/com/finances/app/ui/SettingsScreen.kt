package com.finances.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.finances.app.data.UserProfile

@Composable
fun SettingsScreen(vm: FinancesViewModel) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        ProfileSection(vm)

        Spacer(Modifier.height(32.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(24.dp))

        Trading212Section(vm)

        Spacer(Modifier.height(32.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(24.dp))

        ThemeSection(context)

        Spacer(Modifier.height(32.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(24.dp))

        ServerSection(vm.serverUrl) { vm.updateServerUrl(it) }
    }
}

@Composable
private fun ProfileSection(vm: FinancesViewModel) {
    val profile by vm.profile.collectAsState()
    var dob by remember(profile) { mutableStateOf(profile?.dateOfBirth ?: "") }
    var retirementAge by remember(profile) { mutableStateOf(profile?.retirementAge?.toString() ?: "53") }
    var pensionAge by remember(profile) { mutableStateOf(profile?.pensionAccessAge?.toString() ?: "58") }
    var targetIncome by remember(profile) { mutableStateOf(profile?.targetMonthlyIncome?.toInputString() ?: "") }
    var inflation by remember(profile) { mutableStateOf(profile?.inflationRate?.toPercentString() ?: "3") }
    var partnerDob by remember(profile) { mutableStateOf(profile?.partnerDateOfBirth ?: "") }
    var partnerPension by remember(profile) { mutableStateOf(profile?.partnerStatePensionMonthly?.toInputString() ?: "0") }
    var partnerSpa by remember(profile) {
        mutableStateOf(profile?.partnerStatePensionAge?.takeIf { it > 0 }?.toString() ?: "")
    }
    var partnerEndAge by remember(profile) {
        mutableStateOf(profile?.partnerPensionEndAge?.takeIf { it > 0 }?.toString() ?: "")
    }
    var singleIncome by remember(profile) {
        mutableStateOf(profile?.singleTargetMonthlyIncome?.takeIf { it > 0 }?.toInputString() ?: "")
    }
    var saved by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }

    val stopAge = parseNumber(retirementAge, NumKind.Whole)?.toInt()
    val drawAge = parseNumber(pensionAge, NumKind.Whole)?.toInt()
    val ageProblem = when {
        stopAge == null || drawAge == null -> null
        stopAge > drawAge -> "Stopping work must not be after drawing the pension"
        drawAge >= 100 -> "Pension age must be under 100"
        else -> null
    }

    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.secondary)
    Spacer(Modifier.height(8.dp))
    Text("Profile & retirement plan", style = MaterialTheme.typography.titleMedium)
    Text(
        "Shared by the pension and ISA bridge goals. Amounts are monthly, in today's money.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(16.dp))

    if (dob.isEmpty()) {
        Text(
            "Date of birth is required to calculate projections.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }

    DateField(dob, { dob = it; saved = false }, "Date of Birth", Modifier.fillMaxWidth(), placeholder = "Select your date of birth")
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(
            retirementAge, { retirementAge = it; saved = false }, "Stop work at", NumKind.Whole, showErrors,
            Modifier.weight(1f), supporting = "Contributions stop, ISA bridge starts"
        )
        NumberField(
            pensionAge, { pensionAge = it; saved = false }, "Draw pension at", NumKind.Whole, showErrors,
            Modifier.weight(1f), supporting = "ISA bridge ends"
        )
    }
    ageProblem?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    Spacer(Modifier.height(8.dp))
    NumberField(
        targetIncome, { targetIncome = it; saved = false }, "Household income target (£/mo)", NumKind.Money, showErrors,
        Modifier.fillMaxWidth(), placeholder = "e.g. 3,000", supporting = "After tax, today's money"
    )
    Spacer(Modifier.height(8.dp))
    NumberField(
        inflation, { inflation = it; saved = false }, "Inflation (% a year)", NumKind.Percent, showErrors,
        Modifier.fillMaxWidth(), supporting = "Used by both the pension and ISA bridge goals"
    )

    Spacer(Modifier.height(16.dp))
    Text("Partner", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
    DateField(
        partnerDob, { partnerDob = it; saved = false }, "Partner's date of birth", Modifier.fillMaxWidth(),
        placeholder = "Optional", clearable = true,
        supporting = "Starts their state pension at their State Pension age. Blank = assume already paid."
    )
    Spacer(Modifier.height(8.dp))
    NumberField(
        partnerPension, { partnerPension = it; saved = false }, "Partner's state pension (£/mo)", NumKind.Money, showErrors,
        Modifier.fillMaxWidth(), supporting = "Full new State Pension is about £1,046/mo (2026/27)"
    )
    Spacer(Modifier.height(8.dp))
    val legislatedSpa = profile?.partnerStatePensionAgeFromDob?.takeIf { it > 0 && partnerDob == profile?.partnerDateOfBirth }
    NumberField(
        partnerSpa, { partnerSpa = it; saved = false }, "Their State Pension age", NumKind.Whole, showErrors,
        Modifier.fillMaxWidth(), optional = true,
        placeholder = legislatedSpa?.let { fmtAge(it) },
        supporting = when {
            partnerDob.isEmpty() -> "Needs their date of birth"
            legislatedSpa != null -> "Blank = ${fmtAge(legislatedSpa)}, from their date of birth under current law"
            else -> "Blank = worked out from their date of birth under current law"
        }
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(
            partnerEndAge, { partnerEndAge = it; saved = false }, "Stops at their age", NumKind.Whole, showErrors,
            Modifier.weight(1f), optional = true, placeholder = "e.g. 95", supporting = "Blank = never"
        )
        NumberField(
            singleIncome, { singleIncome = it; saved = false }, "Then target (£/mo)", NumKind.Money, showErrors,
            Modifier.weight(1f), optional = true, supporting = "Blank = unchanged"
        )
    }

    Spacer(Modifier.height(16.dp))
    Button(
        onClick = {
            val target = parseNumber(targetIncome, NumKind.Money)
            val partner = parseNumber(partnerPension, NumKind.Money)
            val valid = dob.isNotEmpty() && stopAge != null && drawAge != null && ageProblem == null &&
                fieldValid(targetIncome, NumKind.Money) && fieldValid(partnerPension, NumKind.Money) &&
                fieldValid(inflation, NumKind.Percent, allowNegative = true) &&
                fieldValid(partnerEndAge, NumKind.Whole, optional = true) &&
                fieldValid(partnerSpa, NumKind.Whole, optional = true) &&
                (partnerSpa.isBlank() || partnerDob.isNotEmpty()) &&
                fieldValid(singleIncome, NumKind.Money, optional = true)
            if (!valid || target == null || partner == null) {
                showErrors = true
                return@Button
            }
            vm.saveProfile(UserProfile(
                dateOfBirth = dob,
                retirementAge = stopAge,
                pensionAccessAge = drawAge,
                targetMonthlyIncome = target,
                singleTargetMonthlyIncome = parseNumber(singleIncome, NumKind.Money) ?: 0.0,
                partnerDateOfBirth = partnerDob,
                partnerStatePensionMonthly = partner,
                partnerPensionEndAge = parseNumber(partnerEndAge, NumKind.Whole)?.toInt() ?: 0,
                partnerStatePensionAge = parseNumber(partnerSpa, NumKind.Whole)?.toInt() ?: 0,
                inflationRate = parseNumber(inflation, NumKind.Percent)!!
            ))
            saved = true
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        if (saved) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp).padding(end = 4.dp))
        Text(if (saved) "Saved" else "Save Profile")
    }
}

@Composable
private fun Trading212Section(vm: FinancesViewModel) {
    val config by vm.trading212Config.collectAsState()
    val storedParts = (config?.apiKey ?: "").split(":", limit = 2)
    var apiKey by remember(config) { mutableStateOf(if (storedParts.size == 2) storedParts[0] else config?.apiKey ?: "") }
    var secretKey by remember(config) { mutableStateOf(if (storedParts.size == 2) storedParts[1] else "") }
    var saved by remember { mutableStateOf(false) }

    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.secondary)
    Spacer(Modifier.height(8.dp))
    Text("Trading 212", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(16.dp))

    if (config?.apiKey?.isNotBlank() == true) {
        Text(
            "API key configured. Enter new keys below to update.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }

    OutlinedTextField(
        value = apiKey, onValueChange = { apiKey = it; saved = false },
        label = { Text("API Key") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = secretKey, onValueChange = { secretKey = it; saved = false },
        label = { Text("Secret Key") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = {
            if (apiKey.isNotBlank() && secretKey.isNotBlank()) {
                vm.saveTrading212Config("$apiKey:$secretKey")
                saved = true
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        if (saved) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp).padding(end = 4.dp))
        Text(if (saved) "Saved" else "Save")
    }
}

@Composable
private fun ThemeSection(context: android.content.Context) {
    Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.secondary)
    Spacer(Modifier.height(8.dp))
    Text("Theme", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(12.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(themePresets) { preset ->
            val isSelected = activeTheme.value.name == preset.name
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable {
                    activeTheme.value = preset
                    context.getSharedPreferences("finances_prefs", android.content.Context.MODE_PRIVATE)
                        .edit().putString("theme", preset.name).apply()
                }
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        drawRect(preset.primary, topLeft = Offset.Zero, size = Size(w / 3, h))
                        drawRect(preset.secondary, topLeft = Offset(w / 3, 0f), size = Size(w / 3, h))
                        drawRect(preset.tertiary, topLeft = Offset(2 * w / 3, 0f), size = Size(w / 3, h))
                    }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            preset.name.first().toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = preset.background
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    preset.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ServerSection(currentUrl: String, onUrlChange: (String) -> Unit) {
    var url by remember(currentUrl) { mutableStateOf(currentUrl) }
    var saved by remember { mutableStateOf(false) }

    Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.secondary)
    Spacer(Modifier.height(8.dp))
    Text("Server Connection", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = url, onValueChange = { url = it; saved = false },
        label = { Text("Server URL") },
        placeholder = { Text("http://192.168.1.100:9002") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { onUrlChange(url.trim()); saved = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        if (saved) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp).padding(end = 4.dp))
        Text(if (saved) "Saved" else "Save")
    }
}

/** An age that may include months: 67.0 → "67", 66.1667 → "66 yrs 2 mo". */
private fun fmtAge(age: Double): String {
    val years = age.toInt()
    val months = Math.round((age - years) * 12).toInt()
    return if (months == 0) "$years" else "$years yrs $months mo"
}
