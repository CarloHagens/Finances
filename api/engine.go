package main

// Pure projection engine: no database access, so every calculation here can be
// unit-tested directly (see engine_test.go).  store.go gathers the inputs and
// copies the results onto the API response types.

import (
	"math"
	"time"
)

const (
	// UK income tax, England/Wales/NI, 2026/27.  Bands apply to taxable income
	// (after the personal allowance), as in the legislation.
	personalAllowance = 12570.0
	basicRateBand     = 37700.0  // first £37,700 of taxable income at 20%
	higherRateLimit   = 125140.0 // taxable income above this at 45%
	paTaperThreshold  = 100000.0 // PA withdrawn £1 per £2 above this; never indexed (frozen since 2010)

	lumpSumAllowance = 268275.0 // lifetime tax-free cash cap; not indexed
	pclsFraction     = 0.25     // tax-free share of each uncrystallised (UFPLS) withdrawal

	planToAge          = 100
	isaAnnualAllowance = 20000.0
)

// Income tax thresholds are frozen until 5 April 2031 (Autumn Budget 2025) and
// assumed to rise with inflation after that.  Update this date if a later Budget
// extends the freeze.
var taxBandsFrozenUntil = time.Date(2031, time.April, 6, 0, 0, 0, 0, time.UTC)

// monthlyRate converts an effective annual rate into the equivalent monthly rate,
// so twelve months of compounding reproduce the annual rate exactly (7%/12
// would compound to 7.23%).
func monthlyRate(annual float64) float64 {
	return math.Pow(1+annual, 1.0/12) - 1
}

func round2(v float64) float64 { return math.Round(v*100) / 100 }
func round1(v float64) float64 { return math.Round(v*10) / 10 }

// monthsFromYears converts a span in years into a whole number of months.
func monthsFromYears(years float64) int {
	return int(math.Round(years * 12))
}

func yearsBetween(from, to time.Time) float64 {
	return to.Sub(from).Hours() / (24 * 365.25)
}

// incomeTax returns the income tax due on a year's taxable income.  bandFactor
// scales the personal allowance and bands (1 while frozen); the allowance taper
// threshold is not scaled.
func incomeTax(income, bandFactor float64) float64 {
	if income <= 0 {
		return 0
	}
	pa := personalAllowance * bandFactor
	if income > paTaperThreshold {
		pa = math.Max(0, pa-(income-paTaperThreshold)/2)
	}
	taxable := math.Max(0, income-pa)
	basic := basicRateBand * bandFactor
	higher := higherRateLimit * bandFactor

	tax := 0.20 * math.Min(taxable, basic)
	if taxable > basic {
		tax += 0.40 * (math.Min(taxable, higher) - basic)
	}
	if taxable > higher {
		tax += 0.45 * (taxable - higher)
	}
	return tax
}

// drawdownGross returns the gross annual pension withdrawal that leaves
// netNeeded after paying all income tax on the withdrawal *and* on
// otherTaxable (the user's own state pension: paid gross, its tax collected
// through the drawdown's PAYE code).  25% of each withdrawal is tax-free, up to
// taxFreeCap (the remaining Lump Sum Allowance, annualised).  netNeeded may be
// negative when the state pension alone covers the target.
func drawdownGross(netNeeded, bandFactor, otherTaxable, taxFreeCap float64) float64 {
	netOf := func(gross float64) float64 {
		taxFree := math.Min(pclsFraction*gross, taxFreeCap)
		return gross - incomeTax(otherTaxable+gross-taxFree, bandFactor)
	}
	if netOf(0) >= netNeeded {
		return 0
	}
	lo := 0.0
	hi := math.Max(netNeeded, 0) + incomeTax(otherTaxable, bandFactor) + 1
	for netOf(hi) < netNeeded {
		hi *= 2
	}
	for i := 0; i < 100; i++ {
		mid := (lo + hi) / 2
		if netOf(mid) < netNeeded {
			lo = mid
		} else {
			hi = mid
		}
	}
	return hi
}

