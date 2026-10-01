package com.finances.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountHistoryScreen(vm: FinancesViewModel, accountId: Int, onBack: () -> Unit) {
    val accounts by vm.accounts.collectAsState()
    val history by vm.accountHistory.collectAsState()
    val t212Config by vm.trading212Config.collectAsState()
    val syncing by vm.syncing.collectAsState()

    val account = accounts.find { it.id == accountId }
    val isT212Linked = account?.id == t212Config?.accountId
    val isT212Linkable = account?.category == "isa" && t212Config?.apiKey?.isNotBlank() == true

    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(accountId) { vm.loadAccountHistory(accountId) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add historical value")
            }
        },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    Text(
                        account?.name ?: "Account",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    if (isT212Linked && syncing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, "More options")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            if (isT212Linked) {
                                DropdownMenuItem(
                                    text = { Text("Sync Trading 212") },
                                    leadingIcon = { Icon(Icons.Default.Sync, null) },
                                    onClick = { vm.syncTrading212(); menuExpanded = false },
                                    enabled = !syncing
                                )
                            }
                            if (isT212Linkable) {
                                DropdownMenuItem(
                                    text = { Text(if (isT212Linked) "Unlink Trading 212" else "Link to Trading 212") },
                                    leadingIcon = {
                                        Icon(if (isT212Linked) Icons.Default.LinkOff else Icons.Default.Link, null)
                                    },
                                    onClick = {
                                        if (isT212Linked) vm.linkTrading212Account(null)
                                        else vm.linkTrading212Account(accountId)
                                        menuExpanded = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Edit balance") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = { showEditDialog = true; menuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Archive", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = { showDeleteDialog = true; menuExpanded = false }
                            )
                        }
                    }
                }
            }

            account?.let { acct ->
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "Current balance",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text("£%,.2f".format(acct.balance), style = MaterialTheme.typography.headlineSmall)
                            }
                            CategoryPill(acct.category)
                        }
                    }
                }
            }

            if (history.isEmpty()) {
                item {
                    Text(
                        "No history yet — tap + to add a historical value.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            } else {
                item {
                    Text(
                        "Balance History",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    HorizontalDivider()
                }
                items(history) { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(formatHistoryDate(entry.recordedAt), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "£%,.2f".format(entry.balance),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
    }

    account?.let { acct ->
        if (showAddDialog) {
            HistoricalEntryDialog(
                account = acct,
                onConfirm = { balance, date ->
                    vm.insertHistoricalBalance(acct.id, balance, date)
                    showAddDialog = false
                },
                onDismiss = { showAddDialog = false }
            )
        }
        if (showEditDialog) {
            EditBalanceDialog(
                account = acct,
                onConfirm = { balance -> vm.updateBalance(acct.id, balance); showEditDialog = false },
                onDismiss = { showEditDialog = false }
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Archive Account") },
            text = { Text("Archive \"${account?.name}\"? It disappears from your accounts, goals and current net worth, but stays in your net worth history up to today.") },
            confirmButton = {
                TextButton(onClick = { account?.let { vm.archiveAccount(it.id) }; onBack() }) {
                    Text("Archive", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }
}

private fun formatHistoryDate(isoDate: String): String {
    return runCatching {
        // Entries are timestamps; show the calendar day in UK time.
        val instant = java.time.OffsetDateTime.parse(isoDate).toInstant()
        java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.UK)
            .withZone(java.time.ZoneId.of("Europe/London"))
            .format(instant)
    }.getOrDefault(isoDate.take(10))
}
