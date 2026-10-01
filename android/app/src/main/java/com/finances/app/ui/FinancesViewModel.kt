package com.finances.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.finances.app.FinancesApp
import com.finances.app.data.Account
import com.finances.app.data.AccountHistoryEntry
import com.finances.app.data.CreateAccountRequest
import com.finances.app.data.IsaBridgeGoal
import com.finances.app.data.IsaBridgeProjection
import com.finances.app.data.MortgageGoal
import com.finances.app.data.MortgageProjection
import com.finances.app.data.NetWorthPoint
import com.finances.app.data.NetWorthSummary
import com.finances.app.data.PensionGoal
import com.finances.app.data.PensionProjection
import com.finances.app.data.Trading212Config
import com.finances.app.data.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** A readable message for a failed call, using the server's error text when there is one. */
private fun Throwable.userMessage(): String =
    (this as? HttpException)?.response()?.errorBody()?.string()?.trim()?.takeIf { it.isNotEmpty() }
        ?: message ?: toString()

/**
 * One goal's projection plus whether it has been loaded from the server at least
 * once.  Edit screens only allow saving once [loaded] is true, so a failed load can
 * never let defaults overwrite a saved goal.  Each new load or save cancels the
 * previous one, so a slow, older response can't overwrite a newer projection.
 */
private class GoalSlot<T>(
    private val name: String,
    private val fetch: suspend () -> T,
    private val onError: (String) -> Unit
) {
    val projection = MutableStateFlow<T?>(null)
    val loaded = MutableStateFlow(false)
    private var job: Job? = null

    fun load(scope: CoroutineScope) = run(scope) {
        try {
            projection.value = fetch()
            loaded.value = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (e.code() == 404) {
                projection.value = null // not configured yet
                loaded.value = true
            } else onError("Couldn't refresh the $name goal: ${e.userMessage()}")
        } catch (e: Exception) {
            onError("Couldn't refresh the $name goal: ${e.userMessage()}")
        }
    }

    fun save(scope: CoroutineScope, upsert: suspend () -> T) = run(scope) {
        try {
            projection.value = upsert()
            loaded.value = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError("Couldn't save the $name goal: ${e.userMessage()}")
        }
    }

    private fun run(scope: CoroutineScope, block: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch { block() }
    }
}

class FinancesViewModel(private val app: FinancesApp) : ViewModel() {
    private val repo get() = app.repository

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts

    /** True while accounts or the profile are being shown from the offline cache. */
    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline

    private val _netWorth = MutableStateFlow<NetWorthSummary?>(null)
    val netWorth: StateFlow<NetWorthSummary?> = _netWorth

    private val _netWorthHistory = MutableStateFlow<List<NetWorthPoint>>(emptyList())
    val netWorthHistory: StateFlow<List<NetWorthPoint>> = _netWorthHistory

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val mortgage = GoalSlot("mortgage", { repo.getMortgageGoal() }) { _error.value = it }
    private val pension = GoalSlot("pension", { repo.getPensionGoal() }) { _error.value = it }
    private val isa = GoalSlot("ISA bridge", { repo.getIsaBridgeGoal() }) { _error.value = it }

    val mortgageProjection: StateFlow<MortgageProjection?> = mortgage.projection
    val mortgageLoaded: StateFlow<Boolean> = mortgage.loaded
    val pensionProjection: StateFlow<PensionProjection?> = pension.projection
    val pensionLoaded: StateFlow<Boolean> = pension.loaded
    val isaProjection: StateFlow<IsaBridgeProjection?> = isa.projection
    val isaLoaded: StateFlow<Boolean> = isa.loaded

    private val _trading212Config = MutableStateFlow<Trading212Config?>(null)
    val trading212Config: StateFlow<Trading212Config?> = _trading212Config

    private val _accountHistory = MutableStateFlow<List<AccountHistoryEntry>>(emptyList())
    val accountHistory: StateFlow<List<AccountHistoryEntry>> = _accountHistory

    private val _currentAccountId = MutableStateFlow<Int?>(null)

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    init {
        loadAll()
    }

    fun loadAll() {
        loadProfile()
        loadAccounts()
        loadGoals()
        loadTrading212Config()
    }

    fun loadTrading212Config() = viewModelScope.launch {
        runCatching { repo.getTrading212Config() }.onSuccess { _trading212Config.value = it }
    }

    fun loadProfile() = viewModelScope.launch {
        runCatching { repo.getProfile() }.onSuccess {
            _profile.value = it.value
            if (it.fromCache) _offline.value = true
        }
    }

    fun saveProfile(profile: UserProfile) = viewModelScope.launch {
        runCatching { repo.upsertProfile(profile) }
            .onSuccess { _profile.value = it; loadGoals() }
            .onFailure { _error.value = "Couldn't save the profile: ${it.userMessage()}" }
    }

