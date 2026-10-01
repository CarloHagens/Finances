package com.finances.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem("dashboard", "Dashboard", Icons.Default.Dashboard),
    NavItem("accounts", "Accounts", Icons.Default.AccountBalance),
    NavItem("goals", "Goals", Icons.Default.TrendingUp),
    NavItem("settings", "Settings", Icons.Default.Settings)
)

@Composable
fun FinancesNavGraph(navController: NavHostController, vm: FinancesViewModel) {
    val snackbarHost = remember { SnackbarHostState() }
    val error by vm.error.collectAsState()

    LaunchedEffect(error) {
        error?.let { snackbarHost.showSnackbar(it); vm.clearError() }
    }

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            val topLevel = navItems.map { it.route }
            if (currentRoute in topLevel) {
                NavigationBar {
                    navItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo("dashboard") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("dashboard") {
                DashboardScreen(
                    vm = vm,
                    onMortgageTap = { navController.navigate("goal_mortgage") },
                    onPensionTap = { navController.navigate("goal_pension") },
                    onIsaTap = { navController.navigate("goal_isa") }
                )
            }
            composable("accounts") {
                AccountsScreen(vm = vm, onAccountClick = { id -> navController.navigate("account/$id") })
            }
            composable("account/{id}") { back ->
                val id = back.arguments?.getString("id")?.toIntOrNull() ?: return@composable
                AccountHistoryScreen(vm = vm, accountId = id, onBack = { navController.popBackStack() })
            }
            composable("goals") {
                GoalsScreen(
                    vm = vm,
                    onMortgageTap = { navController.navigate("goal_mortgage") },
                    onPensionTap = { navController.navigate("goal_pension") },
                    onIsaTap = { navController.navigate("goal_isa") },
                    onMortgageEdit = { navController.navigate("goal_mortgage_edit") },
                    onPensionEdit = { navController.navigate("goal_pension_edit") },
                    onIsaEdit = { navController.navigate("goal_isa_edit") }
                )
            }
            composable("settings") {
                SettingsScreen(vm = vm)
            }
            composable("goal_mortgage") {
                MortgageProjectionScreen(
                    vm = vm,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate("goal_mortgage_edit") }
                )
            }
            composable("goal_pension") {
                PensionProjectionScreen(
                    vm = vm,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate("goal_pension_edit") }
                )
            }
            composable("goal_isa") {
                IsaBridgeProjectionScreen(
                    vm = vm,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate("goal_isa_edit") }
                )
            }
            composable("goal_mortgage_edit") {
                MortgageEditScreen(vm = vm, onBack = { navController.popBackStack() })
            }
            composable("goal_pension_edit") {
                PensionEditScreen(vm = vm, onBack = { navController.popBackStack() })
            }
            composable("goal_isa_edit") {
                IsaBridgeEditScreen(vm = vm, onBack = { navController.popBackStack() })
            }
        }
    }
}
