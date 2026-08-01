# ROADMAP

Living plan of follow-ups based on the current state of the codebase (post Boot 4.1 + Java 25 upgrade, June 2026). Items are sized **S** (≤ half day), **M** (1-2 days), **L** (multi-day). Tackle items in any order, but the next-priorities list is the recommended sequence. Current architecture and invariants live in `AGENTS.md`.

## Next priorities (high impact, low risk)

1. **Persist `DrawdownAlertRule` peak equity** _(S)_ - currently in-memory and resets on every restart. Store it in a dedicated `notification_state` table; do not restore the removed `portfolio_history` table.
2. **Per-rule alert deduplication** _(S)_ - the same condition can fire every weekday at the 22:22 notification run. Add `last_fired_at` per `AlertRule.code()` and only re-fire after N hours or a state change.
3. **Spring Boot Actuator + healthcheck** _(S)_ - add `spring-boot-starter-actuator`, expose `/actuator/health` (DB + Telegram + import-staleness), wire into Docker `HEALTHCHECK`.
4. **Test job in GitHub Actions** _(S)_ - the existing workflow only builds the Docker image. Add a `mvn test` job on pull requests so the suite gates merges.
5. **Maven wrapper (`mvnw`)** _(S)_ - vendor and verify the wrapper, then switch CI, Docker, `README.md`, and the build instructions in `AGENTS.md` together. Until that change lands, Maven must be installed and invoked directly.

That's roughly 2-3 days of focused work and clears most of the operational rough edges.

> After pulling the Boot 4.1 upgrade, trigger **Reload All Maven Projects** in IntelliJ so it fetches the two new artifacts pinned in `<dependencyManagement>` (`poi-ooxml:5.4.1`, `commons-beanutils:1.11.0`).

## Theme A - Observability & ops

| Item | Effort | Why |
|---|---|---|
| Actuator + `/actuator/health` (DB, Telegram bot reg, last import age) | S | Container orchestrators (Compose `healthcheck`, K8s probes) need a real signal. |
| Micrometer + Prometheus exporter | M | Quote-fetch latency, FX-refresh failures, rule-fire counts — none are visible today. |
| Structured request logging (correlation id per import batch) | S | Currently logs are flat; tying log lines to `imports.id` makes triage trivial. |
| OpenAPI / Swagger UI (`springdoc-openapi-starter-webmvc-ui`) | S | Replaces the hand-written `src/test/manual/api.http`. ~10 lines + auto-docs. |
| Dedicated `application-prod.yml` (disable devtools, force `notifications.enabled=true`, tighten Hibernate logging) | S | Today a prod run inherits dev defaults. |

## Theme B - Test hardening

| Item | Effort | Why |
|---|---|---|
| WireMock fixtures for `TwelveDataService` (`/quote`, `/time_series`, rate-limit 429) | M | The most fragile external dependency has zero tests. |
| Extend IBKR FIFO edge-case coverage | M | Existing importer tests cover the main statement flows; add focused cases for lot matching across partial fills, forex rows, and synthetic-ID stability. |
| Synthetic XLSX builder for `XtbImportHistoryV2ServiceTest` | M | Several fixture tests skip when private broker exports are absent. An Apache POI helper can generate the minimal sheet shapes and remove `Assumptions.assumeTrue`. |
| JaCoCo report (Maven plugin + threshold per package) | S | Surface what's actually covered; today coverage is a guess. |
| Spring Boot integration test against Testcontainers Postgres | M | Catches Flyway migration drift before deploy. Today no test boots the full context. |

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
| Idempotent partial re-imports (overlap windows) | M | The SHA-256 check covers full duplicates; overlapping date ranges between two exports double-count today unless you reset. |
| Validation report attached to `ImportBatch` (sheet-level row totals, currency totals, gaps) | S | Helps the user trust the import. |

## Theme E - Security & multi-user

