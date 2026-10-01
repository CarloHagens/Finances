package com.finances.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.finances.app.data.Account
import com.finances.app.data.CreateAccountRequest

private val accountCategories = listOf(
    "checking", "savings", "isa", "pension", "property",
    "mortgage", "loan", "credit_card", "other"
)

internal data class CategoryStyle(val icon: ImageVector, val color: Color, val label: String)

internal val categoryStyles = mapOf(
    "checking"    to CategoryStyle(Icons.Default.AccountBalance, Color(0xFF1565C0), "Checking"),
    "savings"     to CategoryStyle(Icons.Default.Savings,        Color(0xFF2E7D32), "Savings"),
    "isa"         to CategoryStyle(Icons.Default.TrendingUp,     Color(0xFF6A1B9A), "ISA"),
    "pension"     to CategoryStyle(Icons.Default.AccountCircle,  Color(0xFF00695C), "Pension"),
    "property"    to CategoryStyle(Icons.Default.Home,           Color(0xFFE65100), "Property"),
    "mortgage"    to CategoryStyle(Icons.Default.Home,           Color(0xFFC62828), "Mortgage"),
    "loan"        to CategoryStyle(Icons.Default.AttachMoney,    Color(0xFF6D4C41), "Loan"),
    "credit_card" to CategoryStyle(Icons.Default.CreditCard,     Color(0xFF37474F), "Credit Card"),
    "other"       to CategoryStyle(Icons.Default.Category,       Color(0xFF546E7A), "Other")
)

