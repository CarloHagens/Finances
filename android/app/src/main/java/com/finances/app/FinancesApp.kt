package com.finances.app

import android.app.Application
import com.finances.app.data.AppDatabase
import com.finances.app.data.Repository
import com.finances.app.ui.activeTheme
import com.finances.app.ui.themePresets

class FinancesApp : Application() {
    lateinit var repository: Repository
    private lateinit var db: AppDatabase

    override fun onCreate() {
        super.onCreate()
        // The app deals only in GBP: format numbers the UK way ("1,234.56") on
        // every device, whatever its language setting.
        java.util.Locale.setDefault(java.util.Locale.UK)
        db = AppDatabase.get(this)
        val prefs = getSharedPreferences("finances_prefs", MODE_PRIVATE)
        val savedTheme = prefs.getString("theme", null)
        if (savedTheme != null) {
            themePresets.find { it.name == savedTheme }?.let { activeTheme.value = it }
        }
        val serverUrl = prefs.getString("server_url", null) ?: Repository.DEFAULT_URL
        initRepository(serverUrl)
    }

    fun initRepository(serverUrl: String) {
        val url = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        repository = Repository(this, url)
        getSharedPreferences("finances_prefs", MODE_PRIVATE)
            .edit()
            .putString("server_url", url)
            .apply()
    }

    fun getServerUrl(): String =
        getSharedPreferences("finances_prefs", MODE_PRIVATE)
            .getString("server_url", Repository.DEFAULT_URL) ?: Repository.DEFAULT_URL
}
