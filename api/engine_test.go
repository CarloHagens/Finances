package main

import (
	"math"
	"testing"
	"time"
)

var testNow = time.Date(2026, time.October, 1, 0, 0, 0, 0, time.UTC)

func near(t *testing.T, name string, got, want, tol float64) {
	t.Helper()
	if math.Abs(got-want) > tol {
		t.Errorf("%s = %.6f, want %.6f (±%g)", name, got, want, tol)
	}
}

func TestMonthlyRateCompoundsToAnnual(t *testing.T) {
	for _, r := range []float64{0, 0.04, 0.07, -0.02} {
		near(t, "12 months", math.Pow(1+monthlyRate(r), 12), 1+r, 1e-12)
	}
}

func TestIncomeTax2026(t *testing.T) {
	cases := []struct{ income, tax float64 }{
		{12570, 0},
		{50270, 7540},
		{100000, 27432},
		{110000, 33432}, // allowance tapered to £7,570
		{125140, 42516}, // allowance fully withdrawn
		{150000, 53703}, // additional rate
	}
	for _, c := range cases {
		near(t, "tax", incomeTax(c.income, 1), c.tax, 0.01)
	}
	// Bands scale with bandFactor; the £100k taper threshold does not.
	near(t, "scaled PA", incomeTax(12570*1.2, 1.2), 0, 1e-9)
}

func TestDrawdownGrossRoundTrip(t *testing.T) {
	for _, net := range []float64{0, 5000, 20000, 45000, 90000, 150000} {
		for _, prior := range []float64{0, 12548, 20000, 40000} {
			for _, cap := range []float64{math.Inf(1), 0, 3000} {
				for _, bf := range []float64{1, 1.4} {
					g := drawdownGross(net-prior, bf, prior, cap)
					taxFree := math.Min(pclsFraction*g, cap)
					got := prior + g - incomeTax(prior+g-taxFree, bf)
					if g > 0 {
						near(t, "household net", got, net, 1e-6)
					} else if got < net-1e-6 {
						t.Errorf("gross 0 but household net %.2f < %.2f", got, net)
					}
				}
			}
		}
	}
}

func TestStatePensionTaxIsFunded(t *testing.T) {
	// State pension above the allowance and target exactly equal to it: the
	// drawdown must still cover the tax on the state pension.
	sp := 14000.0
	g := drawdownGross(0, 1, sp, math.Inf(1))
	if g <= 0 {
		t.Fatalf("expected a withdrawal to pay tax on the state pension, got %v", g)
	}
	near(t, "net", sp+g-incomeTax(sp+0.75*g, 1), sp, 1e-6)
}

func TestBandFactorFrozenUntil2031(t *testing.T) {
	tl := timeline{now: testNow, inflation: 0.03}
	near(t, "2027", tl.bandFactor(12), 1, 1e-12)
	near(t, "2031-03", tl.bandFactor(53), 1, 1e-12)
	// 10 years out: frozen for ~4.51 years, then 3% a year.
	frozen := yearsBetween(testNow, taxBandsFrozenUntil)
	near(t, "2036", tl.bandFactor(120), math.Pow(1.03, 10-frozen), 1e-9)
}

func TestStatePensionAge(t *testing.T) {
	d := func(y int, m time.Month, day int) time.Time { return time.Date(y, m, day, 0, 0, 0, 0, time.UTC) }
	near(t, "1970", statePensionAge(d(1970, time.June, 15)), 67, 0)
	near(t, "1990", statePensionAge(d(1990, time.January, 1)), 68, 0)
	near(t, "1958", statePensionAge(d(1958, time.June, 1)), 66, 0)
	near(t, "May 1960", statePensionAge(d(1960, time.May, 10)), 66+2.0/12, 1e-9)
}

func TestPartnerPensionTiming(t *testing.T) {
	h := household{partnerPensionMonthly: 1045, partnerAgeNow: 51, partnerSPAge: 67, partnerPensionEndAge: 95}
	if h.partnerPensionPaid(0) {
		t.Error("partner aged 51 should not be paid yet")
	}
	if !h.partnerPensionPaid(17 * 12) {
		t.Error("partner aged 68 should be paid")
	}
	if h.partnerPensionPaid(44 * 12) {
		t.Error("partner aged 95 should have stopped")
	}
	unknown := household{partnerPensionMonthly: 1045, partnerAgeNow: -1}
	if !unknown.partnerPensionPaid(0) {
		t.Error("unknown partner age should assume in payment")
	}
}