// statePensionAge returns the UK State Pension age for a date of birth under
// current legislation (the 1977–78 phase-in to 68 is approximated as 67.5).
func statePensionAge(dob time.Time) float64 {
	d := func(y int, m time.Month, day int) time.Time { return time.Date(y, m, day, 0, 0, 0, 0, time.UTC) }
	switch {
	case dob.Before(d(1954, time.October, 6)):
		return 65
	case dob.Before(d(1960, time.April, 6)):
		return 66
	case dob.Before(d(1961, time.March, 6)):
		// 66 years + 1 month for 6 Apr–5 May 1960, rising a month at a time.
		start := d(1960, time.April, 6)
		n := (dob.Year()-start.Year())*12 + int(dob.Month()) - int(start.Month())
		if dob.Day() < 6 {
			n--
		}
		return 66 + float64(n+1)/12
	case dob.Before(d(1977, time.April, 6)):
		return 67
	case dob.Before(d(1978, time.April, 6)):
		return 67.5
	default:
		return 68
	}
}

// partnerStatePensionAge is the age the partner's state pension starts: the
// override when set, otherwise the legislated age for their date of birth.
func partnerStatePensionAge(dob time.Time, override int) float64 {
	if override > 0 {
		return float64(override)
	}
	return statePensionAge(dob)
}

// timeline converts month offsets from today into inflation and tax-band factors.
type timeline struct {
	now        time.Time
	currentAge float64
	inflation  float64
}

func (t timeline) inflationFactor(m int) float64 {
	return math.Pow(1+t.inflation, float64(m)/12)
}

// bandFactor is how far tax thresholds have risen by month m: not at all while
// frozen, then with inflation.
func (t timeline) bandFactor(m int) float64 {
	frozenYears := math.Max(0, yearsBetween(t.now, taxBandsFrozenUntil))
	return math.Pow(1+t.inflation, math.Max(0, float64(m)/12-frozenYears))
}

func (t timeline) ageAt(m int) float64 { return t.currentAge + float64(m)/12 }

// household holds the retirement-income assumptions shared by the pension and
// ISA bridge goals.  Money is monthly, in today's £.
type household struct {
	targetMonthly         float64
	singleTargetMonthly   float64 // after the partner's pension stops; 0 = unchanged
	partnerPensionMonthly float64
	partnerAgeNow         float64 // < 0 when the partner's date of birth is unknown
	partnerSPAge          float64
	partnerPensionEndAge  int // partner's age when their pension stops; 0 = never
}

func (h household) partnerKnown() bool { return h.partnerAgeNow >= 0 }

func (h household) partnerPensionEnded(m int) bool {
	return h.partnerKnown() && h.partnerPensionEndAge > 0 &&
		h.partnerAgeNow+float64(m)/12 >= float64(h.partnerPensionEndAge)
}

func (h household) partnerPensionPaid(m int) bool {
	if h.partnerPensionMonthly <= 0 {
		return false
	}
	if !h.partnerKnown() {
		return true // no date of birth: assume already in payment
	}
	return h.partnerAgeNow+float64(m)/12 >= h.partnerSPAge && !h.partnerPensionEnded(m)
}

func (h household) targetToday(m int) float64 {
	if h.singleTargetMonthly > 0 && h.partnerPensionEnded(m) {
		return h.singleTargetMonthly
	}
	return h.targetMonthly
}

// partnerNetNominal is the partner's state pension in month m, in that month's
// £, after the partner's own income tax (against their own allowance).
func (h household) partnerNetNominal(t timeline, m int) (gross, net float64) {
	if !h.partnerPensionPaid(m) {
		return 0, 0
	}
	gross = h.partnerPensionMonthly * t.inflationFactor(m)
	return gross, gross - incomeTax(gross*12, t.bandFactor(m))/12
}

// ---------------------------------------------------------------------------
// Pension
// ---------------------------------------------------------------------------