| Item | Effort | Why |
|---|---|---|
| ~~Activate or remove the OAuth2 Google config~~ | done | The dead YAML stub is gone from the default and local configs. Drift removed. |
| Per-user data scoping (add `owner_id` column to all positional tables + Flyway migration) | L | Today every authenticated user sees the same portfolio. Required before exposing the instance to more than one human. |
| CSRF for UI POST routes only | S | CSRF is globally off; enable for the dashboard's import form and keep API routes off (Basic auth). |
| Rate limiting on import endpoints (`bucket4j`) | S | Anyone with admin can DOS the orchestrator with a giant XLSX loop. |
| ~~Externalise secrets to env / Docker secrets, not yaml defaults~~ | done | `application-prod.yml` now requires explicit `APP_SECURITY_*` overrides and disables devtools in prod. |

## Theme F - Code health & dependencies

| Item | Effort | Why |
|---|---|---|
| ~~Upgrade Spring Boot 3.2.3 → 3.5.x~~ → **Spring Boot 4.1.0 + Spring Cloud 2025.1.2** | done | Java 25, Spring Security 7 idioms (`Customizer.withDefaults`, `AbstractHttpConfigurer::disable`), `@MockBean` → `@MockitoBean` across the suite. |
| ~~Upgrade Lombok 1.18.30 → latest~~ | done | Now BOM-managed (Boot 4.1 pins Lombok). |
| ~~Spotless (google-java-format)~~ | partly done | Configured (`mvn spotless:apply` / `spotless:check`), not yet bound to a build phase and no pre-commit hook. Bind to `verify` once the codebase is reformatted once. |
| ~~Extract `TaxCalculator` and `CashFlowAggregator` out of `PortfolioService`~~ | done | Both extracted as Spring components with unit tests; `calculateTotalProfitLoss()` is now a thin orchestrator. |
| ~~Move inline dashboard JS to `static/js/dashboard.js`~~ | done | The Import-statement card now references the external script. |
| ~~Pin vulnerable telegrambots transitives~~ | done | `commons-io 2.21.0`, `commons-lang3 3.20.0`, `commons-beanutils 1.11.0` pinned in `<dependencyManagement>` to clear three advisories. |
| ~~Bump `poi-ooxml` 5.2.3 → 5.4.1, `opencsv` 5.7.1 → 5.9, `gson` 2.10.1 → 2.13.2~~ | done | Refreshes direct deps; clears `CVE-2025-31672` on `poi-ooxml`. |
| Bump `telegrambots` 6.9 → 7.x / 10.x (Boot starter, split artifacts) | M | 6.x is no longer maintained; the 7.x+ line ships a Spring Boot starter (`telegrambots-springboot-longpolling-starter`) and splits client/meta into separate jars. This requires reworking `PortfolioBot` to the newer consumer API. |
| ~~Tighten `PortfolioBot.detectBroker` heuristic~~ | done | IBKR now matches only explicit `ibkr` names or strict `U\d+\..*\.csv` activity files. |
| ~~`application-prod.yml` profile + secret hygiene (no `change-me-admin` default)~~ | done | Production config now demands explicit `APP_SECURITY_*` overrides. |

## Theme G - UX

| Item | Effort | Why |
|---|---|---|
| Positions table on dashboard (sortable, filter by broker / currency) | M | KPIs are great for trend, but you can't drill into individual holdings today. |
| Per-symbol detail page (`/dashboard/symbol/{ticker}`) - lot history, dividends, TA snapshot | L | Pulls together data already in the DB; just no surface for it. |
| Dark-mode toggle | S | Tabler.css already ships a dark variant. |
| "Import progress" SSE stream | M | Long-running IBKR imports today show nothing until they finish. |
| Mobile layout pass | S | The grid breaks below 720px. |

## Explicitly deferred

- **Real-time market data (websocket).** TwelveData rest polling is fine for daily snapshots; sockets add complexity without clear payoff.
- **Migrating off Telegram.** Slack/Discord adapters are easy *after* `NotificationService` learns to push to multiple channels (Theme C), but not before.
- **Replacing JPA with R2DBC / WebFlux.** No throughput problem to solve.
- **Yahoo as primary quote source.** The Yahoo unofficial API breaks every few quarters; keep it as the optional CSV-export fallback only.

## How to use this file

- Each line is meant to land as a small PR. Reference the item in the commit subject (e.g. `feat(notifications): persist drawdown peak [Roadmap A.1]`).
- When something ships, remove it from "Next priorities" and mark it done in the relevant theme or release notes.
- Keep `AGENTS.md` in sync once a roadmap item changes a documented invariant.



