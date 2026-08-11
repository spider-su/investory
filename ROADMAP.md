# ROADMAP

Future work based on the current codebase. Completed work is recorded in [`CHANGELOG.md`](CHANGELOG.md).
Items are sized **S** (≤ half day), **M** (1-2 days), and **L** (multi-day). Current financial
contracts live in `docs/domain/`; stable architecture lives in `docs/architecture/`. `AGENTS.md`
only routes coding agents to those sources.

## Next priorities (high impact, low risk)

1. **Finish deterministic golden reconciliation and make it a release gate** _(M)_ - stabilize and freeze the heavily reduced real IBKR/XTB corpus around previously observed edge cases, then make a clean-PostgreSQL rebuild produce an explicit `READY` / `NOT_READY` result. The golden path must remain offline and deterministic: Flyway -> import -> local FX/prices -> projections -> reconciliation -> semantic checkpoints.
2. **PR test job + migration-test recovery** _(M)_ - the existing workflow only builds and publishes the Docker image on `main`, while `SchemaMigrationCheckpoint2Test` is disabled. Add a `mvn test` pull-request job and make the migration chain test reliable enough to gate merges.
3. **Restore authoritative dashboard quality status** _(M)_ - `PortfolioService` currently bypasses reconciliation, data-quality, and risk enrichment. Re-enable them through a cached or on-demand path and add regression tests before presenting the result as trustworthy dashboard state.
4. **Align dashboard profit explanation with the service formula** _(S)_ - the headline is calculated from realized P/L, unrealized P/L, net dividends, and interest, while one dashboard popover describes it as portfolio value minus net deposits. Show the implemented formula and expose any reconciliation difference separately.
5. **Persist `DrawdownAlertRule` peak equity** _(S)_ - currently in-memory and resets on every restart. Store it in a dedicated `notification_state` table; do not restore the removed `portfolio_history` table.
6. **Per-rule alert deduplication** _(S)_ - the same condition can fire every weekday at the 22:22 notification run. Add `last_fired_at` per `AlertRule.code()` and only re-fire after N hours or a state change.

The verification gate is intentionally first: accounting/import changes should not be considered complete until the reduced real-world corpus rebuilds cleanly and reproducibly.

## Theme A - Observability & ops

| Item | Effort | Why |
|---|---|---|
| Actuator + `/actuator/health` (DB, Telegram bot reg, last import age) | S | Container orchestrators (Compose `healthcheck`, K8s probes) need a real signal. |
| Micrometer + Prometheus exporter | M | Quote-fetch latency, FX-refresh failures, rule-fire counts — none are visible today. |
| Structured request logging (correlation id per import batch) | S | Currently logs are flat; tying log lines to `imports.id` makes triage trivial. |
| OpenAPI / Swagger UI (`springdoc-openapi-starter-webmvc-ui`) | S | Replaces the hand-written `src/test/manual/api.http`. ~10 lines + auto-docs. |
| Tighten production Hibernate and request logging | S | Production logging still uses the shared defaults. |

## Theme B - Test hardening

| Item | Effort | Why |
|---|---|---|
| Transport-level fixtures for `TwelveDataService` (`/quote`, `/time_series`, rate-limit 429) | M | Mockito-based `HttpClient` tests already cover parsing and HTTP failures; add WireMock-level request/response contracts and retry behavior. |
| Extend IBKR FIFO edge-case coverage | M | Existing importer tests cover the main statement flows; add focused cases for lot matching across partial fills, forex rows, and synthetic-ID stability. |
| Remove private-fixture dependency from remaining XTB ZIP tests | M | The suite already builds synthetic XLSX workbooks, but several ZIP-path tests still skip when private broker exports are absent. Add synthetic ZIP bundles and remove `Assumptions.assumeTrue`. |
| JaCoCo report (Maven plugin + threshold per package) | S | Surface what's actually covered; today coverage is a guess. |
| Re-enable migration-chain Testcontainers coverage and add a full-context boot test | M | `SchemaMigrationCheckpoint2Test` exists but is disabled, and no test currently boots the full Spring application context against PostgreSQL. |

## Theme C - Notification depth

| Item | Effort | Why |
|---|---|---|
| `notification_event` table + repository (rule_code, message, fired_at, ack_at) | M | Foundation for dedup, history, in-app inbox. |
| "Notifications" tab on dashboard | M | Read from `notification_event`; lets you ack or mute rules from the UI. |
| Mute / snooze per rule (`POST /notifications/rules/{code}/mute`) | S | Pairs with the tab; admin-only. |
| `WeeklyPnlAlertRule` (Friday-only digest of last 5 days) | S | Concrete demo that the SPI is genuinely pluggable. |
| Telegram retry queue + circuit breaker | M | Today a failed Telegram send is just logged. Move to a small in-memory queue with backoff so a flaky network doesn't drop messages. |

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
| Define and enforce read-route authentication policy | S | The current security chain protects `POST`, `PUT`, and `DELETE`, then permits every other request. Decide which dashboard/API reads may be public and require authentication for the rest. |
| Per-user data scoping (add `owner_id` column to all positional tables + Flyway migration) | L | Today every authenticated user sees the same portfolio. Required before exposing the instance to more than one human. |
| CSRF for UI POST routes only | S | CSRF is globally off; enable for the dashboard's import form and keep API routes off (Basic auth). |
| Rate limiting on import endpoints (`bucket4j`) | S | Anyone with admin can DOS the orchestrator with a giant XLSX loop. |