type pensionInputs struct {
	now                 time.Time
	currentAge          float64
	currentValue        float64
	monthlyContribution float64
	minMonthly          float64
	growthRate          float64
	glidepathRate       float64
	glidepathYears      int
	inflation           float64
	stopAge             int // stop contributing (= stop work)
	drawAge             int // start drawing the pension
	ownSPMonthly        float64
	ownSPAge            int
	hh                  household
}

// projectPension fills every computed field of proj.
func projectPension(in pensionInputs, proj *PensionProjection) {
	t := timeline{now: in.now, currentAge: in.currentAge, inflation: in.inflation}

	planMonths := max(0, monthsFromYears(planToAge-in.currentAge))
	monthsToDraw := min(planMonths, max(0, monthsFromYears(float64(in.drawAge)-in.currentAge)))
	monthsToStop := min(monthsToDraw, max(0, monthsFromYears(float64(in.stopAge)-in.currentAge)))
	proj.InDrawdown = monthsToDraw == 0
	proj.PartnerPensionAssumed = in.hh.partnerPensionMonthly > 0 && !in.hh.partnerKnown()

	glideStart := max(0, monthsToDraw-in.glidepathYears*12)
	rFull, rGlide := monthlyRate(in.growthRate), monthlyRate(in.glidepathRate)
	rateAt := func(m int) float64 {
		if m < glideStart {
			return rFull
		}
		return rGlide
	}

	// --- Drawdown: month by month from draw age to 100, solving the gross
	// withdrawal that delivers the household target after all tax.
	retirementMonths := planMonths - monthsToDraw
	gross := make([]float64, retirementMonths)
	lsaUsed := 0.0
	potNeeded, disc := 0.0, 1.0
	capMonth := -1
	type snapshot struct{ gross, tax, net float64 }
	var early, late snapshot
	haveLate := false
	for k := 0; k < retirementMonths; k++ {
		m := monthsToDraw + k
		infl, bf := t.inflationFactor(m), t.bandFactor(m)

		_, partnerNet := in.hh.partnerNetNominal(t, m)
		ownActive := in.ownSPMonthly > 0 && t.ageAt(m) >= float64(in.ownSPAge)
		ownSP := 0.0
		if ownActive {
			ownSP = in.ownSPMonthly * infl
		}
		netNeeded := in.hh.targetToday(m)*infl - partnerNet - ownSP

		remaining := math.Max(0, lumpSumAllowance-lsaUsed)
		g := drawdownGross(netNeeded*12, bf, ownSP*12, remaining*12) / 12
		taxFree := math.Min(pclsFraction*g, remaining)
		lsaUsed += taxFree
		if capMonth < 0 && lsaUsed >= lumpSumAllowance-0.005 {
			capMonth = m
		}
		gross[k] = g

		// Pot needed = present value at draw age of the withdrawal stream.
		disc *= 1 + rGlide
		potNeeded += g / disc

		// Representative figures in today's £: total tax on pension + drawdown.
		tax := incomeTax((ownSP+g-taxFree)*12, bf) / 12
		snap := snapshot{g / infl, tax / infl, (g - tax) / infl}
		if k == 0 {
			early = snap
		}
		if ownActive && !haveLate && in.ownSPAge > in.drawAge {
			late, haveLate = snap, true
		}
	}
	if !haveLate {
		late = early
	}
	proj.GrossMonthlyDrawdown = round2(early.gross)
	proj.TaxMonthlyDrawdown = round2(early.tax)
	proj.NetMonthlyDrawdown = round2(early.net)
	proj.GrossMonthlyDrawdownLate = round2(late.gross)
	proj.TaxMonthlyDrawdownLate = round2(late.tax)
	proj.NetMonthlyDrawdownLate = round2(late.net)
	if capMonth >= 0 {
		proj.LumpSumCapAge = round1(t.ageAt(capMonth + 1))
	}
	proj.InflatedTarget = round2(potNeeded)
	drawInfl := t.inflationFactor(monthsToDraw)
	proj.InflatedTargetToday = round2(potNeeded / drawInfl)

	// --- Coast FIRE.  suffixGF[m] grows £1 at month m to draw age;
	// suffixAnnuity[m] is the draw-age value of £1/month contributed from m
	// until the stop month.
	suffixGF := make([]float64, monthsToDraw+1)
	suffixGF[monthsToDraw] = 1
	for m := monthsToDraw - 1; m >= 0; m-- {
		suffixGF[m] = suffixGF[m+1] * (1 + rateAt(m))
	}
	suffixAnnuity := make([]float64, monthsToDraw+1)
	for m := monthsToDraw - 1; m >= 0; m-- {
		suffixAnnuity[m] = suffixAnnuity[m+1]
		if m < monthsToStop {
			suffixAnnuity[m] += suffixGF[m+1]
		}
	}
	coastNumberAt := func(m int, contrib float64) float64 {
		return (potNeeded - contrib*suffixAnnuity[m]) / suffixGF[m]
	}
	// coastSearch walks the balance forward with the real contribution and
	// returns the first month the coast number (at contrib) is met.
	coastSearch := func(contrib float64) (number float64, reached bool, date string, age float64) {
		number = round2(math.Max(0, coastNumberAt(0, contrib)))
		if in.currentValue >= number {
			return number, true, in.now.Format("2006-01-02"), round1(in.currentAge)
		}
		balance := in.currentValue
		for m := 0; m < monthsToStop; m++ {
			balance = balance*(1+rateAt(m)) + in.monthlyContribution
			if m+1 < monthsToDraw && balance >= coastNumberAt(m+1, contrib) {
				return number, false, in.now.AddDate(0, m+1, 0).Format("2006-01-02"), round1(t.ageAt(m + 1))
			}
		}
		return number, false, "", 0
	}

	minMonthly := in.minMonthly
	proj.MinMonthlyContrib = round2(minMonthly)
	proj.CoastFireNumber, proj.CoastFireReached, proj.CoastFireDate, proj.CoastFireAge = coastSearch(0)
	proj.MinCoastFireNumber, proj.MinCoastFireReached, proj.MinCoastFireDate, proj.MinCoastFireAge = coastSearch(minMonthly)
	if minMonthly <= 0 {
		proj.MinCoastFireDate, proj.MinCoastFireAge = "", 0
	}

	// --- Projected pot at draw age under the three contribution scenarios.
	projectWith := func(contrib float64) float64 {
		v := in.currentValue
		for m := 0; m < monthsToDraw; m++ {
			c := 0.0
			if m < monthsToStop {
				c = contrib
			}
			v = v*(1+rateAt(m)) + c
		}
		return v
	}
	full := projectWith(in.monthlyContribution)
	proj.ProjectedAtDraw = round2(full)
	proj.ProjectedAtDrawToday = round2(full / drawInfl)
	proj.ProjectedAtDrawMinContrib = round2(projectWith(minMonthly))
	proj.ProjectedAtDrawNoContrib = round2(projectWith(0))
	proj.Surplus = round2(full - potNeeded)
	proj.OnTrack = full >= potNeeded

	// --- How long the projected pot lasts on the same withdrawal stream.
	pot := full
	for k := 0; k < retirementMonths; k++ {
		pot = pot*(1+rGlide) - gross[k]
		if pot <= 0 {
			proj.PotLastsToAge = round1(t.ageAt(monthsToDraw + k + 1))
			break
		}
	}

	// --- Year-by-year schedule for the app's chart (balances floored at 0).
	proj.Schedule = []PensionScheduleRow{}
	bal := [3]float64{in.currentValue, in.currentValue, in.currentValue}
	contribs := [3]float64{in.monthlyContribution, minMonthly, 0}
	yearWithdrawn := 0.0
	for m := 0; m < planMonths; m++ {
		for i := range bal {
			switch {
			case m < monthsToStop:
				bal[i] = bal[i]*(1+rateAt(m)) + contribs[i]
			case m < monthsToDraw:
				bal[i] = bal[i] * (1 + rateAt(m))
			default:
				bal[i] = math.Max(0, bal[i]*(1+rGlide)-gross[m-monthsToDraw])
			}
		}
		if m >= monthsToDraw {
			yearWithdrawn += gross[m-monthsToDraw]
		}
		if (m+1)%12 == 0 || m == planMonths-1 {
			phase := "draw"
			if m < monthsToStop {
				phase = "contrib"
			} else if m < monthsToDraw {
				phase = "growth"
			}
			proj.Schedule = append(proj.Schedule, PensionScheduleRow{
				Age:             round1(t.ageAt(m + 1)),
				Phase:           phase,
				Balance:         round2(bal[0]),
				BalanceMin:      round2(bal[1]),
				BalanceNone:     round2(bal[2]),
				GrossWithdrawal: round2(yearWithdrawn),
			})
			yearWithdrawn = 0
		}
	}
}