@Composable
internal fun CategoryPill(category: String) {
    val fallback = CategoryStyle(
        Icons.Default.Category,
        Color(0xFF546E7A),
        category.replace("_", " ").replaceFirstChar { it.uppercase() }
    )
    val style = categoryStyles[category] ?: fallback
    Surface(shape = RoundedCornerShape(50), color = style.color.copy(alpha = 0.12f)) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(style.icon, contentDescription = null, tint = style.color, modifier = Modifier.size(11.dp))
            Text(style.label, style = MaterialTheme.typography.labelSmall, color = style.color)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(vm: FinancesViewModel, onAccountClick: (Int) -> Unit) {
    val accounts by vm.accounts.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editAccount by remember { mutableStateOf<Account?>(null) }
    var deleteConfirmAccount by remember { mutableStateOf<Account?>(null) }

    val t212Config by vm.trading212Config.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val assets = accounts.filter { it.type == "asset" }
    val liabilities = accounts.filter { it.type == "liability" }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add account")
            }
        },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "Accounts",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            if (assets.isNotEmpty()) {
                item { SectionHeader("Assets", assets.sumOf { it.balance }) }
                items(assets) { acct ->
                    AccountRow(
                        acct,
                        isT212Linked = acct.id == t212Config?.accountId,
                        isT212Linkable = acct.category == "isa" && t212Config?.apiKey?.isNotBlank() == true,
                        syncing = syncing,
                        onClick = { onAccountClick(acct.id) },
                        onEdit = { editAccount = acct },
                        onDelete = { deleteConfirmAccount = acct },
                        onSync = { vm.syncTrading212() },
                        onToggleT212Link = {
                            if (acct.id == t212Config?.accountId) vm.linkTrading212Account(null)
                            else vm.linkTrading212Account(acct.id)
                        }
                    )
                }
            }
            if (liabilities.isNotEmpty()) {
                item { SectionHeader("Liabilities", liabilities.sumOf { it.balance }) }
                items(liabilities) { acct ->
                    AccountRow(
                        acct,
                        isT212Linked = false,
                        isT212Linkable = false,
                        syncing = false,
                        onClick = { onAccountClick(acct.id) },
                        onEdit = { editAccount = acct },
                        onDelete = { deleteConfirmAccount = acct },
                        onSync = {},
                        onToggleT212Link = {}
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddAccountDialog(
            onConfirm = { req -> vm.createAccount(req); showAddDialog = false },
            onDismiss = { showAddDialog = false }
        )
    }

    editAccount?.let { acct ->
        EditBalanceDialog(
            account = acct,
            onConfirm = { balance -> vm.updateBalance(acct.id, balance); editAccount = null },
            onDismiss = { editAccount = null }
        )
    }

    deleteConfirmAccount?.let { acct ->
        AlertDialog(
            onDismissRequest = { deleteConfirmAccount = null },
            title = { Text("Archive Account") },
            text = { Text("Archive \"${acct.name}\"? It disappears from your accounts, goals and current net worth, but stays in your net worth history up to today.") },
            confirmButton = {
                TextButton(onClick = { vm.archiveAccount(acct.id); deleteConfirmAccount = null }) {
                    Text("Archive", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmAccount = null }) { Text("Cancel") }
            }
        )
    }

}

@Composable
private fun SectionHeader(title: String, total: Double) {
    Column(modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text("£%,.2f".format(total), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        HorizontalDivider()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountRow(
    account: Account,
    isT212Linked: Boolean,
    isT212Linkable: Boolean,
    syncing: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSync: () -> Unit,
    onToggleT212Link: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(account.name, style = MaterialTheme.typography.bodyLarge)
                CategoryPill(account.category)
            }
            Text("£%,.2f".format(account.balance), style = MaterialTheme.typography.bodyLarge)
            if (isT212Linked && syncing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp).padding(start = 8.dp), strokeWidth = 2.dp)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    if (isT212Linked) {
                        DropdownMenuItem(
                            text = { Text("Sync Trading 212") },
                            leadingIcon = { Icon(Icons.Default.Sync, contentDescription = null) },
                            onClick = { onSync(); menuExpanded = false },
                            enabled = !syncing
                        )
                    }
                    if (isT212Linkable) {
                        DropdownMenuItem(
                            text = { Text(if (isT212Linked) "Unlink Trading 212" else "Link to Trading 212") },
                            leadingIcon = {
                                Icon(
                                    if (isT212Linked) Icons.Default.LinkOff else Icons.Default.Link,
                                    contentDescription = null
                                )
                            },
                            onClick = { onToggleT212Link(); menuExpanded = false }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Edit balance") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { onEdit(); menuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Archive", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { onDelete(); menuExpanded = false }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAccountDialog(onConfirm: (CreateAccountRequest) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("asset") }
    var category by remember { mutableStateOf("checking") }
    var balance by remember { mutableStateOf("") }
    var typeExpanded by remember { mutableStateOf(false) }
    var catExpanded by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Name") },
                    isError = showErrors && name.isBlank(),
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                    OutlinedTextField(
                        value = type, onValueChange = {}, readOnly = true, label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        listOf("asset", "liability").forEach { opt ->
                            DropdownMenuItem(text = { Text(opt) }, onClick = { type = opt; typeExpanded = false })
                        }
                    }
                }

                ExposedDropdownMenuBox(expanded = catExpanded, onExpandedChange = { catExpanded = it }) {
                    OutlinedTextField(
                        value = category, onValueChange = {}, readOnly = true, label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(catExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                        accountCategories.forEach { opt ->
                            DropdownMenuItem(text = { Text(opt) }, onClick = { category = opt; catExpanded = false })
                        }
                    }
                }

                NumberField(
                    value = balance, onValueChange = { balance = it },
                    label = if (type == "liability") "Amount owed (£)" else "Opening Balance (£)",
                    kind = NumKind.Money, showError = showErrors,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val b = parseNumber(balance, NumKind.Money)
                if (name.isBlank() || b == null || (type == "liability" && b < 0)) {
                    showErrors = true
                    return@TextButton
                }
                onConfirm(CreateAccountRequest(name.trim(), type, category, b))
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun EditBalanceDialog(account: Account, onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    var balance by remember { mutableStateOf(account.balance.toInputString()) }
    var showErrors by remember { mutableStateOf(false) }
    val liability = account.type == "liability"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(account.name) },
        text = {
            NumberField(
                value = balance, onValueChange = { balance = it },
                label = if (liability) "Amount owed (£)" else "Balance (£)",
                kind = NumKind.Money, showError = showErrors,
                supporting = if (liability) "Enter what you owe as a positive number" else null,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val b = parseNumber(balance, NumKind.Money)
                if (b == null || (liability && b < 0)) { showErrors = true; return@TextButton }
                onConfirm(b)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoricalEntryDialog(
    account: Account,
    onConfirm: (Double, String) -> Unit,
    onDismiss: () -> Unit
) {
    var balance by remember { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())

    val selectedDateStr = remember(datePickerState.selectedDateMillis) {
        datePickerState.selectedDateMillis?.let {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.UK)
                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                .format(java.util.Date(it))
        } ?: ""
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = { showDatePicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Historical Value") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(account.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NumberField(
                    value = balance, onValueChange = { balance = it },
                    label = if (account.type == "liability") "Amount owed (£)" else "Balance (£)",
                    kind = NumKind.Money, showError = showErrors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = selectedDateStr,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date") },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Pick date")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val b = parseNumber(balance, NumKind.Money)
                if (b == null || (account.type == "liability" && b < 0)) { showErrors = true; return@TextButton }
                onConfirm(b, selectedDateStr)
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
