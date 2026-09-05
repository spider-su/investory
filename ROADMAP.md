# ROADMAP

Future work based on the current codebase. Completed work is recorded in [`CHANGELOG.md`](CHANGELOG.md).
Items are sized **S** (≤ half day), **M** (1-2 days), and **L** (multi-day). Current financial
contracts live in `docs/domain/`; stable architecture lives in `docs/architecture/`. `AGENTS.md`
only routes coding agents to those sources.

## TODO priority queue

- [ ] **P0 - Secure wider deployment:** add per-user data isolation, UI CSRF, and import rate limiting.
- [ ] **P1 - Make portfolio scope consistent:** complete the portfolio-scoped dashboard read model,
  portfolio-scoped planning aggregation, and remaining Long-Term database constraints.
- [ ] **P1 - Make reconciliation operationally durable:** add refresh status/retry/stale visibility,
  archive-manifest evidence, private ARCHIVE integration, separate execution and coverage status, and
  require the golden CI gate before release.
- [ ] **P1 - Reduce remaining MV refresh cost:** investigate `recon_v_reconstructed_cash_daily_mv`
  (~53 s), `app_v_normalized_cash_operations` (~32 s), `recon_v_reconstructed_position_daily_mv`
  (~25 s), and `recon_v_account_daily_reconciliation_mv` (~18 s) using the tracked performance
  baseline; preserve exact reconciliation output and refresh-order contracts.
- [ ] **P1 - Complete durable notification transitions:** move drawdown, concentration, and stale-import
  rules onto persisted threshold/recovery state; add maturity and contract-expiry producers.
- [ ] **P2 - Reduce CI cost:** avoid rebuilding the complete Flyway chain before every
  `SchemaMigrationCheckpoint2IT` method without weakening disposable-database isolation.
- [ ] **P2 - Finish code-health migrations:** remove null-based dashboard construction, bound broad
  Investment reads, finish immutable monetary API models, remove the plan-editor compatibility lookup,
  and cover refresh rollback behavior.
- [ ] **P2 - Add operational visibility:** expose Prometheus metrics, correlation identifiers, and
  production logging controls.
- [ ] **P3 - Extend notification and import features:** add recovery replay/mute UI, more broker parsers,
  IBKR NAV/FlexQuery ingestion, overlap-idempotency proof, and import validation reports.
- [ ] **P3 - Finish dashboard UX:** align profit copy, add the positions workspace, extend asset detail,
  stream import progress, restore performant optional enrichment, and complete the mobile layout pass.

Detailed acceptance scope and effort follow below. Explicitly deferred work is not part of this TODO
queue.

The verification gate is intentionally first: accounting/import changes should not be considered complete until the reduced real-world corpus rebuilds cleanly and reproducibly.

## Planning follow-ups

The implemented long-term assets, deterministic retirement simulation, Reserve + Harvest policy, manual
cash reserve, planning display currency, and Actual/Live/Projected timeline are no longer roadmap work.
Reviewed Long-Term baselines are immutable snapshots; deposits, rental-contract rollover, archived
asset reactivation, and subtype-specific creation are supported.

| Item | Effort | Why |
|---|---|---|
| Complete Long-Term database integrity constraints | S | The current Flyway chain ends at `V01.008` and enforces rental-contract subtype consistency. Add any remaining cross-table subtype and lifecycle constraints through a new append-only migration, with matching snapshot and migration-contract coverage. |
| Portfolio-scoped market aggregation for planning | M | `InvestmentProfile` currently combines portfolio-scoped manual assets with shared market aggregation; make multi-portfolio planning semantics explicit and safe. |
| Richer Live-year tracking and longer actual-versus-plan history | M | The current annual baseline/timeline is intentionally compact. Add reliable flow and strategy tracking without turning planning into transaction budgeting. |
| Assumption calibration from closed years | M | Use approved historical planning data as an optional review input; do not silently alter assumptions. |
| Monte Carlo / sequence-risk model | L | Keep separate from the deterministic annual planning contract. |
| Explicit real-estate sale strategy | M | Current simulation intentionally never sells real estate automatically. |

## Theme A - Observability & ops

| Item | Effort | Why |
|---|---|---|
| Micrometer + Prometheus exporter | M | Quote-fetch latency, FX-refresh failures, rule-fire counts — none are visible today. |
| Structured request logging (correlation id per import batch) | S | Currently logs are flat; tying log lines to `imports.id` makes triage trivial. |
| Tighten production Hibernate and request logging | S | Production logging still uses the shared defaults. |

## Theme B - Test hardening

| Item | Effort | Why |
|---|---|---|
| Extend IBKR FIFO edge-case coverage | M | Existing importer tests cover the main statement flows; add focused cases for lot matching across partial fills, forex rows, and synthetic-ID stability. |
| Add calibrated JaCoCo thresholds per package | S | Per-module reports now exist; set package floors only after reviewing generated-code and adapter exclusions so the gate rewards behavior coverage instead of ceremonial tests. |
| Reduce migration-contract test setup cost | M | `SchemaMigrationCheckpoint2IT` currently cleans and reapplies the complete Flyway chain before each test; keep destructive-test isolation while sharing or grouping setup so the schema CI leg does not dominate the gate. |