    fun loadAccounts() = viewModelScope.launch {
        runCatching { repo.getAccounts() }
            .onSuccess {
                _accounts.value = it.value
                _offline.value = it.fromCache
                loadNetWorth()
            }
            .onFailure { _error.value = it.userMessage() }
    }

    fun createAccount(req: CreateAccountRequest) = viewModelScope.launch {
        runCatching { repo.createAccount(req) }
            .onSuccess { loadAccounts(); loadGoals() }
            .onFailure { _error.value = "Couldn't add the account: ${it.userMessage()}" }
    }

    fun updateBalance(id: Int, balance: Double) = viewModelScope.launch {
        runCatching { repo.updateAccountBalance(id, balance) }
            .onSuccess { loadAccounts(); loadGoals(); reloadHistoryIfShowing(id) }
            .onFailure { _error.value = "Couldn't update the balance: ${it.userMessage()}" }
    }

    fun archiveAccount(id: Int) = viewModelScope.launch {
        runCatching { repo.archiveAccount(id) }
            .onSuccess { loadAccounts(); loadGoals() }
            .onFailure { _error.value = "Couldn't archive the account: ${it.userMessage()}" }
    }

    fun loadNetWorth() = viewModelScope.launch {
        val accts = _accounts.value
        val assets = accts.filter { it.type == "asset" }.sumOf { it.balance }
        val liabilities = accts.filter { it.type == "liability" }.sumOf { it.balance }
        _netWorth.value = NetWorthSummary(assets, liabilities, assets - liabilities)
        runCatching { repo.getNetWorthHistory() }
            .onSuccess { _netWorthHistory.value = it }
            .onFailure { if (!_offline.value) _error.value = "Couldn't load net worth history: ${it.userMessage()}" }
    }

    fun loadGoals() {
        mortgage.load(viewModelScope)
        pension.load(viewModelScope)
        isa.load(viewModelScope)
    }

    fun loadMortgageGoal() = mortgage.load(viewModelScope)
    fun loadPensionGoal() = pension.load(viewModelScope)
    fun loadIsaBridgeGoal() = isa.load(viewModelScope)

    fun saveMortgageGoal(goal: MortgageGoal) = mortgage.save(viewModelScope) { repo.upsertMortgageGoal(goal) }
    fun savePensionGoal(goal: PensionGoal) = pension.save(viewModelScope) { repo.upsertPensionGoal(goal) }
    fun saveIsaBridgeGoal(goal: IsaBridgeGoal) = isa.save(viewModelScope) { repo.upsertIsaBridgeGoal(goal) }

    fun saveTrading212Config(key: String) = viewModelScope.launch {
        runCatching { repo.saveTrading212Config(key, _trading212Config.value?.accountId) }
            .onSuccess { loadTrading212Config() }
            .onFailure { _error.value = it.userMessage() }
    }

    fun linkTrading212Account(accountId: Int?) = viewModelScope.launch {
        val currentKey = _trading212Config.value?.apiKey ?: return@launch
        runCatching { repo.saveTrading212Config(currentKey, accountId) }
            .onSuccess { loadTrading212Config() }
            .onFailure { _error.value = it.userMessage() }
    }

    fun loadAccountHistory(id: Int) = viewModelScope.launch {
        // Clear first so another account's entries are never shown under this one.
        if (_currentAccountId.value != id) _accountHistory.value = emptyList()
        _currentAccountId.value = id
        runCatching { repo.getAccountHistory(id) }
            .onSuccess { if (_currentAccountId.value == id) _accountHistory.value = it }
            .onFailure { _error.value = "Couldn't load the history: ${it.userMessage()}" }
    }

    private fun reloadHistoryIfShowing(id: Int) {
        if (_currentAccountId.value == id) loadAccountHistory(id)
    }

    fun insertHistoricalBalance(id: Int, balance: Double, recordedAt: String) = viewModelScope.launch {
        runCatching { repo.insertHistoricalBalance(id, balance, recordedAt) }
            .onSuccess { loadNetWorth(); reloadHistoryIfShowing(id) }
            .onFailure { _error.value = "Couldn't add the value: ${it.userMessage()}" }
    }

    fun syncTrading212() = viewModelScope.launch {
        _syncing.value = true
        runCatching { repo.syncTrading212() }
            .onSuccess {
                loadAccounts(); loadGoals()
                _trading212Config.value?.accountId?.let { reloadHistoryIfShowing(it) }
            }
            .onFailure { _error.value = "Trading 212 sync failed: ${it.userMessage()}" }
        _syncing.value = false
    }

    val serverUrl: String get() = app.getServerUrl()

    fun updateServerUrl(url: String) {
        app.initRepository(url)
        loadAll()
    }

    fun clearError() { _error.value = null }
}

class FinancesViewModelFactory(private val app: FinancesApp) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return FinancesViewModel(app) as T
    }
}