// ---------------------------------------------------------------------------
// ISA bridge
// ---------------------------------------------------------------------------

type isaInputs struct {
	now                 time.Time
	currentAge          float64
	currentValue        float64
	monthlyContribution float64
	growthRate          float64
	glidepathRate       float64
	glidepathYears      int
	inflation           float64
	startAge            int // stop work
	endAge              int // pension draw age
	hh                  household
}

func projectIsaBridge(in isaInputs, proj *IsaBridgeProjection) {
	t := timeline{now: in.now, currentAge: in.currentAge, inflation: in.inflation}

	endMonths := max(0, monthsFromYears(float64(in.endAge)-in.currentAge))
	accMonths := min(endMonths, max(0, monthsFromYears(float64(in.startAge)-in.currentAge)))
	proj.InBridge = accMonths == 0 && endMonths > 0
	proj.PartnerPensionAssumed = in.hh.partnerPensionMonthly > 0 && !in.hh.partnerKnown()

	glideStart := max(0, accMonths-in.glidepathYears*12)
	rFull, rGlide := monthlyRate(in.growthRate), monthlyRate(in.glidepathRate)
	rateAt := func(m int) float64 {
		if m < glideStart {
			return rFull
		}
		return rGlide
	}

	// Accumulation.  growthFactor/annuityFactor decompose the recurrence so the
	// contribution or lump sum needed can be solved for directly.
	value := in.currentValue
	growthFactor, annuityFactor := 1.0, 0.0
	for m := 0; m < accMonths; m++ {
		r := rateAt(m)
		value = value*(1+r) + in.monthlyContribution
		growthFactor *= 1 + r
		annuityFactor = annuityFactor*(1+r) + 1
	}
	projected := value
	proj.ProjectedAtRetirement = round2(projected)

	// Bridge withdrawals: the target rises with inflation every month; the
	// partner's net state pension is netted off only while it is in payment.
	// ISA withdrawals are tax-free.
	need := func(m int) float64 {
		_, partnerNet := in.hh.partnerNetNominal(t, m)
		return math.Max(0, in.hh.targetToday(m)*t.inflationFactor(m)-partnerNet)
	}
	startInfl := t.inflationFactor(accMonths)
	partnerGross, _ := in.hh.partnerNetNominal(t, accMonths)
	proj.InflatedTargetMonthlyIncome = round2(in.hh.targetToday(accMonths) * startInfl)
	proj.InflatedPartnerStatePension = round2(partnerGross)
	if endMonths > accMonths {
		proj.InflatedMonthlyWithdrawal = round2(need(accMonths))
	}

	required, disc := 0.0, 1.0
	runsOut := -1
	for m := accMonths; m < endMonths; m++ {
		w := need(m)
		disc *= 1 + rGlide
		required += w / disc
		value = value*(1+rGlide) - w
		if value < 0 && runsOut < 0 {
			runsOut = m
		}
	}
	proj.RequiredAtRetirement = round2(required)
	proj.RequiredAtRetirementToday = round2(required / startInfl)
	proj.ProjectedAtRetirementToday = round2(projected / startInfl)
	proj.SurvivesToBridgeEnd = runsOut < 0
	proj.OnTrack = proj.SurvivesToBridgeEnd
	if runsOut >= 0 {
		proj.RunsOutAge = round1(t.ageAt(runsOut + 1))
		// The pot sustains this fraction of the inflation-linked withdrawal
		// stream; the shortfall is the rest, at bridge-start money.
		sustainable := 0.0
		if required > 0 {
			sustainable = math.Min(1, projected/required)
		}
		proj.MonthlyShortfall = round2((1 - sustainable) * proj.InflatedMonthlyWithdrawal)
	}

	gap := required - projected
	if gap > 0 {
		proj.RequiredLumpSum = round2(gap / growthFactor)
		if annuityFactor > 0 {
			proj.RequiredMonthlyContribution = round2(in.monthlyContribution + gap/annuityFactor)
		}
	}
	// The deposit that lets contributions stop altogether.
	proj.RequiredLumpSumNoContrib = round2(math.Max(0, required/growthFactor-in.currentValue))

	monthlyAllowance := isaAnnualAllowance / 12
	proj.ContributionExceedsAllowance = in.monthlyContribution > monthlyAllowance+0.005
	proj.RequiredContributionExceedsAllowance = proj.RequiredMonthlyContribution > monthlyAllowance+0.005
	proj.LumpSumExceedsAllowance = proj.RequiredLumpSum > isaAnnualAllowance ||
		proj.RequiredLumpSumNoContrib > isaAnnualAllowance

	// Year-by-year schedule for the chart (balance floored at 0).
	proj.Schedule = []IsaScheduleRow{}
	bal := in.currentValue
	for m := 0; m < endMonths; m++ {
		if m < accMonths {
			bal = bal*(1+rateAt(m)) + in.monthlyContribution
		} else {
			bal = math.Max(0, bal*(1+rGlide)-need(m))
		}
		if (m+1)%12 == 0 || m == endMonths-1 {
			proj.Schedule = append(proj.Schedule, IsaScheduleRow{Age: round1(t.ageAt(m + 1)), Balance: round2(bal)})
		}
	}
}