## Theme C - Notification depth

| Item | Effort | Why |
|---|---|---|
| "Notifications" tab on dashboard | M | Read from `notification_event`; lets you ack or mute rules from the UI. |
| Mute / snooze per rule (`POST /notifications/rules/{code}/mute`) | S | Pairs with the tab; admin-only. |
| Replace daily digest with weekly portfolio digest | S | Keep routine movement out of immediate alerts; include income, drawdown, concentration, upcoming obligations, plan status, warnings, and tax estimate. |
| Recovery notifications and operator replay | M | Persist recovery for P0/P1 state transitions and provide a safe replay path for `EXHAUSTED` events. |
| Remaining action rules | M | Stuck imports, stale price/FX data, annual plan review, and material live-versus-baseline deviation. |

## Theme D - Import depth

| Item | Effort | Why |
|---|---|---|
| Additional `BrokerImportParser` impls (Revolut, Trade Republic, DEGIRO) | M each | SPI is ready; each broker is essentially a column-mapping plus FIFO match. |
| IBKR FlexQuery scheduled pull (no more manual CSV upload) | L | Replaces the Telegram/UI upload for IBKR with a `@Scheduled` job hitting IBKR's auth-token endpoint. |
| IBKR Net Asset Value/account-summary import | M | Open Positions are supported, but Net Asset Value sections are not parsed. Add explicit NAV ingestion and reconcile it against reconstructed account equity rather than silently overriding canonical projections. |
| Harden and prove partial-overlap idempotency | M | Exact files reuse the existing audit batch and are reprocessed. Overlapping non-identical exports rely on stable broker or synthetic row IDs, but that guarantee is not formally tested across changed export windows. |
| Validation report attached to `ImportBatch` (sheet-level row totals, currency totals, gaps) | S | Helps the user trust the import. |

## Theme E - Security & multi-user

| Item | Effort | Why |
|---|---|---|
| Per-user data scoping (add `owner_id` column to all positional tables + Flyway migration) | L | Today every authenticated user sees the same portfolio. Required before exposing the instance to more than one human. |
| CSRF for UI POST routes only | S | CSRF is globally off; enable for the dashboard's import form and keep API routes off (Basic auth). |
| Rate limiting on import endpoints (`bucket4j`) | S | Anyone with admin can DOS the orchestrator with a giant XLSX loop. |

## Theme F - Code health & dependencies

| Item | Effort | Why |
|---|---|---|
| Database module isolation | L | Split core, Investment, Long-Term, and Retirement persistence into explicit PostgreSQL schemas with producer-owned read views and optional role grants. This is separate from the current Maven modularization. |
| Portfolio-scoped dashboard read model | L | **TODO.** Replace the mixed system-wide `PortfolioMetricsService` plus portfolio-specific canonical sections with one consistently portfolio-scoped dashboard read path. |
| Finish immutable Investment API model migration | M | Canonical reporting APIs now use immutable records and `BigDecimal`; finish the remaining asset-detail compatibility models that still expose `double`/`Double`, and keep primitive conversion at the Web UI/chart boundary. |
| Remove null-based dashboard test construction | S | **TODO.** Remove legacy `InvestmentDashboardFacade` overloads that inject `null`; use explicit test fixtures around the single production constructor. |
| Bound Investment reporting and projection reads | M | **TODO.** Replace remaining broad `findAll()` calls in cash, allocation, account, position, and price paths with portfolio/date/active predicates or database aggregates. |
| Complete PostgreSQL refresh-function failure coverage | S | Disposable-PostgreSQL tests cover refresh order, separation, queryability, and rounding boundaries. Add explicit transaction rollback/failure behavior around the `AccountDailyRepository` refresh calls. |
| Optimize remaining large MV refreshes | M | TODO. Current complete baseline is ~194 s for all MVs. Profile and optimize reconstructed cash, normalized cash, reconstructed position daily, and account daily reconciliation in that order; compare exact rows/columns before each migration. |
| Review and refactor the golden dashboard UI test | M | TODO. The test currently spends its remaining time in FX preload/account rebuild before browser assertions; profile the range resolver, cache fill, and account rebuild separately, then reduce preparation cost without weakening dashboard-value coverage. |
| Finish the typed retirement plan-editor boundary | S | HTML form parsing now lives in the Web UI `SimulationRequestMapper`, and `PlanEditorInput` is typed. Remove its remaining string-key compatibility view (`value(String)`) after the last MVC caller is migrated, preserving percentage-point, currency, null/fallback, and expense-stage tests. |
| Consolidate long-term application orchestration after the POC freeze | L | Post-POC code-structure TODO: (1) consolidate the duplicated `LongTermAssetsApplicationService` / `LongTermAssetsFacade` layers into one composition-based public API with explicit model mappers; (2) relocate `SimulationPlanService` to an application/persistence orchestration package; (3) standardize API model package conventions; (4) break oversized calculation/orchestration classes along existing responsibilities, including `PortfolioProjectionService`, `PortfolioMetricsService`, and `PlanningTimelineFacade`; and (5) remove compatibility constructors and presentation bridges, including the deprecated `SimulationPlanService` constructor and `PlanningPresentation` forwarding bridge. This is planned technical debt and is not a POC blocker; schedule only when there is sufficient regression time. |
| Bind Spotless to `verify` and add a pre-commit hook | S | Formatting is configured but remains optional and can drift between contributors. |
| Bump `telegrambots` 6.9 → 7.x / 10.x (Boot starter, split artifacts) | M | 6.x is no longer maintained; the 7.x+ line ships a Spring Boot starter (`telegrambots-springboot-longpolling-starter`) and splits client/meta into separate jars. This requires reworking `PortfolioBot` to the newer consumer API. |

