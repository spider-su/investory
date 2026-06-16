# ROADMAP

Living plan of follow-ups based on the current state of the codebase (post Boot 4.1 + Java 25 upgrade, June 2026). Items are sized **S** (≤ half day), **M** (1-2 days), **L** (multi-day). Tackle in any order, but the "Next 30 days" list is the recommended sequence.

## Next 30 days (high impact, low risk)

1. **Persist `DrawdownAlertRule` peak equity** _(S)_ - currently in-memory, resets on every restart; a single-row `notification_state` table (or column on `portfolio_history`) makes the rule reliable.
2. **Per-rule alert deduplication** _(S)_ - same condition fires every weekday at 22:00 → 5 duplicate Telegrams per week. Add `last_fired_at` per `AlertRule.code()` and only re-fire after N hours / state change.
3. **Spring Boot Actuator + healthcheck** _(S)_ - add `spring-boot-starter-actuator`, expose `/actuator/health` (DB + Telegram + import-staleness), wire into Docker `HEALTHCHECK`.
4. **Test job in GitHub Actions** _(S)_ - the existing workflow only builds the Docker image. Add a `mvn test` step on PRs so the ~30-class suite gates merges.
5. **Maven wrapper (`mvnw`)** _(S)_ - vendoring the wrapper unblocks CI and Docker builds without a system Maven, and means the README "Run locally" snippet works on a fresh machine.

That's roughly 2-3 days of focused work and clears most of the operational rough edges.

> After pulling the Boot 4.1 upgrade, trigger **Reload All Maven Projects** in IntelliJ so it fetches the two new artifacts pinned in `<dependencyManagement>` (`poi-ooxml:5.4.1`, `commons-beanutils:1.11.0`).

## Theme A - Observability & ops

| Item | Effort | Why |
|---|---|---|
| Actuator + `/actuator/health` (DB, Telegram bot reg, last import age) | S | Container orchestrators (Compose `healthcheck`, K8s probes) need a real signal. |
| Micrometer + Prometheus exporter | M | Quote-fetch latency, FX-refresh failures, rule-fire counts — none are visible today. |
| Structured request logging (correlation id per import batch) | S | Currently logs are flat; tying log lines to `import_batch.id` makes triage trivial. |
| OpenAPI / Swagger UI (`springdoc-openapi-starter-webmvc-ui`) | S | Replaces the hand-written `src/test/manual/api.http`. ~10 lines + auto-docs. |
| Dedicated `application-prod.yml` (disable devtools, force `notifications.enabled=true`, tighten Hibernate logging) | S | Today a prod run inherits dev defaults. |

## Theme B - Test hardening

| Item | Effort | Why |
|---|---|---|
| WireMock fixtures for `TwelveDataService` (`/quote`, `/time_series`, rate-limit 429) | M | The most fragile external dependency has zero tests. |
| Unit tests for `IbkrImportService` FIFO matching | M | 577 lines, only smoke-tested via parser. Cover lot-matching, partial fills, forex rows, dividends, synthetic-id stability. |
| Synthetic XLSX builder for `XtbImportServiceTest` | M | Today 10 of 11 fixture tests skip when broker exports aren't present; an Apache POI helper would generate the minimal sheet shape and remove the `Assumptions.assumeTrue` workaround. |
| `YahooExportService` + `YahooFinanceService` tests (temp dir, mocked client) | S | Currently a hot-zone for silent regressions. |
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
| Refactor `XtbImportService` static state into a per-call context | M | Listed as a known gotcha in `AGENTS.md`; blocks concurrent imports today. |
| Idempotent partial re-imports (overlap windows) | M | The SHA-256 check covers full duplicates; overlapping date ranges between two exports double-count today unless you reset. |
| Validation report attached to `ImportBatch` (sheet-level row totals, currency totals, gaps) | S | Helps the user trust the import. |

## Theme E - Security & multi-user

| Item | Effort | Why |
|---|---|---|
| Activate or remove the OAuth2 Google config | S | The YAML stub is dead code; either wire it in (uncomment the starter dep in `pom.xml`) or delete the block. Drift attracts confusion. |
| Per-user data scoping (add `owner_id` column to all positional tables + Flyway migration) | L | Today every authenticated user sees the same portfolio. Required before exposing the instance to more than one human. |
| CSRF for UI POST routes only | S | CSRF is globally off; enable for the dashboard's import form and keep API routes off (Basic auth). |
| Rate limiting on import endpoints (`bucket4j`) | S | Anyone with admin can DOS the orchestrator with a giant XLSX loop. |
| Externalise secrets to env / Docker secrets, not yaml defaults | S | `change-me-admin` is the default password today; the README should reflect that and the prod profile should refuse to start without overrides. |

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
| Bump `telegrambots` 6.8 → 7.x / 10.x (Boot starter, split artifacts) | M | 6.x is no longer maintained; the 7.x+ line ships a real Spring Boot starter (`telegrambots-springboot-longpolling-starter`) and splits client/meta into separate jars. Held back during the Boot 4 migration because the local Maven cache didn't have the newer versions — pure infra task, no API blocker. Requires reworking `PortfolioBot` to the `LongPollingSingleThreadUpdateConsumer` API. |
| Tighten `PortfolioBot.detectBroker` heuristic | S | Today any `U*.csv` is treated as IBKR; tighten to `^U\d+\..*\.csv$` to avoid false positives. |
| `application-prod.yml` profile + secret hygiene (no `change-me-admin` default) | S | Already listed under Theme E; relevant here too once Spotless/format is enforced. |

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
- When something ships, move it out of "Next 30 days" and into the relevant theme as `~~done~~` (or just delete).
- Keep `AGENTS.md` in sync once a roadmap item changes a documented invariant.



