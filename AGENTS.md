# AGENTS.md

## Project Snapshot
- Spring Boot 4.1 monolith for portfolio/investment tracking (`pom.xml`, Java 25 via `<java.version>`).
- Main flow: broker XLSX/CSV import -> normalized portfolio tables -> FX/market sync -> projection refresh -> Thymeleaf dashboard, Yahoo CSV export, and optional Telegram notifications.
- Package boundaries:
  - `controllers`: UI, REST import/export, Telegram bot entrypoint.
  - `services`: dashboard analytics, market/FX sync, projection rebuilds, reset helpers, tax/cash-flow helpers.
  - `services/imports`: broker parser SPI and broker-specific importers.
  - `services/notifications`: digest and alert rules.
  - `infrastructure`: enums and JPA repositories/entities.
  - `clients`: native `java.net.http` clients for external APIs.
  - `config`: security, scheduling, Telegram wiring.
- Current public controllers are intentionally small: dashboard (`/`, `/dashboard`), broker import (`POST /import/broker/{broker}`), and Yahoo export (`GET /export/generate`).

## System Flow
- Import flow: `POST /import/broker/{broker}` -> `ImportController` -> `ImportOrchestratorService.importFile()` -> `BrokerImportParser` (`XtbBrokerImportParser` or `IbkrBrokerImportParser`) -> writes `cash_operations`, unified `positions`, `assets`, account summary fields on `accounts`, and `import_history`.
- UI import flow: dashboard "Import statement" card posts multipart files to `/import/broker/{broker}` and renders the `ImportBatchResponse`.
- Telegram import flow: `PortfolioBot` detects broker from document filename and calls the same orchestrator with `ImportSourceType.TELEGRAM`.
- Market flow: `MarketService.fullPortfolioUpdate()` runs price refresh, applies prices to open positions, re-values IBKR positions/account equity, then refreshes SQL projections/materialized views.
- Projection flow: `PortfolioProjectionService.recalculateAll()` rebuilds `account_monthly` and `account_statistics` from current imported rows. Per-position projection values are kept in memory only for account-statistics calculation. Missing/projection-sourced account balance/equity fields are backfilled from `account_statistics` in the account's own currency; real broker summaries are not overwritten.
- Dashboard flow: `PortfolioService.calculateTotalProfitLoss()` prefers `mv_portfolio_kpi_summary` for totals, then enriches with non-zero account balances, currency breakdowns, taxes, symbol performance, monthly performance, dividend gainers, and FX cards.
- Benchmark flow: `BenchmarkService.calculate(...)` reads `account_monthly`, supports account filtering from dashboard checkboxes, hides near-zero accounts, and compares each selected account's month-to-month value change against the same starting value compounded by SPY monthly closes from TwelveData. For multi-account selections, each account keeps its own starting value and curves are summed.
- Export flow: `YahooExportService` writes a Yahoo-compatible CSV from DB state; cash is exported as a `USDT-USD` position and positions use the first day of the current month as open date.
- Notification flow: `NotificationService.sendDailyDigest()` plus `runAlerts()`; Telegram delivery is optional and disabled by default.
- Scheduler flow:
  - FX refresh: weekdays `15:00 Europe/Warsaw`.
  - Market close record: weekdays `22:01 Europe/Warsaw`, runs full market update plus projection recalculation.
  - Notifications: weekdays `22:22 Europe/Warsaw`.

## Database And Migrations
- PostgreSQL + Flyway. Current squashed baseline files are:
  - `V01.000__Initial_schema.sql`
  - `V01.001__Initial_data.sql`
  - `V01.002__checks_and_views.sql`
- Keep schema consistent as `investory` (`spring.jpa.properties.hibernate.default_schema` and `spring.flyway.schemas` in `application.yml`).
- Currency domain is fixed to `USD`, `EUR`, `PLN` (`CurrencyType`). FX rows live in `exchange_rates(month, base, to_currency, rate)`.
- Core tables:
  - `accounts`, `portfolios`, `assets`
  - `cash_operations`
  - unified `positions` table for both open and closed positions
  - `accounts` also stores broker-reported cash/equity snapshots, or projection-derived fallback values in account currency when broker data is missing
  - `account_statistics`, `account_monthly`
  - `import_history`
- Java still has `OpenedPosition` and `ClosedPosition` entities, but both map to `positions`; repositories filter by `close_time IS NULL` or `close_time IS NOT NULL`. Do not recreate split `opened_positions` / `closed_positions` tables.
- `V01.002__checks_and_views.sql` keeps:
  - `refresh_account_statistics(...)`
  - `mv_portfolio_asset_allocation` (kept for future allocation UI/API use)
  - `mv_portfolio_kpi_summary` (used by dashboard totals)
- Removed legacy/unused database surface: `stocks`, `ticker_monthly`, `monthly_position_summary`, `position_summary`, `portfolio_history`, open-position history, indicator tables, and old diagnostic regular views.

