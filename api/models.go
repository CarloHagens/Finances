package main

import "time"

// UserProfile holds the user's dates and the retirement-plan assumptions shared
// by the pension and ISA bridge goals.  Money is monthly, in today's £.
type UserProfile struct {
	ID                         int     `json:"id" db:"id"`
	DateOfBirth                string  `json:"date_of_birth" db:"date_of_birth"`
	RetirementAge              int     `json:"retirement_age" db:"retirement_age"`         // stop work: pension contributions stop, ISA bridge starts
	PensionAccessAge           int     `json:"pension_access_age" db:"pension_access_age"` // pension drawdown starts, ISA bridge ends
	TargetMonthlyIncome        float64 `json:"target_monthly_income" db:"target_monthly_income"`
	SingleTargetMonthlyIncome  float64 `json:"single_target_monthly_income" db:"single_target_monthly_income"` // after the partner's pension stops; 0 = unchanged
	PartnerDateOfBirth         string  `json:"partner_date_of_birth" db:"partner_date_of_birth"`               // "" = unknown
	PartnerStatePensionMonthly float64 `json:"partner_state_pension_monthly" db:"partner_state_pension_monthly"`
	PartnerPensionEndAge       int     `json:"partner_pension_end_age" db:"partner_pension_end_age"` // partner's age; 0 = paid for the whole plan
}

type Account struct {
	ID       int     `json:"id" db:"id"`
	Name     string  `json:"name" db:"name"`
	Type     string  `json:"type" db:"type"`
	Category string  `json:"category" db:"category"`
	Balance  float64 `json:"balance" db:"balance"`
	Currency string  `json:"currency" db:"currency"`
}

type NetWorthSummary struct {
	TotalAssets      float64 `json:"total_assets"`
	TotalLiabilities float64 `json:"total_liabilities"`
	NetWorth         float64 `json:"net_worth"`
}

// NetWorthPoint is one reconstructed data point derived from account_balance_history.
// No separate snapshot table exists — this is computed on the fly.
type NetWorthPoint struct {
	TotalAssets      float64   `json:"total_assets"`
	TotalLiabilities float64   `json:"total_liabilities"`
	NetWorth         float64   `json:"net_worth"`
	RecordedAt       time.Time `json:"recorded_at"`
}

type MortgageGoal struct {
	ID                      int      `json:"id" db:"id"`
	AccountID               *int     `json:"account_id" db:"account_id"`
	PropertyAccountID       *int     `json:"property_account_id" db:"property_account_id"`
	MonthlyPayment          float64  `json:"monthly_payment" db:"monthly_payment"`
	MonthlyOverpayment      float64  `json:"monthly_overpayment" db:"monthly_overpayment"`
	AnnualInterestRate      float64  `json:"annual_interest_rate" db:"annual_interest_rate"`
	FollowOnRate            *float64 `json:"follow_on_rate" db:"follow_on_rate"` // after the fixed term; nil = current rate + 1pt
	TargetLtv               float64  `json:"target_ltv" db:"target_ltv"`
	FixedTermEnd            string   `json:"fixed_term_end" db:"fixed_term_end"`
	TermEnd                 string   `json:"term_end" db:"term_end"` // mortgage end date, for recalculating the payment at remortgage
	OverpaymentAllowancePct float64  `json:"overpayment_allowance_pct" db:"overpayment_allowance_pct"`
}

type MortgagePoint struct {
	Age     float64 `json:"age"`
	Balance float64 `json:"balance"`
}

type MortgageScenario struct {
	Key             string          `json:"key"` // overpay | stop | regular
	Label           string          `json:"label"`
	Months          int             `json:"months"`
	NeverPaysOff    bool            `json:"never_pays_off"`
	PayoffDate      string          `json:"payoff_date"`
	PayoffAge       float64         `json:"payoff_age"`
	TotalInterest   float64         `json:"total_interest"`
	OverpayUntilAge float64         `json:"overpay_until_age"`
	Points          []MortgagePoint `json:"points"`
}