func basePension() pensionInputs {
	return pensionInputs{
		now:                 testNow,
		currentAge:          37.5,
		currentValue:        150000,
		monthlyContribution: 2500,
		minMonthly:          400,
		growthRate:          0.07,
		glidepathRate:       0.04,
		glidepathYears:      5,
		inflation:           0.03,
		stopAge:             53,
		drawAge:             58,
		ownSPMonthly:        1045.63,
		ownSPAge:            68,
		hh: household{
			targetMonthly:         5000,
			partnerPensionMonthly: 1045.63,
			partnerAgeNow:         -1,
		},
	}
}

// A pot of exactly the "pot needed" lasts to 100 and no further.
func TestPensionPotNeededDepletesAt100(t *testing.T) {
	in := basePension()
	in.currentAge, in.stopAge, in.drawAge = 58, 58, 58 // already drawing: projected = current value
	var first PensionProjection
	projectPension(in, &first)

	in.currentValue = first.InflatedTarget + 0.01 // undo rounding to the penny
	var exact PensionProjection
	projectPension(in, &exact)
	if exact.PotLastsToAge != 0 {
		t.Errorf("pot of exactly the target ran out at %.1f", exact.PotLastsToAge)
	}
	last := exact.Schedule[len(exact.Schedule)-1]
	near(t, "balance at 100", last.Balance, 0, 1)

	in.currentValue = first.InflatedTarget * 0.97
	var short PensionProjection
	projectPension(in, &short)
	if short.PotLastsToAge <= 58 || short.PotLastsToAge >= 100 {
		t.Errorf("3%% short pot should run out before 100, got %.1f", short.PotLastsToAge)
	}
}

// Past the stop age, only the months actually left until draw age count.
func TestPensionPastStopAge(t *testing.T) {
	in := basePension()
	in.currentAge, in.currentValue = 55, 500000
	var p PensionProjection
	projectPension(in, &p)
	// 36 months to 58, all inside the 5-year glidepath at 4%.
	near(t, "projected", p.ProjectedAtDraw, 500000*math.Pow(1.04, 3), 1)
	near(t, "no-contrib equals full", p.ProjectedAtDrawNoContrib, p.ProjectedAtDraw, 0.01)
}

// Starting from the coast number and contributing nothing reaches the pot
// needed exactly at draw age.
func TestCoastNumberReachesTarget(t *testing.T) {
	in := basePension()
	var p PensionProjection
	projectPension(in, &p)

	monthsToDraw := monthsFromYears(float64(in.drawAge) - in.currentAge)
	glideStart := monthsToDraw - in.glidepathYears*12
	v := p.CoastFireNumber
	for m := 0; m < monthsToDraw; m++ {
		r := monthlyRate(in.growthRate)
		if m >= glideStart {
			r = monthlyRate(in.glidepathRate)
		}
		v *= 1 + r
	}
	near(t, "coast grows to target", v, p.InflatedTarget, 0.05)
}

func TestLumpSumCapCrossedOnce(t *testing.T) {
	in := basePension()
	in.hh.targetMonthly = 8000
	var p PensionProjection
	projectPension(in, &p)
	if p.LumpSumCapAge <= 58 || p.LumpSumCapAge >= 100 {
		t.Errorf("expected the tax-free cap to run out in retirement, got %.1f", p.LumpSumCapAge)
	}
}

func TestIsaBridgeRequiredDepletesAtEnd(t *testing.T) {
	in := isaInputs{
		now: testNow, currentAge: 53, growthRate: 0.07, glidepathRate: 0.04, glidepathYears: 5,
		inflation: 0.03, startAge: 53, endAge: 58,
		hh: household{targetMonthly: 3000, partnerPensionMonthly: 1045.63, partnerAgeNow: 70, partnerSPAge: 67},
	}
	var first IsaBridgeProjection
	projectIsaBridge(in, &first)

	in.currentValue = first.RequiredAtRetirement + 0.01 // undo rounding to the penny
	var exact IsaBridgeProjection
	projectIsaBridge(in, &exact)
	if !exact.SurvivesToBridgeEnd {
		t.Errorf("pot of exactly the requirement ran out at %.1f", exact.RunsOutAge)
	}
	near(t, "balance at bridge end", exact.Schedule[len(exact.Schedule)-1].Balance, 0, 1)

	// Withdrawals rise with inflation, so more is needed than a flat stream.
	w := first.InflatedMonthlyWithdrawal
	r := monthlyRate(0.04)
	flat := w * (1 - math.Pow(1+r, -60)) / r
	if first.RequiredAtRetirement <= flat {
		t.Errorf("required %.0f should exceed the flat-withdrawal value %.0f", first.RequiredAtRetirement, flat)
	}

	in.currentValue = first.RequiredAtRetirement * 0.95
	var short IsaBridgeProjection
	projectIsaBridge(in, &short)
	if short.SurvivesToBridgeEnd || short.MonthlyShortfall <= 0 {
		t.Error("5% short pot should run out with a shortfall")
	}
}

