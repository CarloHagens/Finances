package main

import (
	"encoding/json"
	"errors"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"
	"github.com/jackc/pgx/v5"
)

func (s *Store) handleGetProfile(w http.ResponseWriter, r *http.Request) {
	profile, err := s.GetProfile(r.Context())
	if errors.Is(err, pgx.ErrNoRows) {
		w.WriteHeader(http.StatusNotFound)
		return
	}
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	jsonResponse(w, profile)
}

func (s *Store) handleUpsertProfile(w http.ResponseWriter, r *http.Request) {
	var p UserProfile
	if err := json.NewDecoder(r.Body).Decode(&p); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	if msg := validateProfile(p); msg != "" {
		http.Error(w, msg, http.StatusBadRequest)
		return
	}
	result, err := s.UpsertProfile(r.Context(), p)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	jsonResponse(w, result)
}

func (s *Store) handleListAccounts(w http.ResponseWriter, r *http.Request) {
	accounts, err := s.ListAccounts(r.Context())
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	jsonResponse(w, accounts)
}

func (s *Store) handleCreateAccount(w http.ResponseWriter, r *http.Request) {
	var a Account
	if err := json.NewDecoder(r.Body).Decode(&a); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	if a.Currency == "" {
		a.Currency = "GBP"
	}
	result, err := s.CreateAccount(r.Context(), a)
	if err != nil {
		writeStoreError(w, err)
		return
	}
	w.WriteHeader(http.StatusCreated)
	jsonResponse(w, result)
}

func (s *Store) handleUpdateAccountBalance(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	var req UpdateBalanceRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	result, err := s.UpdateAccountBalance(r.Context(), id, req.Balance)
	if err != nil {
		writeStoreError(w, err)
		return
	}
	jsonResponse(w, result)
}

// handleDeleteAccount archives the account rather than deleting it, so past
// net worth keeps it.
func (s *Store) handleDeleteAccount(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	if err := s.ArchiveAccount(r.Context(), id); err != nil {
		writeStoreError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Store) handleGetAccountHistory(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	entries, err := s.GetAccountHistory(r.Context(), id)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	jsonResponse(w, entries)
}

func (s *Store) handleInsertHistoricalBalance(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	var req HistoricalBalanceRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	if err := s.InsertHistoricalBalance(r.Context(), id, req); err != nil {
		writeStoreError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Store) handleGetNetWorth(w http.ResponseWriter, r *http.Request) {
	summary, err := s.GetNetWorthSummary(r.Context())
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	jsonResponse(w, summary)
}

func (s *Store) handleGetNetWorthHistory(w http.ResponseWriter, r *http.Request) {
	points, err := s.GetNetWorthHistory(r.Context())
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	jsonResponse(w, points)
}

func (s *Store) handleGetMortgageGoal(w http.ResponseWriter, r *http.Request) {
	proj, err := s.ProjectMortgage(r.Context())
	if errors.Is(err, pgx.ErrNoRows) {
		w.WriteHeader(http.StatusNotFound)
		return
	}
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	jsonResponse(w, proj)
}

func (s *Store) handleUpsertMortgageGoal(w http.ResponseWriter, r *http.Request) {
	var g MortgageGoal
	if err := json.NewDecoder(r.Body).Decode(&g); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	if msg := validateMortgageGoal(g); msg != "" {
		http.Error(w, msg, http.StatusBadRequest)
		return
	}
	if _, err := s.UpsertMortgageGoal(r.Context(), g); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	proj, err := s.ProjectMortgage(r.Context())
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	jsonResponse(w, proj)
}

func (s *Store) handleGetPensionGoal(w http.ResponseWriter, r *http.Request) {
	proj, err := s.ProjectPension(r.Context())
	if errors.Is(err, pgx.ErrNoRows) {
		w.WriteHeader(http.StatusNotFound)
		return
	}
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	jsonResponse(w, proj)
}

func (s *Store) handleUpsertPensionGoal(w http.ResponseWriter, r *http.Request) {
	var g PensionGoal
	if err := json.NewDecoder(r.Body).Decode(&g); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	if msg := validatePensionGoal(g); msg != "" {
		http.Error(w, msg, http.StatusBadRequest)
		return
	}
	if _, err := s.UpsertPensionGoal(r.Context(), g); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	proj, err := s.ProjectPension(r.Context())
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	jsonResponse(w, proj)
}

func (s *Store) handleGetIsaBridgeGoal(w http.ResponseWriter, r *http.Request) {
	proj, err := s.ProjectIsaBridge(r.Context())
	if errors.Is(err, pgx.ErrNoRows) {
		w.WriteHeader(http.StatusNotFound)
		return
	}
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	jsonResponse(w, proj)
}

func (s *Store) handleUpsertIsaBridgeGoal(w http.ResponseWriter, r *http.Request) {
	var g IsaBridgeGoal
	if err := json.NewDecoder(r.Body).Decode(&g); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	if msg := validateIsaBridgeGoal(g); msg != "" {
		http.Error(w, msg, http.StatusBadRequest)
		return
	}
	if _, err := s.UpsertIsaBridgeGoal(r.Context(), g); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	proj, err := s.ProjectIsaBridge(r.Context())
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	jsonResponse(w, proj)
}

func (s *Store) handleSyncTrading212(w http.ResponseWriter, r *http.Request) {
	cfg, err := s.GetTrading212Config(r.Context())
	if errors.Is(err, pgx.ErrNoRows) {
		http.Error(w, "trading212 api key not configured", http.StatusBadRequest)
		return
	}
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if cfg.AccountID == nil {
		http.Error(w, "no account linked to trading212 — select one in settings", http.StatusBadRequest)
		return
	}

	balance, err := fetchTrading212Balance(cfg.APIKey)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadGateway)
		return
	}

	if err := s.UpdateTrading212SyncTime(r.Context(), *cfg.AccountID, balance); err != nil {
		writeStoreError(w, err)
		return
	}

	jsonResponse(w, map[string]any{"balance": balance, "account_id": *cfg.AccountID})
}

func (s *Store) handleGetTrading212Config(w http.ResponseWriter, r *http.Request) {
	cfg, err := s.GetTrading212Config(r.Context())
	if errors.Is(err, pgx.ErrNoRows) {
		jsonResponse(w, Trading212Config{})
		return
	}
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	jsonResponse(w, cfg)
}

func (s *Store) handleUpsertTrading212Config(w http.ResponseWriter, r *http.Request) {
	var req UpdateAPIKeyRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	if err := s.UpsertTrading212Config(r.Context(), req); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// writeStoreError maps store errors onto HTTP status codes.
func writeStoreError(w http.ResponseWriter, err error) {
	switch {
	case errors.Is(err, pgx.ErrNoRows):
		http.Error(w, "not found", http.StatusNotFound)
	case errors.Is(err, errNegativeLiability), errors.Is(err, errAccountArchived):
		http.Error(w, err.Error(), http.StatusBadRequest)
	default:
		http.Error(w, err.Error(), http.StatusInternalServerError)
	}
}

func jsonResponse(w http.ResponseWriter, v any) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(v)
}