type MortgageProjection struct {
	MortgageGoal
	// Status: ok | not_configured | account_missing | paid_off | invalid_balance | never_pays_off
	Status                 string  `json:"status"`
	ProfileIncomplete      bool    `json:"profile_incomplete"`
	CurrentBalance         float64 `json:"current_balance"`
	EffectiveFollowOnRate  float64 `json:"effective_follow_on_rate"`
	FollowOnRateAssumed    bool    `json:"follow_on_rate_assumed"`
	PaymentRecalculated    bool    `json:"payment_recalculated"`
	NeverPaysOff           bool    `json:"never_pays_off"`
	ProjectedPayoffAt      string  `json:"projected_payoff_at"`
	ProjectedAge           float64 `json:"projected_age"`
	TotalInterest          float64 `json:"total_interest"`
	TotalInterestWithout   float64 `json:"total_interest_without"`
	InterestSaved          float64 `json:"interest_saved"`
	MonthsAheadBehind      int     `json:"months_ahead_behind"`
	OnTrack                bool    `json:"on_track"`
	StopOverpaymentDate    string  `json:"stop_overpayment_date"`
	StopOverpaymentAge     float64 `json:"stop_overpayment_age"`
	StopOverpaymentReached bool    `json:"stop_overpayment_reached"`
	// Penalty-free overpayment limit
	OverpaymentAllowance             float64 `json:"overpayment_allowance"`
	OverpaymentAllowanceExceededYear int     `json:"overpayment_allowance_exceeded_year"`
	// LTV remortgage targeting
	PropertyValue             float64            `json:"property_value"`
	PropertyAccountAmbiguous  bool               `json:"property_account_ambiguous"`
	CurrentLTV                float64            `json:"current_ltv"`
	TargetLtvBalance          float64            `json:"target_ltv_balance"`
	LtvAlreadyBelow           bool               `json:"ltv_already_below"`
	LtvStopOverpaymentDate    string             `json:"ltv_stop_overpayment_date"`
	LtvStopOverpaymentAge     float64            `json:"ltv_stop_overpayment_age"`
	LtvStopOverpaymentReached bool               `json:"ltv_stop_overpayment_reached"`
	Scenarios                 []MortgageScenario `json:"scenarios"`
}

type PensionGoal struct {
	ID                     int     `json:"id" db:"id"`
	AccountID              *int    `json:"account_id" db:"account_id"`
	MonthlyContribution    float64 `json:"monthly_contribution" db:"monthly_contribution"`
	AnnualGrowthRate       float64 `json:"annual_growth_rate" db:"annual_growth_rate"`
	InflationRate          float64 `json:"inflation_rate" db:"inflation_rate"`
	MinContribSalary       float64 `json:"min_contrib_salary" db:"min_contrib_salary"`
	MinContribRate         float64 `json:"min_contrib_rate" db:"min_contrib_rate"`
	GlidepathYears         int     `json:"glidepath_years" db:"glidepath_years"`
	GlidepathRate          float64 `json:"glidepath_rate" db:"glidepath_rate"`
	OwnStatePensionMonthly float64 `json:"own_state_pension_monthly" db:"own_state_pension_monthly"`
	OwnStatePensionAge     int     `json:"own_state_pension_age" db:"own_state_pension_age"`
}

type PensionScheduleRow struct {
	Age             float64 `json:"age"`   // age at the end of the year
	Phase           string  `json:"phase"` // contrib | growth | draw
	Balance         float64 `json:"balance"`
	BalanceMin      float64 `json:"balance_min"`
	BalanceNone     float64 `json:"balance_none"`
	GrossWithdrawal float64 `json:"gross_withdrawal"` // total withdrawn that year, future £
}

type PensionProjection struct {
	PensionGoal
	// Plan inputs taken from the profile, echoed for display.
	StopContributionAge        int     `json:"stop_contribution_age"`
	DrawAge                    int     `json:"draw_age"`
	TargetMonthlyIncome        float64 `json:"target_monthly_income"`
	PartnerStatePensionMonthly float64 `json:"partner_state_pension_monthly"`

	ProfileIncomplete     bool `json:"profile_incomplete"`
	AccountMissing        bool `json:"account_missing"`
	InDrawdown            bool `json:"in_drawdown"`
	PartnerPensionAssumed bool `json:"partner_pension_assumed"`

	CurrentValue        float64 `json:"current_value"`
	InflatedTarget      float64 `json:"inflated_target"`       // pot needed at draw age, future £
	InflatedTargetToday float64 `json:"inflated_target_today"` // the same in today's £
	// Drawdown figures, monthly, today's £.  "Late" is from the own state pension age.
	NetMonthlyDrawdown        float64              `json:"net_monthly_drawdown"`
	GrossMonthlyDrawdown      float64              `json:"gross_monthly_drawdown"`
	TaxMonthlyDrawdown        float64              `json:"tax_monthly_drawdown"`
	NetMonthlyDrawdownLate    float64              `json:"net_monthly_drawdown_late"`
	GrossMonthlyDrawdownLate  float64              `json:"gross_monthly_drawdown_late"`
	TaxMonthlyDrawdownLate    float64              `json:"tax_monthly_drawdown_late"`
	LumpSumCapAge             float64              `json:"lump_sum_cap_age"`
	PotLastsToAge             float64              `json:"pot_lasts_to_age"`
	MinMonthlyContrib         float64              `json:"min_monthly_contrib"`
	CoastFireNumber           float64              `json:"coast_fire_number"`
	CoastFireReached          bool                 `json:"coast_fire_reached"`
	CoastFireDate             string               `json:"coast_fire_date"`
	CoastFireAge              float64              `json:"coast_fire_age"`
	MinCoastFireNumber        float64              `json:"min_coast_fire_number"`
	MinCoastFireReached       bool                 `json:"min_coast_fire_reached"`
	MinCoastFireDate          string               `json:"min_coast_fire_date"`
	MinCoastFireAge           float64              `json:"min_coast_fire_age"`
	ProjectedAtDraw           float64              `json:"projected_at_draw"` // future £
	ProjectedAtDrawToday      float64              `json:"projected_at_draw_today"`
	ProjectedAtDrawMinContrib float64              `json:"projected_at_draw_min_contrib"`
	ProjectedAtDrawNoContrib  float64              `json:"projected_at_draw_no_contrib"`
	Surplus                   float64              `json:"surplus"`
	OnTrack                   bool                 `json:"on_track"`
	Schedule                  []PensionScheduleRow `json:"schedule"`
}

