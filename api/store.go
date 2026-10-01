package main

import (
	"context"
	"errors"
	"math"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type Store struct {
	db *pgxpool.Pool
}

func NewStore(db *pgxpool.Pool) *Store {
	return &Store{db: db}
}

var (
	errNegativeLiability = errors.New("a liability balance must not be negative — enter the amount owed as a positive number")
	errAccountArchived   = errors.New("account has been archived")
)

// UserProfile

func (s *Store) GetProfile(ctx context.Context) (*UserProfile, error) {
	p := &UserProfile{}
	err := s.db.QueryRow(ctx, `
		SELECT id, date_of_birth::text, retirement_age, pension_access_age,
		       target_monthly_income, single_target_monthly_income,
		       COALESCE(partner_date_of_birth::text, ''), partner_state_pension_monthly, partner_pension_end_age,
		       partner_state_pension_age, inflation_rate
		FROM user_profile WHERE id = 1`,
	).Scan(&p.ID, &p.DateOfBirth, &p.RetirementAge, &p.PensionAccessAge,
		&p.TargetMonthlyIncome, &p.SingleTargetMonthlyIncome,
		&p.PartnerDateOfBirth, &p.PartnerStatePensionMonthly, &p.PartnerPensionEndAge,
		&p.PartnerStatePensionAge, &p.InflationRate)
	if err != nil {
		return nil, err
	}
	if pdob, err := time.Parse("2006-01-02", p.PartnerDateOfBirth); err == nil {
		p.PartnerStatePensionAgeFromDOB = round2(statePensionAge(pdob))
	}
	return p, nil
}

func (s *Store) UpsertProfile(ctx context.Context, p UserProfile) (*UserProfile, error) {
	var partnerDOB interface{}
	if p.PartnerDateOfBirth != "" {
		partnerDOB = p.PartnerDateOfBirth
	}
	_, err := s.db.Exec(ctx, `
		INSERT INTO user_profile (id, date_of_birth, retirement_age, pension_access_age,
			target_monthly_income, single_target_monthly_income,
			partner_date_of_birth, partner_state_pension_monthly, partner_pension_end_age,
			partner_state_pension_age, inflation_rate)
		VALUES (1, $1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
		ON CONFLICT (id) DO UPDATE SET
			date_of_birth = EXCLUDED.date_of_birth,
			retirement_age = EXCLUDED.retirement_age,
			pension_access_age = EXCLUDED.pension_access_age,
			target_monthly_income = EXCLUDED.target_monthly_income,
			single_target_monthly_income = EXCLUDED.single_target_monthly_income,
			partner_date_of_birth = EXCLUDED.partner_date_of_birth,
			partner_state_pension_monthly = EXCLUDED.partner_state_pension_monthly,
			partner_pension_end_age = EXCLUDED.partner_pension_end_age,
			partner_state_pension_age = EXCLUDED.partner_state_pension_age,
			inflation_rate = EXCLUDED.inflation_rate`,
		p.DateOfBirth, p.RetirementAge, p.PensionAccessAge,
		p.TargetMonthlyIncome, p.SingleTargetMonthlyIncome,
		partnerDOB, p.PartnerStatePensionMonthly, p.PartnerPensionEndAge,
		p.PartnerStatePensionAge, p.InflationRate,
	)
	if err != nil {
		return nil, err
	}
	return s.GetProfile(ctx)
}

// planContext is what every projection needs from the profile.
type planContext struct {
	profile    *UserProfile
	currentAge float64
	hh         household
}

// loadPlan reads the profile; ok is false when it is missing or has no valid
// date of birth, in which case projections report profile_incomplete.
func (s *Store) loadPlan(ctx context.Context, now time.Time) (plan planContext, ok bool) {
	profile, err := s.GetProfile(ctx)
	if err != nil {
		return plan, false
	}
	plan.profile = profile
	dob, err := time.Parse("2006-01-02", profile.DateOfBirth)
	if err != nil {
		return plan, false
	}
	plan.currentAge = yearsBetween(dob, now)
	plan.hh = household{
		targetMonthly:         profile.TargetMonthlyIncome,
		singleTargetMonthly:   profile.SingleTargetMonthlyIncome,
		partnerPensionMonthly: profile.PartnerStatePensionMonthly,
		partnerAgeNow:         -1,
		partnerPensionEndAge:  profile.PartnerPensionEndAge,
	}
	if pdob, err := time.Parse("2006-01-02", profile.PartnerDateOfBirth); err == nil {
		plan.hh.partnerAgeNow = yearsBetween(pdob, now)
		plan.hh.partnerSPAge = partnerStatePensionAge(pdob, profile.PartnerStatePensionAge)
	}
	return plan, true
}

// accountBalance returns the balance of a live (non-archived) account; ok is
// false when it doesn't exist or has been archived.
func (s *Store) accountBalance(ctx context.Context, id *int) (balance float64, ok bool) {
	if id == nil {
		return 0, false
	}
	err := s.db.QueryRow(ctx, `SELECT balance FROM accounts WHERE id = $1 AND archived_at IS NULL`, *id).Scan(&balance)
	return balance, err == nil
}

// Accounts

func (s *Store) ListAccounts(ctx context.Context) ([]Account, error) {
	rows, err := s.db.Query(ctx, `SELECT id, name, type, category, balance, currency FROM accounts WHERE archived_at IS NULL ORDER BY type, name`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var accounts []Account
	for rows.Next() {
		var a Account
		if err := rows.Scan(&a.ID, &a.Name, &a.Type, &a.Category, &a.Balance, &a.Currency); err != nil {
			return nil, err
		}
		accounts = append(accounts, a)
	}
	if accounts == nil {
		accounts = []Account{}
	}
	return accounts, rows.Err()
}

func (s *Store) CreateAccount(ctx context.Context, a Account) (*Account, error) {
	if a.Type == "liability" && a.Balance < 0 {
		return nil, errNegativeLiability
	}
	err := pgx.BeginFunc(ctx, s.db, func(tx pgx.Tx) error {
		if err := tx.QueryRow(ctx,
			`INSERT INTO accounts (name, type, category, balance, currency) VALUES ($1, $2, $3, $4, $5) RETURNING id`,
			a.Name, a.Type, a.Category, a.Balance, a.Currency,
		).Scan(&a.ID); err != nil {
			return err
		}
		_, err := tx.Exec(ctx, `INSERT INTO account_balance_history (account_id, balance) VALUES ($1, $2)`, a.ID, a.Balance)
		return err
	})
	if err != nil {
		return nil, err
	}
	return &a, nil
}

// setBalance updates a live account's balance and records it in the history,
// in one transaction.  Liabilities are owed amounts and must not be negative.
func setBalance(ctx context.Context, tx pgx.Tx, id int, balance float64) (*Account, error) {
	var a Account
	var archived *time.Time
	err := tx.QueryRow(ctx,
		`SELECT id, name, type, category, currency, archived_at FROM accounts WHERE id = $1 FOR UPDATE`, id,
	).Scan(&a.ID, &a.Name, &a.Type, &a.Category, &a.Currency, &archived)
	if err != nil {
		return nil, err
	}
	if archived != nil {
		return nil, errAccountArchived
	}
	if a.Type == "liability" && balance < 0 {
		return nil, errNegativeLiability
	}
	if _, err := tx.Exec(ctx, `UPDATE accounts SET balance = $1 WHERE id = $2`, balance, id); err != nil {
		return nil, err
	}
	if _, err := tx.Exec(ctx, `INSERT INTO account_balance_history (account_id, balance) VALUES ($1, $2)`, id, balance); err != nil {
		return nil, err
	}
	a.Balance = balance
	return &a, nil
}

func (s *Store) UpdateAccountBalance(ctx context.Context, id int, balance float64) (*Account, error) {
	var result *Account
	err := pgx.BeginFunc(ctx, s.db, func(tx pgx.Tx) error {
		var err error
		result, err = setBalance(ctx, tx, id, balance)
		return err
	})
	return result, err
}

// ArchiveAccount hides an account from current balances and goals but keeps
// its history, so past net worth is unchanged.  A zero balance is recorded at
// the archive date so it stops counting from then on.
func (s *Store) ArchiveAccount(ctx context.Context, id int) error {
	return pgx.BeginFunc(ctx, s.db, func(tx pgx.Tx) error {
		tag, err := tx.Exec(ctx, `UPDATE accounts SET archived_at = NOW(), balance = 0 WHERE id = $1 AND archived_at IS NULL`, id)
		if err != nil {
			return err
		}
		if tag.RowsAffected() == 0 {
			return pgx.ErrNoRows
		}
		if _, err := tx.Exec(ctx, `INSERT INTO account_balance_history (account_id, balance) VALUES ($1, 0)`, id); err != nil {
			return err
		}
		_, err = tx.Exec(ctx, `UPDATE trading212_config SET account_id = NULL WHERE account_id = $1`, id)
		return err
	})
}

func (s *Store) GetAccountHistory(ctx context.Context, id int) ([]AccountHistoryEntry, error) {
	rows, err := s.db.Query(ctx,
		`SELECT id, account_id, balance, recorded_at FROM account_balance_history WHERE account_id = $1 ORDER BY recorded_at DESC`,
		id)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var entries []AccountHistoryEntry
	for rows.Next() {
		var e AccountHistoryEntry
		if err := rows.Scan(&e.ID, &e.AccountID, &e.Balance, &e.RecordedAt); err != nil {
			return nil, err
		}
		entries = append(entries, e)
	}
	if entries == nil {
		entries = []AccountHistoryEntry{}
	}
	return entries, rows.Err()
}

func (s *Store) InsertHistoricalBalance(ctx context.Context, id int, req HistoricalBalanceRequest) error {
	var accountType string
	if err := s.db.QueryRow(ctx, `SELECT type FROM accounts WHERE id = $1`, id).Scan(&accountType); err != nil {
		return err
	}
	if accountType == "liability" && req.Balance < 0 {
		return errNegativeLiability
	}
	if req.RecordedAt != "" {
		t, err := time.Parse("2006-01-02", req.RecordedAt)
		if err != nil {
			t, err = time.Parse(time.RFC3339, req.RecordedAt)
		}
		if err != nil {
			return err
		}
		_, err = s.db.Exec(ctx, `INSERT INTO account_balance_history (account_id, balance, recorded_at) VALUES ($1, $2, $3)`, id, req.Balance, t)
		return err
	}
	_, err := s.db.Exec(ctx, `INSERT INTO account_balance_history (account_id, balance) VALUES ($1, $2)`, id, req.Balance)
	return err
}

// Net Worth

func (s *Store) GetNetWorthSummary(ctx context.Context) (*NetWorthSummary, error) {
	var summary NetWorthSummary
	err := s.db.QueryRow(ctx, `
		SELECT
			COALESCE(SUM(CASE WHEN type = 'asset' THEN balance ELSE 0 END), 0),
			COALESCE(SUM(CASE WHEN type = 'liability' THEN balance ELSE 0 END), 0)
		FROM accounts WHERE archived_at IS NULL`,
	).Scan(&summary.TotalAssets, &summary.TotalLiabilities)
	if err != nil {
		return nil, err
	}
	summary.NetWorth = summary.TotalAssets - summary.TotalLiabilities
	return &summary, nil
}

// GetNetWorthHistory returns one point at the end of every calendar month
// (Europe/London) from the first recorded balance to the end of last month,
// carrying each account's latest balance forward through months without an
// entry, plus a live "now" point from the current balances.  Archived accounts
// count in the months before they were archived.
func (s *Store) GetNetWorthHistory(ctx context.Context) ([]NetWorthPoint, error) {
	rows, err := s.db.Query(ctx, `
		WITH bounds AS (
			SELECT DATE_TRUNC('month', MIN(recorded_at AT TIME ZONE 'Europe/London')) AS first_month
			FROM account_balance_history
		),
		months AS (
			SELECT gs AS month_start
			FROM bounds,
			     GENERATE_SERIES(bounds.first_month,
			                     DATE_TRUNC('month', NOW() AT TIME ZONE 'Europe/London') - INTERVAL '1 month',
			                     INTERVAL '1 month') AS gs
			WHERE bounds.first_month IS NOT NULL
		),
		historical AS (
			SELECT
				COALESCE(SUM(CASE WHEN a.type = 'asset'     THEN COALESCE(b.balance, 0) ELSE 0 END), 0) AS total_assets,
				COALESCE(SUM(CASE WHEN a.type = 'liability' THEN COALESCE(b.balance, 0) ELSE 0 END), 0) AS total_liabilities,
				((m.month_start + INTERVAL '1 month') AT TIME ZONE 'Europe/London') - INTERVAL '1 second' AS month_ts
			FROM months m
			CROSS JOIN accounts a
			LEFT JOIN LATERAL (
				SELECT balance
				FROM account_balance_history h
				WHERE h.account_id = a.id
				  AND h.recorded_at < ((m.month_start + INTERVAL '1 month') AT TIME ZONE 'Europe/London')
				ORDER BY h.recorded_at DESC
				LIMIT 1
			) b ON true
			GROUP BY m.month_start
		),
		current_point AS (
			SELECT
				COALESCE(SUM(CASE WHEN type = 'asset'     THEN balance ELSE 0 END), 0) AS total_assets,
				COALESCE(SUM(CASE WHEN type = 'liability' THEN balance ELSE 0 END), 0) AS total_liabilities,
				NOW() AS month_ts
			FROM accounts
			WHERE archived_at IS NULL
		)
		SELECT total_assets, total_liabilities, month_ts FROM historical
		UNION ALL
		SELECT total_assets, total_liabilities, month_ts FROM current_point
		ORDER BY month_ts ASC`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var points []NetWorthPoint
	for rows.Next() {
		var p NetWorthPoint
		if err := rows.Scan(&p.TotalAssets, &p.TotalLiabilities, &p.RecordedAt); err != nil {
			return nil, err
		}
		p.NetWorth = p.TotalAssets - p.TotalLiabilities
		points = append(points, p)
	}
	if points == nil {
		points = []NetWorthPoint{}
	}
	return points, rows.Err()
}

// Mortgage Goal

func (s *Store) GetMortgageGoal(ctx context.Context) (*MortgageGoal, error) {
	g := &MortgageGoal{}
	err := s.db.QueryRow(ctx, `
		SELECT id, account_id, property_account_id, monthly_payment, monthly_overpayment,
		       annual_interest_rate, follow_on_rate, target_ltv,
		       COALESCE(fixed_term_end::text, ''), COALESCE(term_end::text, ''), overpayment_allowance_pct
		FROM mortgage_goal WHERE id = 1`,
	).Scan(&g.ID, &g.AccountID, &g.PropertyAccountID, &g.MonthlyPayment, &g.MonthlyOverpayment,
		&g.AnnualInterestRate, &g.FollowOnRate, &g.TargetLtv,
		&g.FixedTermEnd, &g.TermEnd, &g.OverpaymentAllowancePct)
	if err != nil {
		return nil, err
	}
	return g, nil
}

func nullableDate(d string) interface{} {
	if d == "" {
		return nil
	}
	return d
}

func (s *Store) UpsertMortgageGoal(ctx context.Context, g MortgageGoal) (*MortgageGoal, error) {
	_, err := s.db.Exec(ctx, `
		INSERT INTO mortgage_goal (id, account_id, property_account_id, monthly_payment, monthly_overpayment,
			annual_interest_rate, follow_on_rate, target_ltv, fixed_term_end, term_end, overpayment_allowance_pct)
		VALUES (1, $1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
		ON CONFLICT (id) DO UPDATE SET
			account_id = EXCLUDED.account_id,
			property_account_id = EXCLUDED.property_account_id,
			monthly_payment = EXCLUDED.monthly_payment,
			monthly_overpayment = EXCLUDED.monthly_overpayment,
			annual_interest_rate = EXCLUDED.annual_interest_rate,
			follow_on_rate = EXCLUDED.follow_on_rate,
			target_ltv = EXCLUDED.target_ltv,
			fixed_term_end = EXCLUDED.fixed_term_end,
			term_end = EXCLUDED.term_end,
			overpayment_allowance_pct = EXCLUDED.overpayment_allowance_pct`,
		g.AccountID, g.PropertyAccountID, g.MonthlyPayment, g.MonthlyOverpayment,
		g.AnnualInterestRate, g.FollowOnRate, g.TargetLtv,
		nullableDate(g.FixedTermEnd), nullableDate(g.TermEnd), g.OverpaymentAllowancePct,
	)
	if err != nil {
		return nil, err
	}
	return s.GetMortgageGoal(ctx)
}

func (s *Store) ProjectMortgage(ctx context.Context) (*MortgageProjection, error) {
	goal, err := s.GetMortgageGoal(ctx)
	if err != nil {
		return nil, err
	}
	now := time.Now()
	proj := &MortgageProjection{MortgageGoal: *goal, Scenarios: []MortgageScenario{}}

	balance, ok := s.accountBalance(ctx, goal.AccountID)
	proj.CurrentBalance = balance
	switch {
	case goal.AccountID == nil || goal.MonthlyPayment <= 0:
		proj.Status = "not_configured"
		return proj, nil
	case !ok:
		proj.Status = "account_missing"
		return proj, nil
	case balance < 0:
		proj.Status = "invalid_balance"
		return proj, nil
	case balance == 0:
		proj.Status = "paid_off"
		proj.OnTrack = true
		return proj, nil
	}
	proj.Status = "ok"

	in := mortgageInputs{
		now:          now,
		currentAge:   -1,
		balance:      balance,
		payment:      goal.MonthlyPayment,
		overpayment:  goal.MonthlyOverpayment,
		rate:         goal.AnnualInterestRate,
		allowancePct: goal.OverpaymentAllowancePct,
		targetMonths: -1,
	}
	if plan, ok := s.loadPlan(ctx, now); ok {
		in.currentAge = plan.currentAge
		in.targetMonths = max(0, monthsFromYears(float64(plan.profile.RetirementAge)-plan.currentAge))
	} else {
		proj.ProfileIncomplete = true
	}
	if t, err := time.Parse("2006-01-02", goal.FixedTermEnd); err == nil {
		in.fixedTermEnd = t
		if goal.FollowOnRate != nil {
			in.followOnRate = *goal.FollowOnRate
		} else {
			in.followOnRate = goal.AnnualInterestRate + 0.01
			proj.FollowOnRateAssumed = true
		}
		proj.EffectiveFollowOnRate = in.followOnRate
		if te, err := time.Parse("2006-01-02", goal.TermEnd); err == nil {
			in.termEnd = te
			proj.PaymentRecalculated = te.After(t)
		}
	}

	// Property value for LTV: the linked property account, or the only one.
	var propertyValue float64
	if goal.PropertyAccountID != nil {
		propertyValue, _ = s.accountBalance(ctx, goal.PropertyAccountID)
	} else {
		var count int
		_ = s.db.QueryRow(ctx,
			`SELECT COUNT(*), COALESCE(SUM(balance), 0) FROM accounts WHERE category = 'property' AND type = 'asset' AND archived_at IS NULL`,
		).Scan(&count, &propertyValue)
		if count > 1 {
			proj.PropertyAccountAmbiguous = true
			propertyValue = 0
		}
	}
	proj.PropertyValue = round2(propertyValue)
	if propertyValue > 0 {
		proj.CurrentLTV = balance / propertyValue
	}

	targetBalance, ltvMonths := 0.0, 0
	if goal.TargetLtv > 0 && propertyValue > 0 && !in.fixedTermEnd.IsZero() && in.fixedTermEnd.After(now) {
		targetBalance = goal.TargetLtv * propertyValue
		proj.TargetLtvBalance = round2(targetBalance)
		for in.monthStart(ltvMonths).Before(in.fixedTermEnd) {
			ltvMonths++
		}
	}

	projectMortgage(in, targetBalance, ltvMonths, proj)
	if proj.NeverPaysOff {
		proj.Status = "never_pays_off"
	}
	return proj, nil
}

// Pension Goal

func (s *Store) GetPensionGoal(ctx context.Context) (*PensionGoal, error) {
	g := &PensionGoal{}
	err := s.db.QueryRow(ctx, `
		SELECT id, account_id, monthly_contribution, annual_growth_rate,
		       min_contrib_salary, min_contrib_rate, glidepath_years, glidepath_rate,
		       own_state_pension_monthly, own_state_pension_age
		FROM pension_goal WHERE id = 1`,
	).Scan(&g.ID, &g.AccountID, &g.MonthlyContribution, &g.AnnualGrowthRate,
		&g.MinContribSalary, &g.MinContribRate, &g.GlidepathYears, &g.GlidepathRate,
		&g.OwnStatePensionMonthly, &g.OwnStatePensionAge)
	if err != nil {
		return nil, err
	}
	return g, nil
}

func (s *Store) UpsertPensionGoal(ctx context.Context, g PensionGoal) (*PensionGoal, error) {
	_, err := s.db.Exec(ctx, `
		INSERT INTO pension_goal (id, account_id, monthly_contribution, annual_growth_rate,
			min_contrib_salary, min_contrib_rate, glidepath_years, glidepath_rate,
			own_state_pension_monthly, own_state_pension_age)
		VALUES (1, $1, $2, $3, $4, $5, $6, $7, $8, $9)
		ON CONFLICT (id) DO UPDATE SET
			account_id = EXCLUDED.account_id,
			monthly_contribution = EXCLUDED.monthly_contribution,
			annual_growth_rate = EXCLUDED.annual_growth_rate,
			min_contrib_salary = EXCLUDED.min_contrib_salary,
			min_contrib_rate = EXCLUDED.min_contrib_rate,
			glidepath_years = EXCLUDED.glidepath_years,
			glidepath_rate = EXCLUDED.glidepath_rate,
			own_state_pension_monthly = EXCLUDED.own_state_pension_monthly,
			own_state_pension_age = EXCLUDED.own_state_pension_age`,
		g.AccountID, g.MonthlyContribution, g.AnnualGrowthRate,
		g.MinContribSalary, g.MinContribRate, g.GlidepathYears, g.GlidepathRate,
		g.OwnStatePensionMonthly, g.OwnStatePensionAge,
	)
	if err != nil {
		return nil, err
	}
	return s.GetPensionGoal(ctx)
}

func (s *Store) ProjectPension(ctx context.Context) (*PensionProjection, error) {
	goal, err := s.GetPensionGoal(ctx)
	if err != nil {
		return nil, err
	}
	now := time.Now()
	proj := &PensionProjection{PensionGoal: *goal, Schedule: []PensionScheduleRow{}}

	balance, ok := s.accountBalance(ctx, goal.AccountID)
	proj.CurrentValue = balance
	proj.AccountMissing = goal.AccountID != nil && !ok

	plan, ok := s.loadPlan(ctx, now)
	if !ok {
		proj.ProfileIncomplete = true
		return proj, nil
	}
	proj.StopContributionAge = plan.profile.RetirementAge
	proj.DrawAge = plan.profile.PensionAccessAge
	proj.InflationRate = plan.profile.InflationRate
	proj.TargetMonthlyIncome = plan.profile.TargetMonthlyIncome
	proj.PartnerStatePensionMonthly = plan.profile.PartnerStatePensionMonthly

	projectPension(pensionInputs{
		now:                 now,
		currentAge:          plan.currentAge,
		currentValue:        balance,
		monthlyContribution: goal.MonthlyContribution,
		minMonthly:          goal.MinContribSalary * goal.MinContribRate / 12,
		growthRate:          goal.AnnualGrowthRate,
		glidepathRate:       goal.GlidepathRate,
		glidepathYears:      goal.GlidepathYears,
		inflation:           plan.profile.InflationRate,
		stopAge:             plan.profile.RetirementAge,
		drawAge:             plan.profile.PensionAccessAge,
		ownSPMonthly:        goal.OwnStatePensionMonthly,
		ownSPAge:            goal.OwnStatePensionAge,
		hh:                  plan.hh,
	}, proj)
	return proj, nil
}

// ISA Bridge Goal

func (s *Store) GetIsaBridgeGoal(ctx context.Context) (*IsaBridgeGoal, error) {
	g := &IsaBridgeGoal{}
	err := s.db.QueryRow(ctx, `
		SELECT id, account_id, monthly_contribution, annual_growth_rate, glidepath_years, glidepath_rate
		FROM isa_bridge_goal WHERE id = 1`,
	).Scan(&g.ID, &g.AccountID, &g.MonthlyContribution, &g.AnnualGrowthRate, &g.GlidepathYears, &g.GlidepathRate)
	if err != nil {
		return nil, err
	}
	return g, nil
}

func (s *Store) UpsertIsaBridgeGoal(ctx context.Context, g IsaBridgeGoal) (*IsaBridgeGoal, error) {
	_, err := s.db.Exec(ctx, `
		INSERT INTO isa_bridge_goal (id, account_id, monthly_contribution, annual_growth_rate, glidepath_years, glidepath_rate)
		VALUES (1, $1, $2, $3, $4, $5)
		ON CONFLICT (id) DO UPDATE SET
			account_id = EXCLUDED.account_id,
			monthly_contribution = EXCLUDED.monthly_contribution,
			annual_growth_rate = EXCLUDED.annual_growth_rate,
			glidepath_years = EXCLUDED.glidepath_years,
			glidepath_rate = EXCLUDED.glidepath_rate`,
		g.AccountID, g.MonthlyContribution, g.AnnualGrowthRate, g.GlidepathYears, g.GlidepathRate,
	)
	if err != nil {
		return nil, err
	}
	return s.GetIsaBridgeGoal(ctx)
}

func (s *Store) ProjectIsaBridge(ctx context.Context) (*IsaBridgeProjection, error) {
	goal, err := s.GetIsaBridgeGoal(ctx)
	if err != nil {
		return nil, err
	}
	now := time.Now()
	proj := &IsaBridgeProjection{IsaBridgeGoal: *goal, Schedule: []IsaScheduleRow{}}

	balance, ok := s.accountBalance(ctx, goal.AccountID)
	proj.CurrentValue = balance
	proj.AccountMissing = goal.AccountID != nil && !ok

	plan, ok := s.loadPlan(ctx, now)
	if !ok {
		proj.ProfileIncomplete = true
		return proj, nil
	}
	proj.BridgeStartAge = plan.profile.RetirementAge
	proj.BridgeEndAge = plan.profile.PensionAccessAge
	proj.InflationRate = plan.profile.InflationRate
	proj.TargetMonthlyIncome = plan.profile.TargetMonthlyIncome
	proj.PartnerStatePensionMonthly = plan.profile.PartnerStatePensionMonthly

	projectIsaBridge(isaInputs{
		now:                 now,
		currentAge:          plan.currentAge,
		currentValue:        balance,
		monthlyContribution: goal.MonthlyContribution,
		growthRate:          goal.AnnualGrowthRate,
		glidepathRate:       goal.GlidepathRate,
		glidepathYears:      goal.GlidepathYears,
		inflation:           plan.profile.InflationRate,
		startAge:            plan.profile.RetirementAge,
		endAge:              plan.profile.PensionAccessAge,
		hh:                  plan.hh,
	}, proj)
	return proj, nil
}

// Trading 212 Config

func (s *Store) GetTrading212Config(ctx context.Context) (*Trading212Config, error) {
	c := &Trading212Config{}
	err := s.db.QueryRow(ctx,
		`SELECT api_key, account_id, last_synced_at FROM trading212_config WHERE id = 1`,
	).Scan(&c.APIKey, &c.AccountID, &c.LastSyncedAt)
	if err != nil {
		return nil, err
	}
	return c, nil
}

func (s *Store) UpsertTrading212Config(ctx context.Context, req UpdateAPIKeyRequest) error {
	_, err := s.db.Exec(ctx, `
		INSERT INTO trading212_config (id, api_key, account_id) VALUES (1, $1, $2)
		ON CONFLICT (id) DO UPDATE SET
			api_key = EXCLUDED.api_key,
			account_id = EXCLUDED.account_id`,
		req.APIKey, req.AccountID,
	)
	return err
}

func (s *Store) UpdateTrading212SyncTime(ctx context.Context, accountID int, balance float64) error {
	return pgx.BeginFunc(ctx, s.db, func(tx pgx.Tx) error {
		if _, err := setBalance(ctx, tx, accountID, math.Round(balance*100)/100); err != nil {
			return err
		}
		_, err := tx.Exec(ctx, `UPDATE trading212_config SET last_synced_at = NOW() WHERE id = 1`)
		return err
	})
}