## Theme F - Code health & dependencies

| Item | Effort | Why |
|---|---|---|
| Maven wrapper (`mvnw`) | S | Maven is still an external prerequisite. Vendor and verify the wrapper, then switch CI, Docker, `README.md`, and `AGENTS.md` together. |
| Bind Spotless to `verify` and add a pre-commit hook | S | Formatting is configured but remains optional and can drift between contributors. |
| Bump `telegrambots` 6.9 → 7.x / 10.x (Boot starter, split artifacts) | M | 6.x is no longer maintained; the 7.x+ line ships a Spring Boot starter (`telegrambots-springboot-longpolling-starter`) and splits client/meta into separate jars. This requires reworking `PortfolioBot` to the newer consumer API. |

## Theme G - UX

| Item | Effort | Why |
|---|---|---|
| Full positions workspace (broker/account/currency filters) | M | A sortable open-position popover already exists. Promote it to a persistent table with filters and explicit open/closed scope. |
| Extend the existing asset detail page (`/dashboard/assets/{symbol}`) | M | The page already shows price history, current holdings, closed transactions, and dividends. Add lot-level attribution, broker filters, and an optional technical-analysis snapshot. |
| Align headline profit UI copy with the component calculation | S | Remove the stale “portfolio value − net deposits” explanation or present it only as an independently reconciled check. |
| Restore dashboard reconciliation/data-quality/risk enrichment | M | The service currently skips these calculations unconditionally; make the path performant, tested, and explicit about freshness. |
| Dark-mode toggle | S | Tabler.css already ships a dark variant. |
| "Import progress" SSE stream | M | Long-running IBKR imports today show nothing until they finish. |
| Mobile layout pass | S | The grid breaks below 720px. |

## Theme H - Data verification & reconciliation

The goal is a repeatable proof that imported and derived portfolio data is safe to use, not a collection of manually executed diagnostic SQL. The accounting and tolerance contracts remain in [`docs/quality/reconciliation.md`](docs/quality/reconciliation.md); this section tracks the remaining engineering work.

| Item | Effort | Why |
|---|---|---|
| Finish and freeze the minimal real-broker golden corpus | M | Keep only the smallest real IBKR/XTB rows needed to reproduce known historical edge cases: direct Treasury percent-of-par lifecycle/redemption, IBKR business-date handling, FX/income/tax rows, XTB VHYD rebooking, true inter-account transfer, RESULT_ONLY CFD, IKE cross-currency holdings, and cash-only funding. Preserve broker semantics exactly, record source hashes/provenance, and require explicit review for fixture changes. |
| Standardize reconciliation check results | M | Give every check the same result contract (`check_id`, scope/account/asset/date, expected, actual, difference, tolerance, status, severity, message) so cash, positions, realized result, accounting identities, and reporting checks can share one runner and one report format. |
| Reusable verification runner with `quick`, `golden`, and `archive` modes | M | `quick` audits the current DB, `golden` rebuilds the frozen reduced corpus from an empty PostgreSQL database, and `archive` rebuilds the complete private broker history. All three must execute the same reconciliation rules where their data scope overlaps. |
| Deterministic clean-DB golden gate | M | Start fresh Testcontainers PostgreSQL, run Flyway, import frozen broker fixtures, load only local deterministic FX/price observations, rebuild projections/views, run semantic checkpoints and reconciliation checks, and exit non-zero on any blocker/error. No live market or FX network calls. |
| Full private-archive clean rebuild | L | Periodically verify the complete original broker archive from scratch to catch interactions intentionally absent from the small CI corpus. Source files must be hash-manifested so a changed export cannot silently change the accepted result. |
| Machine + human verification reports | S | Emit concise console status plus JSON/CSV/Markdown details. The summary must identify top residuals and distinguish `BLOCKER`, `ERROR`, `WARNING`, and `INFO`; do not hide failures by widening tolerances. |
| Explicit `READY` / `NOT_READY` contract | S | Define readiness as zero blocker/error reconciliation failures, zero unexplained material residuals, passing golden checkpoints, and passing reporting consistency. Warnings may remain only when their degraded-data semantics are explicit. |
| CI/release integration | M | Run the golden verification on accounting/import/reconciliation changes and require a green result before merge/release. Keep the full private archive outside normal public CI but make it a standard pre-release/local verification command. |
| Regression-corpus growth rule | S | Every newly confirmed production accounting/import bug must land with the smallest real or faithful reduced fixture that reproduces it plus one semantic assertion. This prevents repeated rediscovery of previously fixed edge cases without letting the corpus grow into a copy of the full archive. |

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
- **Yahoo as primary quote source.** Keep Yahoo as an optional CSV-export target rather than the primary quote provider.

## How to use this file

- Each line should land as a small PR. Reference the item in the commit subject when practical.
- When work ships, remove it from this file and record it in `CHANGELOG.md`.
- When work changes a domain contract or stable architecture boundary, update its canonical document.
- Update `AGENTS.md` only when agent workflow or documentation routing changes.