// ---------------------------------------------------------------------------
// Mortgage
// ---------------------------------------------------------------------------

type mortgageInputs struct {
	now          time.Time
	currentAge   float64 // < 0 when unknown
	balance      float64
	payment      float64
	overpayment  float64
	rate         float64
	followOnRate float64
	fixedTermEnd time.Time // zero = no fixed term
	termEnd      time.Time // zero = unknown; payment then stays the same at remortgage
	allowancePct float64   // penalty-free overpayment per calendar year, of the 1 Jan balance
	targetMonths int       // months until the target (retirement) age; < 0 when unknown
}

// monthStart returns the first day of the calendar month m months from now.
// Stepping from the 1st avoids AddDate rolling the 31st past short months.
func (in mortgageInputs) monthStart(m int) time.Time {
	return time.Date(in.now.Year(), in.now.Month()+time.Month(m), 1, 0, 0, 0, 0, time.UTC)
}

type mortgageRun struct {
	months          int
	neverPaysOff    bool
	totalInterest   float64
	balanceAt       float64 // balance after recordAt payments
	points          []MortgagePoint
	allowanceBreach int // first calendar year overpayments exceed the allowance; 0 = none
}

// simulateMortgage runs the mortgage month by month, overpaying for the first
// overpayMonths months.  Interest accrues daily (balance × rate ÷ 365 × days in
// the month).  At the fixed-term end the rate switches to the follow-on rate
// and, when the term end is known, the payment is recalculated to clear the
// balance by then.
func simulateMortgage(in mortgageInputs, overpayMonths, recordAt int) mortgageRun {
	run := mortgageRun{balanceAt: in.balance, points: []MortgagePoint{}}
	bal, payment := in.balance, in.payment
	switched := false
	year, yearStartBal, yearOverpaid := in.now.Year(), bal, 0.0
	for bal > 0.005 && run.months < 1200 {
		ms := in.monthStart(run.months)
		rate := in.rate
		if !in.fixedTermEnd.IsZero() && !ms.Before(in.fixedTermEnd) {
			rate = in.followOnRate
			if !switched {
				switched = true
				if !in.termEnd.IsZero() {
					n := (in.termEnd.Year()-ms.Year())*12 + int(in.termEnd.Month()) - int(ms.Month()) + 1
					if n > 0 {
						payment = clearingPayment(bal, rate, ms, n)
					}
				}
			}
		}
		if ms.Year() != year {
			year, yearStartBal, yearOverpaid = ms.Year(), bal, 0
		}
		days := time.Date(ms.Year(), ms.Month()+1, 0, 0, 0, 0, 0, time.UTC).Day()
		interest := bal * rate / 365 * float64(days)
		over := 0.0
		if run.months < overpayMonths {
			over = in.overpayment
		}
		principal := payment + over - interest
		if principal <= 0 {
			run.neverPaysOff = true
			break
		}
		if principal > bal {
			// Final, partial month: only interest + balance is paid, the regular
			// payment first and any overpayment after it.
			principal = bal
			over = math.Max(0, math.Min(over, interest+bal-payment))
		}
		yearOverpaid += over
		if in.allowancePct > 0 && run.allowanceBreach == 0 && yearOverpaid > in.allowancePct*yearStartBal+0.005 {
			run.allowanceBreach = year
		}
		run.totalInterest += interest
		bal -= principal
		run.months++
		if run.months == recordAt {
			run.balanceAt = bal
		}
		if run.months%12 == 0 || bal <= 0.005 {
			age := 0.0
			if in.currentAge >= 0 {
				age = round1(in.currentAge + float64(run.months)/12)
			}
			run.points = append(run.points, MortgagePoint{Age: age, Balance: round2(math.Max(0, bal))})
		}
	}
	if bal > 0.005 {
		run.neverPaysOff = true
	}
	if recordAt > run.months {
		run.balanceAt = math.Max(0, bal)
	}
	return run
}

