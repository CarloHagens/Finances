package com.finances.app.data

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface ApiService {
    @GET("api/profile")
    suspend fun getProfile(): UserProfile

    @PUT("api/profile")
    suspend fun upsertProfile(@Body profile: UserProfile): UserProfile

    @GET("api/accounts")
    suspend fun getAccounts(): List<Account>

    @POST("api/accounts")
    suspend fun createAccount(@Body req: CreateAccountRequest): Account

    @PATCH("api/accounts/{id}/balance")
    suspend fun updateAccountBalance(@Path("id") id: Int, @Body req: UpdateBalanceRequest): Account

    @DELETE("api/accounts/{id}")
    suspend fun deleteAccount(@Path("id") id: Int)

    @GET("api/accounts/{id}/history")
    suspend fun getAccountHistory(@Path("id") id: Int): List<AccountHistoryEntry>

    @POST("api/accounts/{id}/history")
    suspend fun insertHistoricalBalance(@Path("id") id: Int, @Body req: HistoricalBalanceRequest)

    @GET("api/net-worth")
    suspend fun getNetWorth(): NetWorthSummary

    @GET("api/net-worth/history")
    suspend fun getNetWorthHistory(): List<NetWorthPoint>

    @GET("api/goals/mortgage")
    suspend fun getMortgageGoal(): MortgageProjection

    @PUT("api/goals/mortgage")
    suspend fun upsertMortgageGoal(@Body goal: MortgageGoal): MortgageProjection

    @GET("api/goals/pension")
    suspend fun getPensionGoal(): PensionProjection

    @PUT("api/goals/pension")
    suspend fun upsertPensionGoal(@Body goal: PensionGoal): PensionProjection

    @GET("api/goals/isa-bridge")
    suspend fun getIsaBridgeGoal(): IsaBridgeProjection

    @PUT("api/goals/isa-bridge")
    suspend fun upsertIsaBridgeGoal(@Body goal: IsaBridgeGoal): IsaBridgeProjection

    @POST("api/sync/trading212")
    suspend fun syncTrading212(): Map<String, Any>

    @GET("api/config/trading212")
    suspend fun getTrading212Config(): Trading212Config

    @PUT("api/config/trading212")
    suspend fun upsertTrading212Config(@Body req: UpdateApiKeyRequest)
}
