package com.finances.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import com.finances.app.ui.FinancesNavGraph
import com.finances.app.ui.FinancesTheme
import com.finances.app.ui.FinancesViewModel
import com.finances.app.ui.FinancesViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = applicationContext as FinancesApp
        val vm = ViewModelProvider(this, FinancesViewModelFactory(app))[FinancesViewModel::class.java]
        setContent {
            FinancesTheme {
                val navController = rememberNavController()
                FinancesNavGraph(navController = navController, vm = vm)
            }
        }
    }
}