// clearingPayment is the level monthly payment that clears balance over the n
// calendar months starting at from, with interest accruing daily as in
// simulateMortgage (a rate/12 annuity formula would leave leap-day interest
// outstanding at the end).
func clearingPayment(balance, rate float64, from time.Time, n int) float64 {
	remaining := func(payment float64) float64 {
		b := balance
		for k := 0; k < n; k++ {
			ms := time.Date(from.Year(), from.Month()+time.Month(k), 1, 0, 0, 0, 0, time.UTC)
			days := time.Date(ms.Year(), ms.Month()+1, 0, 0, 0, 0, 0, time.UTC).Day()
			b += b*rate/365*float64(days) - payment
		}
		return b
	}
	lo, hi := 0.0, balance*(1+math.Max(rate, 0))
	for i := 0; i < 100; i++ {
		mid := (lo + hi) / 2
		if remaining(mid) > 0 {
			lo = mid
		} else {
			hi = mid
		}
	}
	return hi
}

func (in mortgageInputs) scenario(key, label string, overpayMonths int) MortgageScenario {
	run := simulateMortgage(in, overpayMonths, 0)
	sc := MortgageScenario{
		Key:           key,
		Label:         label,
		Months:        run.months,
		NeverPaysOff:  run.neverPaysOff,
		TotalInterest: round2(run.totalInterest),
		Points:        run.points,
	}
	if !run.neverPaysOff {
		sc.PayoffDate = in.monthStart(max(0, run.months-1)).Format("2006-01-02")
		if in.currentAge >= 0 {
			sc.PayoffAge = round1(in.currentAge + float64(run.months)/12)
		}
	}
	if overpayMonths > 0 && overpayMonths < run.months && in.currentAge >= 0 {
		sc.OverpayUntilAge = round1(in.currentAge + float64(overpayMonths)/12)
	}
	return sc
}

