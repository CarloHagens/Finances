package com.finances.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The user's dates plus the retirement-plan assumptions shared by the pension and
 * ISA bridge goals.  Money is monthly, in today's £.
 */
@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val dateOfBirth: String = "",
    val retirementAge: Int = 53,              // stop work: pension contributions stop, ISA bridge starts
    val pensionAccessAge: Int = 58,           // pension drawdown starts, ISA bridge ends
    val targetMonthlyIncome: Double = 0.0,
    val singleTargetMonthlyIncome: Double = 0.0, // after the partner's pension stops; 0 = unchanged
    val partnerDateOfBirth: String = "",      // "" = unknown
    val partnerStatePensionMonthly: Double = 0.0,
    val partnerPensionEndAge: Int = 0,        // partner's age; 0 = paid for the whole plan
    val partnerStatePensionAge: Int = 0,      // override; 0 = from their date of birth
    val partnerStatePensionAgeFromDob: Double = 0.0, // read-only: legislated age for their date of birth
    val inflationRate: Double = 0.03
)

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey val id: Int = 0,
    val name: String = "",
    val type: String = "",
    val category: String = "",
    val balance: Double = 0.0,
    val currency: String = "GBP"
)

data class NetWorthSummary(
    val totalAssets: Double = 0.0,
    val totalLiabilities: Double = 0.0,
    val netWorth: Double = 0.0
)

// NetWorthPoint is one reconstructed data point derived from account_balance_history.
data class NetWorthPoint(
    val totalAssets: Double = 0.0,
    val totalLiabilities: Double = 0.0,
    val netWorth: Double = 0.0,
    val recordedAt: String = ""
)

data class MortgageGoal(
    val id: Int = 1,
    val accountId: Int? = null,
    val propertyAccountId: Int? = null,
    val monthlyPayment: Double = 0.0,
    val monthlyOverpayment: Double = 0.0,
    val annualInterestRate: Double = 0.0,
    val followOnRate: Double? = null,        // null = current rate + 1pt
    val targetLtv: Double = 0.60,
    val fixedTermEnd: String = "",
    val termEnd: String = "",
    val overpaymentAllowancePct: Double = 0.10
)

data class MortgagePoint(val age: Double = 0.0, val balance: Double = 0.0)

data class MortgageScenario(
    val key: String = "",                    // overpay | stop | regular
    val label: String = "",
    val months: Int = 0,
    val neverPaysOff: Boolean = false,
    val payoffDate: String = "",
    val payoffAge: Double = 0.0,
    val totalInterest: Double = 0.0,
    val overpayUntilAge: Double = 0.0,
    val points: List<MortgagePoint> = emptyList()
)

data class MortgageProjection(
    val id: Int = 1,
    val accountId: Int? = null,
    val propertyAccountId: Int? = null,
    val monthlyPayment: Double = 0.0,
    val monthlyOverpayment: Double = 0.0,
    val annualInterestRate: Double = 0.0,
    val followOnRate: Double? = null,
    val targetLtv: Double = 0.60,
    val fixedTermEnd: String = "",
    val termEnd: String = "",
    val overpaymentAllowancePct: Double = 0.10,
    // ok | not_configured | account_missing | paid_off | invalid_balance | never_pays_off
    val status: String = "",
    val profileIncomplete: Boolean = false,
    val currentBalance: Double = 0.0,
    val effectiveFollowOnRate: Double = 0.0,
    val followOnRateAssumed: Boolean = false,
    val paymentRecalculated: Boolean = false,
    val neverPaysOff: Boolean = false,
    val projectedPayoffAt: String = "",
    val projectedAge: Double = 0.0,
    val totalInterest: Double = 0.0,
    val totalInterestWithout: Double = 0.0,
    val interestSaved: Double = 0.0,
    val monthsAheadBehind: Int = 0,
    val onTrack: Boolean = false,
    val stopOverpaymentDate: String = "",
    val stopOverpaymentAge: Double = 0.0,
    val stopOverpaymentReached: Boolean = false,
    val overpaymentAllowance: Double = 0.0,
    val overpaymentAllowanceExceededYear: Int = 0,
    val propertyValue: Double = 0.0,
    val propertyAccountAmbiguous: Boolean = false,
    val currentLtv: Double = 0.0,
    val targetLtvBalance: Double = 0.0,
    val ltvAlreadyBelow: Boolean = false,
    val ltvStopOverpaymentDate: String = "",
    val ltvStopOverpaymentAge: Double = 0.0,
    val ltvStopOverpaymentReached: Boolean = false,
    val scenarios: List<MortgageScenario> = emptyList()
)

data class PensionGoal(
    val id: Int = 1,
    val accountId: Int? = null,
    val monthlyContribution: Double = 0.0,
    val annualGrowthRate: Double = 0.07,
    val minContribSalary: Double = 0.0,
    val minContribRate: Double = 0.08,
    val glidepathYears: Int = 5,
    val glidepathRate: Double = 0.04,
    val ownStatePensionMonthly: Double = 0.0,
    val ownStatePensionAge: Int = 68
)

data class PensionScheduleRow(
    val age: Double = 0.0,                   // age at the end of the year
    val phase: String = "",                  // contrib | growth | draw
    val balance: Double = 0.0,
    val balanceMin: Double = 0.0,
    val balanceNone: Double = 0.0,
    val grossWithdrawal: Double = 0.0
)

