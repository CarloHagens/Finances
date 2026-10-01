package com.finances.app.data

import android.content.Context
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** A value plus whether it came from the offline cache rather than the server. */
data class Fetched<T>(val value: T, val fromCache: Boolean)

class Repository(context: Context, baseUrl: String) {
    private val db = AppDatabase.get(context)
    private val api = buildApi(baseUrl)

    private fun buildApi(baseUrl: String): ApiService {
        val gson = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
        val client = OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApiService::class.java)
    }

    // Profile
    suspend fun getProfile(): Fetched<UserProfile?> {
        val remote = runCatching { api.getProfile() }
        remote.getOrNull()?.let {
            db.userProfileDao().upsert(it)
            return Fetched(it, fromCache = false)
        }
        // A 404 means no profile yet — not an offline condition.
        val notFound = (remote.exceptionOrNull() as? retrofit2.HttpException)?.code() == 404
        return Fetched(if (notFound) null else db.userProfileDao().get(), fromCache = !notFound)
    }

    suspend fun upsertProfile(profile: UserProfile): UserProfile {
        val result = api.upsertProfile(profile)
        db.userProfileDao().upsert(result)
        return result
    }

    // Accounts
    suspend fun getAccounts(): Fetched<List<Account>> {
        val remote = runCatching { api.getAccounts() }.getOrNull()
        if (remote != null) {
            db.accountDao().replaceAll(remote)
            return Fetched(remote, fromCache = false)
        }
        return Fetched(db.accountDao().getAll(), fromCache = true)
    }

    suspend fun createAccount(req: CreateAccountRequest): Account {
        val result = api.createAccount(req)
        db.accountDao().upsert(result)
        return result
    }

    suspend fun updateAccountBalance(id: Int, balance: Double): Account {
        val result = api.updateAccountBalance(id, UpdateBalanceRequest(balance))
        db.accountDao().upsert(result)
        return result
    }

    /** Archives the account on the server (its history is kept) and drops it from the cache. */
    suspend fun archiveAccount(id: Int) {
        api.deleteAccount(id)
        db.accountDao().delete(id)
    }

    suspend fun getAccountHistory(id: Int): List<AccountHistoryEntry> = api.getAccountHistory(id)

    suspend fun insertHistoricalBalance(id: Int, balance: Double, recordedAt: String) =
        api.insertHistoricalBalance(id, HistoricalBalanceRequest(balance, recordedAt))

    // Net Worth
    suspend fun getNetWorthHistory(): List<NetWorthPoint> = api.getNetWorthHistory()

    // Goals
    suspend fun getMortgageGoal(): MortgageProjection = api.getMortgageGoal()
    suspend fun upsertMortgageGoal(goal: MortgageGoal): MortgageProjection = api.upsertMortgageGoal(goal)

    suspend fun getPensionGoal(): PensionProjection = api.getPensionGoal()
    suspend fun upsertPensionGoal(goal: PensionGoal): PensionProjection = api.upsertPensionGoal(goal)

    suspend fun getIsaBridgeGoal(): IsaBridgeProjection = api.getIsaBridgeGoal()
    suspend fun upsertIsaBridgeGoal(goal: IsaBridgeGoal): IsaBridgeProjection = api.upsertIsaBridgeGoal(goal)

    // Trading 212
    suspend fun syncTrading212() = api.syncTrading212()
    suspend fun getTrading212Config(): Trading212Config = api.getTrading212Config()
    suspend fun saveTrading212Config(key: String, accountId: Int?) = api.upsertTrading212Config(UpdateApiKeyRequest(key, accountId))

    companion object {
        const val DEFAULT_URL = "http://10.0.2.2:8080/"
    }
}