// projectMortgage fills the computed fields of proj from in.  targetLTVBalance
// and ltvMonths (payments before the fixed-term end) are 0 when not applicable.
func projectMortgage(in mortgageInputs, targetLTVBalance float64, ltvMonths int, proj *MortgageProjection) {
	const forever = 1 << 30
	overpay := in.scenario("overpay", "With overpayment", forever)
	regular := in.scenario("regular", "Regular payments only", 0)

	headline := overpay
	if in.overpayment <= 0 {
		headline = regular
	}
	proj.NeverPaysOff = headline.NeverPaysOff
	proj.ProjectedPayoffAt = headline.PayoffDate
	proj.ProjectedAge = headline.PayoffAge
	proj.TotalInterest = headline.TotalInterest
	proj.TotalInterestWithout = regular.TotalInterest
	if !overpay.NeverPaysOff && !regular.NeverPaysOff {
		proj.InterestSaved = round2(regular.TotalInterest - overpay.TotalInterest)
	}
	if in.targetMonths >= 0 && !headline.NeverPaysOff {
		proj.MonthsAheadBehind = in.targetMonths - headline.Months
		proj.OnTrack = proj.MonthsAheadBehind >= 0
	}

	breach := simulateMortgage(in, forever, 0).allowanceBreach
	if in.overpayment > 0 {
		proj.OverpaymentAllowanceExceededYear = breach
	}
	proj.OverpaymentAllowance = round2(in.allowancePct * in.balance)

	proj.Scenarios = []MortgageScenario{}
	if in.overpayment > 0 {
		proj.Scenarios = append(proj.Scenarios, overpay)
	}

	// "When can I stop overpaying and still clear the mortgage by retirement?"
	// The earliest stop month whose payoff still meets the target.
	if in.overpayment > 0 && in.targetMonths >= 0 && !overpay.NeverPaysOff && overpay.Months <= in.targetMonths {
		for s := 0; s <= overpay.Months; s++ {
			run := simulateMortgage(in, s, 0)
			if !run.neverPaysOff && run.months <= in.targetMonths {
				proj.StopOverpaymentReached = s == 0
				proj.StopOverpaymentDate = in.monthStart(s).Format("2006-01-02")
				if in.currentAge >= 0 {
					proj.StopOverpaymentAge = round1(in.currentAge + float64(s)/12)
				}
				if s > 0 && s < overpay.Months {
					proj.Scenarios = append(proj.Scenarios, in.scenario("stop", "Stop overpaying on plan", s))
				}
				break
			}
		}
	}
	proj.Scenarios = append(proj.Scenarios, regular)

	// "When can I stop overpaying and still reach the target LTV by the
	// fixed-term end?"  Checked with regular payments alone too.
	if targetLTVBalance > 0 && ltvMonths > 0 {
		switch {
		case in.balance <= targetLTVBalance:
			proj.LtvStopOverpaymentReached = true
			proj.LtvAlreadyBelow = true
			proj.LtvStopOverpaymentDate = in.now.Format("2006-01-02")
			if in.currentAge >= 0 {
				proj.LtvStopOverpaymentAge = round1(in.currentAge)
			}
		default:
			maxStop := 0
			if in.overpayment > 0 {
				maxStop = ltvMonths
			}
			for s := 0; s <= maxStop; s++ {
				if simulateMortgage(in, s, ltvMonths).balanceAt <= targetLTVBalance {
					proj.LtvStopOverpaymentReached = s == 0
					proj.LtvStopOverpaymentDate = in.monthStart(s).Format("2006-01-02")
					if in.currentAge >= 0 {
						proj.LtvStopOverpaymentAge = round1(in.currentAge + float64(s)/12)
					}
					break
				}
			}
		}
	}
}
