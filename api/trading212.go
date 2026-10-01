package main

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"math"
	"net/http"
	"time"
)

const trading212BaseURL = "https://live.trading212.com/api/v0"

// t212AccountSummary is GET /equity/account/summary.  Every amount is already
// in the account's primary currency, unlike per-position prices, which are in
// each instrument's own currency (pence for LSE lines, dollars for US stocks).
type t212AccountSummary struct {
	Currency string `json:"currency"`
	Cash     struct {
		AvailableToTrade  float64 `json:"availableToTrade"`
		InPies            float64 `json:"inPies"`
		ReservedForOrders float64 `json:"reservedForOrders"`
	} `json:"cash"`
	Investments struct {
		CurrentValue float64 `json:"currentValue"`
	} `json:"investments"`
	TotalValue float64 `json:"totalValue"`
}

// fetchTrading212Balance returns the whole account value in GBP: all cash
// (free, in pies and reserved for orders) plus the current value of all
// investments.
func fetchTrading212Balance(apiKey string) (float64, error) {
	client := &http.Client{Timeout: 30 * time.Second}

	req, err := http.NewRequest("GET", trading212BaseURL+"/equity/account/summary", nil)
	if err != nil {
		return 0, err
	}
	req.Header.Set("Authorization", "Basic "+base64.StdEncoding.EncodeToString([]byte(apiKey)))

	resp, err := client.Do(req)
	if err != nil {
		return 0, fmt.Errorf("trading212 account summary request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		log.Printf("trading212 account summary error: status=%d body=%s", resp.StatusCode, body)
		return 0, fmt.Errorf("trading212 account summary returned status %d: %s", resp.StatusCode, body)
	}

	var summary t212AccountSummary
	if err := json.NewDecoder(resp.Body).Decode(&summary); err != nil {
		return 0, fmt.Errorf("trading212 account summary decode failed: %w", err)
	}
	if summary.Currency != "GBP" {
		return 0, fmt.Errorf("trading212 account currency is %q; only GBP accounts are supported", summary.Currency)
	}

	total := summary.Cash.AvailableToTrade + summary.Cash.InPies + summary.Cash.ReservedForOrders +
		summary.Investments.CurrentValue
	if summary.TotalValue > 0 && math.Abs(total-summary.TotalValue) > 1 {
		log.Printf("trading212: cash+investments £%.2f differs from totalValue £%.2f", total, summary.TotalValue)
	}
	return total, nil
}