func TestIsaBridgeInsideBridgeUsesRemainingMonths(t *testing.T) {
	base := isaInputs{
		now: testNow, growthRate: 0.07, glidepathRate: 0.04, glidepathYears: 5,
		inflation: 0, startAge: 53, endAge: 58, hh: household{targetMonthly: 3000, partnerAgeNow: -1},
	}
	at55 := base
	at55.currentAge = 55
	var p IsaBridgeProjection
	projectIsaBridge(at55, &p)
	r := monthlyRate(0.04)
	near(t, "36 months remaining", p.RequiredAtRetirement, 3000*(1-math.Pow(1+r, -36))/r, 0.01)
}

func baseMortgage() mortgageInputs {
	return mortgageInputs{
		now: testNow, currentAge: 37.5, balance: 250000, payment: 1400, overpayment: 500,
		rate: 0.0429, followOnRate: 0.0429, allowancePct: 0.10, targetMonths: 186,
	}
}

func TestMortgageNeverPaysOff(t *testing.T) {
	in := baseMortgage()
	in.payment, in.overpayment = 800, 0
	var p MortgageProjection
	projectMortgage(in, 0, 0, &p)
	if !p.NeverPaysOff || p.OnTrack || p.ProjectedPayoffAt != "" {
		t.Errorf("interest-only payment must never pay off: %+v", p.Scenarios)
	}
}

func TestMortgageZeroRate(t *testing.T) {
	in := baseMortgage()
	in.balance, in.payment, in.overpayment, in.rate = 1200, 100, 0, 0
	run := simulateMortgage(in, 0, 0)
	if run.months != 12 || run.totalInterest != 0 {
		t.Errorf("got %d months, £%.2f interest", run.months, run.totalInterest)
	}
}

func TestMortgageMonthStartFrom31st(t *testing.T) {
	in := baseMortgage()
	in.now = time.Date(2026, time.October, 31, 0, 0, 0, 0, time.UTC)
	if got := in.monthStart(1); got.Month() != time.November {
		t.Errorf("month after 31 Oct should be November, got %v", got)
	}
	a := simulateMortgage(in, 0, 0)
	in.now = testNow
	b := simulateMortgage(in, 0, 0)
	if a.months != b.months || math.Abs(a.totalInterest-b.totalInterest) > 0.01 {
		t.Errorf("1st vs 31st of the month differ: %d/%.2f vs %d/%.2f", b.months, b.totalInterest, a.months, a.totalInterest)
	}
}

func TestMortgageRecalculatesAtRemortgage(t *testing.T) {
	in := baseMortgage()
	in.overpayment = 0
	in.fixedTermEnd = time.Date(2028, time.December, 31, 0, 0, 0, 0, time.UTC)
	in.termEnd = time.Date(2050, time.December, 1, 0, 0, 0, 0, time.UTC)
	in.followOnRate = 0.055
	run := simulateMortgage(in, 0, 0)
	// Payments run Oct 2026 … Dec 2050 inclusive.
	want := (2050-2026)*12 + int(time.December) - int(time.October) + 1
	if run.months != want {
		t.Errorf("recalculated payment should clear by the term end: %d months, want %d", run.months, want)
	}
}

func TestMortgageStopOverpayingMeetsTarget(t *testing.T) {
	in := baseMortgage()
	in.targetMonths = 220
	var p MortgageProjection
	projectMortgage(in, 0, 0, &p)
	if p.StopOverpaymentDate == "" {
		t.Fatal("expected a stop-overpaying date")
	}
	var stop *MortgageScenario
	for i := range p.Scenarios {
		if p.Scenarios[i].Key == "stop" {
			stop = &p.Scenarios[i]
		}
	}
	if stop == nil || stop.Months > in.targetMonths {
		t.Errorf("stop scenario must still meet the target: %+v", stop)
	}
}

func TestMortgageOverpaymentAllowance(t *testing.T) {
	in := baseMortgage()
	in.balance, in.overpayment = 200000, 2000 // £24k a year against a £20k allowance
	var p MortgageProjection
	projectMortgage(in, 0, 0, &p)
	if p.OverpaymentAllowanceExceededYear == 0 {
		t.Error("expected the 10% allowance to be exceeded")
	}
}

func TestMortgageLtvWithRegularPaymentsOnly(t *testing.T) {
	in := baseMortgage()
	in.balance, in.overpayment = 182000, 0
	var p MortgageProjection
	projectMortgage(in, 180000, 12, &p)
	if !p.LtvStopOverpaymentReached {
		t.Error("regular payments alone reach the LTV target within 12 months")
	}
}