type IsaBridgeGoal struct {
	ID                  int     `json:"id" db:"id"`
	AccountID           *int    `json:"account_id" db:"account_id"`
	MonthlyContribution float64 `json:"monthly_contribution" db:"monthly_contribution"`
	AnnualGrowthRate    float64 `json:"annual_growth_rate" db:"annual_growth_rate"`
	InflationRate       float64 `json:"inflation_rate" db:"inflation_rate"`
	GlidepathYears      int     `json:"glidepath_years" db:"glidepath_years"`
	GlidepathRate       float64 `json:"glidepath_rate" db:"glidepath_rate"`
}

type IsaScheduleRow struct {
	Age     float64 `json:"age"`
	Balance float64 `json:"balance"`
}

type IsaBridgeProjection struct {
	IsaBridgeGoal
	// Plan inputs taken from the profile, echoed for display.
	BridgeStartAge             int     `json:"bridge_start_age"`
	BridgeEndAge               int     `json:"bridge_end_age"`
	TargetMonthlyIncome        float64 `json:"target_monthly_income"`
	PartnerStatePensionMonthly float64 `json:"partner_state_pension_monthly"`

	ProfileIncomplete     bool `json:"profile_incomplete"`
	AccountMissing        bool `json:"account_missing"`
	InBridge              bool `json:"in_bridge"`
	PartnerPensionAssumed bool `json:"partner_pension_assumed"`

	CurrentValue                float64 `json:"current_value"`
	InflatedTargetMonthlyIncome float64 `json:"inflated_target_monthly_income"` // at bridge start, future £
	InflatedPartnerStatePension float64 `json:"inflated_partner_state_pension"`
	InflatedMonthlyWithdrawal   float64 `json:"inflated_monthly_withdrawal"` // first bridge month; rises with inflation after
	ProjectedAtRetirement       float64 `json:"projected_at_retirement"`
	ProjectedAtRetirementToday  float64 `json:"projected_at_retirement_today"`
	RequiredAtRetirement        float64 `json:"required_at_retirement"`
	RequiredAtRetirementToday   float64 `json:"required_at_retirement_today"`
	RequiredMonthlyContribution float64 `json:"required_monthly_contribution"`
	RequiredLumpSum             float64 `json:"required_lump_sum"`
	RequiredLumpSumNoContrib    float64 `json:"required_lump_sum_no_contrib"`
	SurvivesToBridgeEnd         bool    `json:"survives_to_bridge_end"`
	RunsOutAge                  float64 `json:"runs_out_age"`
	MonthlyShortfall            float64 `json:"monthly_shortfall"`
	OnTrack                     bool    `json:"on_track"`
	// ISA allowance (£20,000 a year) warnings
	ContributionExceedsAllowance         bool             `json:"contribution_exceeds_allowance"`
	RequiredContributionExceedsAllowance bool             `json:"required_contribution_exceeds_allowance"`
	LumpSumExceedsAllowance              bool             `json:"lump_sum_exceeds_allowance"`
	Schedule                             []IsaScheduleRow `json:"schedule"`
}

type Trading212Config struct {
	APIKey       string     `json:"api_key" db:"api_key"`
	AccountID    *int       `json:"account_id" db:"account_id"`
	LastSyncedAt *time.Time `json:"last_synced_at" db:"last_synced_at"`
}

type UpdateBalanceRequest struct {
	Balance float64 `json:"balance"`
}

type UpdateAPIKeyRequest struct {
	APIKey    string `json:"api_key"`
	AccountID *int   `json:"account_id"`
}

type HistoricalBalanceRequest struct {
	Balance    float64 `json:"balance"`
	RecordedAt string  `json:"recorded_at"`
}

type AccountHistoryEntry struct {
	ID         int       `json:"id"`
	AccountID  int       `json:"account_id"`
	Balance    float64   `json:"balance"`
	RecordedAt time.Time `json:"recorded_at"`
}