data class PensionProjection(
    val id: Int = 1,
    val accountId: Int? = null,
    val monthlyContribution: Double = 0.0,
    val annualGrowthRate: Double = 0.07,
    val inflationRate: Double = 0.03,
    val minContribSalary: Double = 0.0,
    val minContribRate: Double = 0.08,
    val glidepathYears: Int = 5,
    val glidepathRate: Double = 0.04,
    val ownStatePensionMonthly: Double = 0.0,
    val ownStatePensionAge: Int = 68,
    // Plan inputs from the profile, echoed for display.
    val stopContributionAge: Int = 0,
    val drawAge: Int = 0,
    val targetMonthlyIncome: Double = 0.0,
    val partnerStatePensionMonthly: Double = 0.0,
    val profileIncomplete: Boolean = false,
    val accountMissing: Boolean = false,
    val inDrawdown: Boolean = false,
    val partnerPensionAssumed: Boolean = false,
    val currentValue: Double = 0.0,
    val inflatedTarget: Double = 0.0,        // pot needed at draw age, future £
    val inflatedTargetToday: Double = 0.0,
    // Drawdown figures, monthly, in today's £.
    val netMonthlyDrawdown: Double = 0.0,
    val grossMonthlyDrawdown: Double = 0.0,
    val taxMonthlyDrawdown: Double = 0.0,
    val netMonthlyDrawdownLate: Double = 0.0,
    val grossMonthlyDrawdownLate: Double = 0.0,
    val taxMonthlyDrawdownLate: Double = 0.0,
    val lumpSumCapAge: Double = 0.0,
    val potLastsToAge: Double = 0.0,
    val minMonthlyContrib: Double = 0.0,
    val coastFireNumber: Double = 0.0,
    val coastFireReached: Boolean = false,
    val coastFireDate: String = "",
    val coastFireAge: Double = 0.0,
    val minCoastFireNumber: Double = 0.0,
    val minCoastFireReached: Boolean = false,
    val minCoastFireDate: String = "",
    val minCoastFireAge: Double = 0.0,
    val projectedAtDraw: Double = 0.0,       // future £
    val projectedAtDrawToday: Double = 0.0,
    val projectedAtDrawMinContrib: Double = 0.0,
    val projectedAtDrawNoContrib: Double = 0.0,
    val surplus: Double = 0.0,
    val onTrack: Boolean = false,
    val schedule: List<PensionScheduleRow> = emptyList()
)

data class IsaBridgeGoal(
    val id: Int = 1,
    val accountId: Int? = null,
    val monthlyContribution: Double = 0.0,
    val annualGrowthRate: Double = 0.07,
    val glidepathYears: Int = 5,
    val glidepathRate: Double = 0.04
)

data class IsaScheduleRow(val age: Double = 0.0, val balance: Double = 0.0)

data class IsaBridgeProjection(
    val id: Int = 1,
    val accountId: Int? = null,
    val monthlyContribution: Double = 0.0,
    val annualGrowthRate: Double = 0.07,
    val inflationRate: Double = 0.03,
    val glidepathYears: Int = 5,
    val glidepathRate: Double = 0.04,
    // Plan inputs from the profile, echoed for display.
    val bridgeStartAge: Int = 0,
    val bridgeEndAge: Int = 0,
    val targetMonthlyIncome: Double = 0.0,
    val partnerStatePensionMonthly: Double = 0.0,
    val profileIncomplete: Boolean = false,
    val accountMissing: Boolean = false,
    val inBridge: Boolean = false,
    val partnerPensionAssumed: Boolean = false,
    val currentValue: Double = 0.0,
    val inflatedTargetMonthlyIncome: Double = 0.0,
    val inflatedPartnerStatePension: Double = 0.0,
    val inflatedMonthlyWithdrawal: Double = 0.0, // first bridge month; rises with inflation after
    val projectedAtRetirement: Double = 0.0,
    val projectedAtRetirementToday: Double = 0.0,
    val requiredAtRetirement: Double = 0.0,
    val requiredAtRetirementToday: Double = 0.0,
    val requiredMonthlyContribution: Double = 0.0,
    val requiredLumpSum: Double = 0.0,
    val requiredLumpSumNoContrib: Double = 0.0,
    val survivesToBridgeEnd: Boolean = false,
    val runsOutAge: Double = 0.0,
    val monthlyShortfall: Double = 0.0,
    val onTrack: Boolean = false,
    val contributionExceedsAllowance: Boolean = false,
    val requiredContributionExceedsAllowance: Boolean = false,
    val lumpSumExceedsAllowance: Boolean = false,
    val schedule: List<IsaScheduleRow> = emptyList()
)

data class Trading212Config(
    val apiKey: String = "",
    val accountId: Int? = null,
    val lastSyncedAt: String? = null
)

data class UpdateBalanceRequest(val balance: Double)
data class UpdateApiKeyRequest(val apiKey: String, val accountId: Int? = null)
data class HistoricalBalanceRequest(val balance: Double, val recordedAt: String)
data class AccountHistoryEntry(
    val id: Int = 0,
    val accountId: Int = 0,
    val balance: Double = 0.0,
    val recordedAt: String = ""
)
data class CreateAccountRequest(
    val name: String,
    val type: String,
    val category: String,
    val balance: Double,
    val currency: String = "GBP"
)
