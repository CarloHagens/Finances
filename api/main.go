package main

import (
	"context"
	"fmt"
	"log"
	"net/http"
	"os"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/go-chi/cors"
	"github.com/jackc/pgx/v5/pgxpool"
)

func main() {
	ctx := context.Background()

	dbURL := os.Getenv("DATABASE_URL")
	if dbURL == "" {
		dbURL = "postgres://finances:finances@localhost:5432/finances"
	}

	db, err := pgxpool.New(ctx, dbURL)
	if err != nil {
		log.Fatalf("failed to connect to database: %v", err)
	}
	defer db.Close()

	if err := runMigrations(ctx, db); err != nil {
		log.Fatalf("migration failed: %v", err)
	}

	store := NewStore(db)
	r := chi.NewRouter()

	r.Use(middleware.Logger)
	r.Use(middleware.Recoverer)
	r.Use(cors.Handler(cors.Options{
		AllowedOrigins: []string{"*"},
		AllowedMethods: []string{"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"},
		AllowedHeaders: []string{"Accept", "Authorization", "Content-Type"},
	}))

	r.Get("/health", func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte("ok"))
	})

	r.Route("/api", func(r chi.Router) {
		r.Get("/profile", store.handleGetProfile)
		r.Put("/profile", store.handleUpsertProfile)

		r.Get("/accounts", store.handleListAccounts)
		r.Post("/accounts", store.handleCreateAccount)
		r.Patch("/accounts/{id}/balance", store.handleUpdateAccountBalance)
		r.Delete("/accounts/{id}", store.handleDeleteAccount)
		r.Get("/accounts/{id}/history", store.handleGetAccountHistory)
		r.Post("/accounts/{id}/history", store.handleInsertHistoricalBalance)

		r.Get("/net-worth", store.handleGetNetWorth)
		r.Get("/net-worth/history", store.handleGetNetWorthHistory)

		r.Get("/goals/mortgage", store.handleGetMortgageGoal)
		r.Put("/goals/mortgage", store.handleUpsertMortgageGoal)

		r.Get("/goals/pension", store.handleGetPensionGoal)
		r.Put("/goals/pension", store.handleUpsertPensionGoal)

		r.Get("/goals/isa-bridge", store.handleGetIsaBridgeGoal)
		r.Put("/goals/isa-bridge", store.handleUpsertIsaBridgeGoal)

		r.Post("/sync/trading212", store.handleSyncTrading212)
		r.Get("/config/trading212", store.handleGetTrading212Config)
		r.Put("/config/trading212", store.handleUpsertTrading212Config)
	})

	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	fmt.Printf("finances-api listening on :%s\n", port)
	log.Fatal(http.ListenAndServe(":"+port, r))
}

func runMigrations(ctx context.Context, db *pgxpool.Pool) error {
	// Create a tracking table so each migration only ever runs once.
	if _, err := db.Exec(ctx, `
		CREATE TABLE IF NOT EXISTS schema_migrations (
			name       TEXT PRIMARY KEY,
			applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
		)`); err != nil {
		return fmt.Errorf("creating schema_migrations: %w", err)
	}

	migrations := []string{
		"001_initial.sql",
		"002_trading212_account.sql",
		"003_balance_history.sql",
		"004_income_inflation.sql",
		"005_min_contrib_coast.sql",
		"006_glidepath.sql",
		"007_pension_partner_pension.sql",
		// 008_reseed_balance_history.sql removed — one-shot cleanup already applied;
		// keeping it here would wipe account_balance_history on every restart.
		"009_mortgage_ltv.sql",
		"010_pension_own_state_pension.sql",
		"011_audit_fixes.sql",
	}

	for _, name := range migrations {
		var applied bool
		_ = db.QueryRow(ctx, `SELECT EXISTS(SELECT 1 FROM schema_migrations WHERE name = $1)`, name).Scan(&applied)
		if applied {
			continue
		}

		var data []byte
		var readErr error
		for _, base := range []string{"migrations/", "/migrations/"} {
			data, readErr = os.ReadFile(base + name)
			if readErr == nil {
				break
			}
		}
		if readErr != nil {
			return fmt.Errorf("%s not found: %w", name, readErr)
		}
		if _, err := db.Exec(ctx, string(data)); err != nil {
			return fmt.Errorf("%s failed: %w", name, err)
		}
		if _, err := db.Exec(ctx, `INSERT INTO schema_migrations (name) VALUES ($1)`, name); err != nil {
			return fmt.Errorf("recording %s: %w", name, err)
		}
		log.Printf("applied migration: %s", name)
	}
	return nil
}
