package main

import (
	"fmt"
	"time"
)

// Request validation: reject values that would silently produce a misleading
// projection.  Each returns "" when valid, otherwise a message for the user.

func validRate(name string, r float64) string {
	if r < -0.5 || r > 1 {
		return fmt.Sprintf("%s must be between -50%% and 100%% (send it as a decimal, e.g. 0.05 for 5%%)", name)
	}
	return ""
}

func validDate(name, d string) string {
	if d == "" {
		return ""
	}
	if _, err := time.Parse("2006-01-02", d); err != nil {
		return fmt.Sprintf("%s must be a date (YYYY-MM-DD)", name)
	}
	return ""
}

func firstProblem(checks ...string) string {
	for _, c := range checks {
		if c != "" {
			return c
		}
	}
	return ""
}

func validateProfile(p UserProfile) string {
	if p.DateOfBirth == "" {
		return "date_of_birth is required"
	}
	return firstProblem(
		validDate("date_of_birth", p.DateOfBirth),
		validDate("partner_date_of_birth", p.PartnerDateOfBirth),
		validRate("inflation", p.InflationRate),
		func() string {
			switch {
			case p.RetirementAge <= 0 || p.PensionAccessAge <= 0:
				return "retirement and pension access ages are required"
			case p.RetirementAge > p.PensionAccessAge:
				return "retirement age must not be after the pension access age"
			case p.PensionAccessAge >= planToAge:
				return fmt.Sprintf("pension access age must be under %d", planToAge)
			case p.TargetMonthlyIncome < 0 || p.SingleTargetMonthlyIncome < 0 || p.PartnerStatePensionMonthly < 0:
				return "income and pension amounts must not be negative"
			case p.PartnerPensionEndAge < 0:
				return "partner pension end age must not be negative"
			case p.PartnerStatePensionAge != 0 && (p.PartnerStatePensionAge < 55 || p.PartnerStatePensionAge > 80):
				return "partner's State Pension age must be between 55 and 80, or blank to use their date of birth"
			case p.PartnerStatePensionAge != 0 && p.PartnerDateOfBirth == "":
				return "set your partner's date of birth to use a State Pension age for them"
			}
			return ""
		}(),
	)
}

func validatePensionGoal(g PensionGoal) string {
	return firstProblem(
		validRate("growth rate", g.AnnualGrowthRate),
		validRate("glidepath rate", g.GlidepathRate),
		validRate("minimum contribution rate", g.MinContribRate),
		func() string {
			switch {
			case g.MonthlyContribution < 0 || g.MinContribSalary < 0 || g.OwnStatePensionMonthly < 0:
				return "amounts must not be negative"
			case g.GlidepathYears < 0 || g.GlidepathYears > 50:
				return "glidepath years must be between 0 and 50"
			case g.OwnStatePensionAge <= 0 || g.OwnStatePensionAge >= planToAge:
				return "state pension age must be between 1 and 99"
			}
			return ""
		}(),
	)
}

func validateIsaBridgeGoal(g IsaBridgeGoal) string {
	return firstProblem(
		validRate("growth rate", g.AnnualGrowthRate),
		validRate("glidepath rate", g.GlidepathRate),
		func() string {
			switch {
			case g.MonthlyContribution < 0:
				return "contribution must not be negative"
			case g.GlidepathYears < 0 || g.GlidepathYears > 50:
				return "glidepath years must be between 0 and 50"
			}
			return ""
		}(),
	)
}

func validateMortgageGoal(g MortgageGoal) string {
	followOn := ""
	if g.FollowOnRate != nil {
		followOn = validRate("follow-on rate", *g.FollowOnRate)
	}
	return firstProblem(
		validRate("interest rate", g.AnnualInterestRate),
		followOn,
		validDate("fixed_term_end", g.FixedTermEnd),
		validDate("term_end", g.TermEnd),
		func() string {
			switch {
			case g.MonthlyPayment < 0 || g.MonthlyOverpayment < 0:
				return "payments must not be negative"
			case g.TargetLtv < 0 || g.TargetLtv > 1:
				return "target LTV must be between 0% and 100%"
			case g.OverpaymentAllowancePct < 0 || g.OverpaymentAllowancePct > 1:
				return "overpayment allowance must be between 0% and 100%"
			}
			return ""
		}(),
	)
}