## Theme G - UX

| Item | Effort | Why |
|---|---|---|
| Full positions workspace (broker/account/currency filters) | M | A sortable open-position popover already exists. Promote it to a persistent table with filters and explicit open/closed scope. |
| Extend the existing asset detail page (`/dashboard/assets/{symbol}`) | M | The page already shows price history, current holdings, closed transactions, and dividends. Add lot-level attribution, broker filters, and an optional technical-analysis snapshot. |
| Align headline profit UI copy with the component calculation | S | Remove the stale “portfolio value − net deposits” explanation or present it only as an independently reconciled check. |
| Restore dashboard reconciliation/data-quality/risk enrichment | M | Database diagnostics are implemented. The normal dashboard path keeps this expensive enrichment opt-in/on-demand until a performant, tested freshness strategy is available. |
| "Import progress" SSE stream | M | Long-running IBKR imports today show nothing until they finish. |
| Mobile layout pass | S | The grid breaks below 720px. |

## Theme H - Data verification & reconciliation

The application reconciliation page now executes C0-C7 from reusable checks backed by persisted
import, ledger, position, valuation, account-daily, reporting, dashboard-fallback, and Yahoo-export
evidence. The current-state report still cannot prove external archive completeness without a
manifest and remains `REVIEW` until all required checkpoint and coverage evidence is available.

The goal is a repeatable proof that imported and derived portfolio data is safe to use, not a collection of manually executed diagnostic SQL. The accounting and tolerance contracts remain in [`docs/quality/reconciliation.md`](docs/quality/reconciliation.md); this section tracks the remaining engineering work.

| Reconciliation follow-up | Status |
|---|---|
| Add archive-manifest completeness evidence | Planned |
| Integrate private ARCHIVE mode with the typed check/report engine used by current-state and GOLDEN validation | Planned |
| Separate reconciliation execution status from archive-coverage status in the public report | TODO |
| Add durable post-import reconciliation refresh status, retry, and stale-report visibility | TODO |
| Move application reconciliation SQL from Java text blocks into versioned database evidence views/functions | TODO |
| Replace the native reconciliation DAO's entity-backed Spring Repository abstraction with a focused JDBC adapter | TODO |
| Measure C1 and full-report latency with representative data and query plans before performance tuning | TODO |

| Item | Effort | Why |
|---|---|---|
| Full private-archive clean rebuild | L | Periodically verify the complete original broker archive from scratch to catch interactions intentionally absent from the small CI corpus. Source files must be hash-manifested so a changed export cannot silently change the accepted result. |
| Machine + human verification reports | S | Extend the existing JSON/console readiness output if needed so all release checks identify top residuals and distinguish `BLOCKER`, `ERROR`, `WARNING`, and `INFO`; do not hide failures by widening tolerances. |
| CI/release integration | M | The golden job exists; require its green result before merge/release through repository branch protection and keep the full private archive outside normal public CI as a standard pre-release/local verification command. |

Target end state:

```text
same check engine
  ├─ quick   -> current DB
  ├─ golden  -> frozen minimal corpus + clean DB
  └─ archive -> complete private broker history + clean DB

all modes -> standardized reconciliation report -> READY / NOT_READY
```

## Explicitly deferred

- **Real-time market data (websocket).** TwelveData REST polling is sufficient for daily snapshots; sockets add complexity without clear payoff.
- **Migrating off Telegram.** Slack/Discord adapters are easier after `NotificationService` supports multiple channels.
- **Replacing JPA with R2DBC / WebFlux.** There is no throughput problem to solve.
- **Yahoo as primary quote source.** Do not promote Yahoo from fallback quote provider to the primary market-data provider.

## How to use this file

- Each line should land as a small PR. Reference the item in the commit subject when practical.
- When work ships, remove it from this file and record it in `CHANGELOG.md`.
- When work changes a domain contract or stable architecture boundary, update its canonical document.
- Update `AGENTS.md` only when agent workflow or documentation routing changes.