## Import Notes
- Import parsers implement `BrokerImportParser`; add a broker by creating a `@Component` implementation and adding a `BrokerType`.
- `ImportOrchestratorService.importFile()` checks SHA-256. A previously applied file returns `duplicate=true` without mutating the existing batch row.
- Failure auditing goes through `ImportBatchAuditWriter` with `REQUIRES_NEW`, so FAILED `import_history` rows survive parser rollback. Raw payload is capped at 8 KB with a truncation header.
- XTB uses the V2 importer (`XtbImportV2Service`) through `XtbBrokerImportParser`; do not reintroduce the legacy XTB importer.
- IBKR import supports activity statements and transaction-only CSVs. Activity statement `Open Positions` / `Net Asset Value` sections replace reconstructed IBKR open rows and update balance/equity fields on `accounts`.
- Asset identity rules:
  - `assets.symbol` is the app/broker symbol, usually `TICKER.EXCHANGE` (`AAPL.US`, `CDR.PL`, `REMX.UK`).
  - `assets.ticker` is the TwelveData request symbol. For US assets this is usually bare (`NVDA`), not `NVDA.US`.
  - `assets.ibkr` stores IBKR-specific aliases/mapping.
  - `assets.yahoo` stores Yahoo export/fetch symbol when needed.
- Latest asset prices are stored directly on `assets` (`market_price`, `market_price_usd`, `price_source`, `price_updated_at`); `market_price_usd` must be populated because projections and export code expect it.
- `account_statistics` and `account_monthly` are projection tables. If imported cash-operation dates are manually changed, run the projection refresh instead of patching dashboard calculations; otherwise balances, cash, and benchmark curves can temporarily disagree.

## Market And FX Notes
- TwelveData client lives in `clients/market/TwelveDataService`; quote sync is intentionally chunked (`MarketService.CHUNK_SIZE = 8`) with `app.market.chunk-pause-ms` defaulting to 120 seconds.
- Market sync sources symbols from `assets`, not a `stocks` table.
- `MarketService` marks assets active based on open positions and backfills inactive asset prices from latest closed deals.
- `REMX.UK` has a manual quote normalization because TwelveData returns a scale inconsistent with the held VanEck instrument.
- `CurrencyRateUpdaterService` calls exchangerate.host only for USD-based rates, then derives EUR/PLN cross rates locally. It writes one row per currency pair per month-start.
- `CurrencyRateService.convertToBaseCurrency(...)` uses direct or inverse cached historical rates and falls back to unconverted amount with a warning when data is missing.

## UI/API Surface
- Public UI:
  - `GET /`
  - `GET /dashboard`
- REST:
  - `POST /import/broker/{broker}`
  - `GET /export/generate`
  - `POST /admin/refresh-prices`
  - `POST /admin/assets/{symbol}/price`
- Removed REST controllers/endpoints: stock sync/create, portfolio JSON API, indicator API, currency refresh API, history API, import history listing API.
- Thymeleaf dashboard template is under `classpath:/static/dashboard/`; `/` returns `templates/home.html`.
- Security uses HTTP Basic with in-memory users. `GET /`, `/error`, static assets, and `/dashboard/**` are public; mutating requests require `ROLE_ADMIN`; CSRF is disabled.
- Dashboard behavior to preserve:
  - Account lists and benchmark account options exclude near-zero accounts (`cash_balance + market_value` threshold).
  - Benchmark chart tooltips show both percent and absolute P/L values.
  - Dividend KPI has a popup-style `Top 10` table. Rows are converted to dashboard base currency, sorted by USD-equivalent net dividends, and rendered as top 9 symbols plus an `Other` summary row when more than 10 symbols exist.
  - Dividend popup symbols are plain text; do not add symbol avatars/icons there.

## Notifications
- Alert rules implement `AlertRule` and are injected as a list into `NotificationService`.
- Active alert rules include concentration, drawdown, and stale import checks.
- `DrawdownAlertRule` keeps peak equity in memory and resets on restart.
- `app.notifications.enabled=false` silences digest and alerts without removing beans.
- Telegram bot config is conditional on `app.telegram.enabled=true`; app starts cleanly with Telegram disabled.

## Build Wiring Gotchas
- Lombok must remain in `<annotationProcessorPaths>` of `maven-compiler-plugin`; Boot 4.1 / compiler plugin 3.15 no longer auto-discovers processors from classpath.
- `@WebMvcTest` needs `@Import({SecurityConfig.class, MockMvcSecurityTestConfig.class})`; otherwise `@WithMockUser` tests can still return 401.
- `@MockBean` is removed in Boot 4.x; tests use `@MockitoBean` from `org.springframework.test.context.bean.override.mockito`.
- `@WebMvcTest` import is `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`.
- Vulnerable Telegram transitives are pinned in dependency management: `commons-io 2.21.0`, `commons-lang3 3.20.0`, `commons-beanutils 1.11.0`. Do not downgrade.
- Maven runtime must be Java 25+. On this machine Java 26 works:
  `JAVA_HOME=/Users/olesirob/Library/Java/JavaVirtualMachines/openjdk-26/Contents/Home PATH="/Users/olesirob/Library/Java/JavaVirtualMachines/openjdk-26/Contents/Home/bin:$PATH" /Users/olesirob/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn test`

## Developer Workflow
- No Maven wrapper detected; use Maven directly.
- Build/test/run:
  - `mvn clean package`
  - `mvn test`
  - `mvn spring-boot:run`
- Spotless is configured but not bound to a lifecycle phase. Use `mvn spotless:apply` or `mvn spotless:check` manually.
- Manual API smoke calls live in `src/test/manual/api.http`.
- Current test suite is JUnit 5 + Mockito + Spring MVC test slices. Recent full run: `191 tests`, `0 failures`, `0 errors`.
