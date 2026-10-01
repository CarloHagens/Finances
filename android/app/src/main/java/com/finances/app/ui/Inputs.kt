package com.finances.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue

// Shared form inputs.  Fields hold the user's text; it is only turned into a
// number on save, and a field that can't be read blocks the save with an error
// rather than silently becoming 0 or a default.

enum class NumKind { Money, Percent, Whole }

/** Inserts thousands separators into a raw amount for display ("1234.5" → "1,234.5"). */
fun formatCurrencyInput(raw: String): String {
    val stripped = raw.replace(",", "")
    val dotIdx = stripped.indexOf('.')
    val intPart = if (dotIdx >= 0) stripped.substring(0, dotIdx) else stripped
    val decPart = if (dotIdx >= 0) stripped.substring(dotIdx) else ""
    val formatted = intPart.toLongOrNull()?.let { "%,d".format(java.util.Locale.UK, it) } ?: intPart
    return formatted + decPart
}

/** A stored amount as editable text; 0 shows as "0", never as a blank. */
fun Double.toInputString() = if (this % 1.0 == 0.0) this.toLong().toString() else this.toString()

/** A stored decimal rate as editable percent text (0.0425 → "4.25", 0 → "0"). */
fun Double.toPercentString(): String {
    val pct = Math.round(this * 1_000_000.0) / 10_000.0
    return if (pct % 1.0 == 0.0) pct.toLong().toString() else pct.toString()
}

/**
 * Parses what the user typed.  Accepts "£1,234.50", "4.5%", and a decimal comma
 * ("4,5") in percent fields.  Returns null when the text isn't a number.
 */
fun parseNumber(raw: String, kind: NumKind): Double? {
    var s = raw.trim().replace("£", "").replace("%", "").replace(" ", "").replace(" ", "")
    if (s.isEmpty()) return null
    s = if (kind == NumKind.Percent && !s.contains('.')) s.replace(',', '.') else s.replace(",", "")
    val v = s.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
    return when (kind) {
        NumKind.Whole -> v.takeIf { it % 1.0 == 0.0 }
        NumKind.Percent -> v / 100.0
        NumKind.Money -> v
    }
}

/** True when [text] is acceptable for a field of [kind]; blank is acceptable only if [optional]. */
fun fieldValid(text: String, kind: NumKind, optional: Boolean = false, allowNegative: Boolean = false): Boolean {
    if (text.isBlank()) return optional
    val v = parseNumber(text, kind) ?: return false
    return allowNegative || v >= 0
}

/**
 * A numeric text field.  Money fields show thousands separators as you type.
 * When [showError] is set and the text isn't valid, the field turns red and says why.
 */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    kind: NumKind,
    showError: Boolean,
    modifier: Modifier = Modifier,
    optional: Boolean = false,
    placeholder: String? = null,
    supporting: String? = null
) {
    val invalid = showError && !fieldValid(value, kind, optional)
    val errorText = when {
        !invalid -> null
        value.isBlank() -> "Required"
        kind == NumKind.Whole -> "Enter a whole number"
        else -> "Enter a number"
    }
    val support: (@Composable () -> Unit)? = (errorText ?: supporting)?.let { { Text(it) } }
    val keyboard = KeyboardOptions(keyboardType = if (kind == NumKind.Whole) KeyboardType.Number else KeyboardType.Decimal)
    if (kind == NumKind.Money) {
        val shown = formatCurrencyInput(value)
        OutlinedTextField(
            value = TextFieldValue(shown, TextRange(shown.length)),
            onValueChange = { onValueChange(it.text.replace(",", "")) },
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            isError = invalid,
            supportingText = support,
            singleLine = true,
            keyboardOptions = keyboard,
            modifier = modifier
        )
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            isError = invalid,
            supportingText = support,
            singleLine = true,
            keyboardOptions = keyboard,
            modifier = modifier
        )
    }
}

private val isoDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.UK)
    .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }

fun String.isoDateToEpochMillis(): Long? = try {
    if (isBlank()) null else isoDate.parse(this)?.time
} catch (_: Exception) { null }

/** A read-only date field (yyyy-MM-dd) with a picker and, when [clearable], a way to clear it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "Select date",
    clearable: Boolean = false,
    supporting: String? = null
) {
    var showPicker by remember { mutableStateOf(false) }
    val state = rememberDatePickerState(initialSelectedDateMillis = value.isoDateToEpochMillis())

    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        supportingText = supporting?.let { { Text(it) } },
        trailingIcon = {
            IconButton(onClick = { showPicker = true }) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "Pick date")
            }
        },
        singleLine = true,
        modifier = modifier.clickable { showPicker = true }
    )
    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onValueChange(isoDate.format(java.util.Date(it))) }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                if (clearable && value.isNotEmpty()) {
                    TextButton(onClick = { onValueChange(""); showPicker = false }) { Text("Clear") }
                } else {
                    TextButton(onClick = { showPicker = false }) { Text("Cancel") }
                }
            }
        ) {
            DatePicker(state = state)
        }
    }
}
