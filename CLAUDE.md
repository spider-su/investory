# CLAUDE.md

## Project Snapshot
- Spring Boot 4.1 monolith for portfolio/investment tracking. Maven project, Java 25 target.
- Main flow: broker XLSX/CSV import -> normalized portfolio tables -> FX/market sync -> projection refresh -> Thymeleaf dashboard, Yahoo CSV export, Telegram notifications, optional AI answers/reports.
- Package layout:
  - `controllers`: UI, REST import/export/admin, Telegram bot, Ghostfolio-compatible read APIs.
  - `services`: portfolio analytics, market/FX sync, projections, reset, taxes, cash flow, manual prices.
  - `services/imports`: broker parser SPI plus XTB, IBKR, Yahoo export.
  - `services/openai`: Telegram AI replies, dashboard-context loading, scheduled reports.
  - `services/portfolio`: deterministic Telegram command routing.
  - `services/notifications`: digest and alert rules.
  - `infrastructure`: enums, JPA entities, repositories, projection/view entities.
  - `clients`: native clients for TwelveData and exchangerate.host.

## Build And Test
- Never run Maven wrappers; no wrapper is present. Use Maven directly.
- Java 25+ required. Known-good command on this machine:
  `JAVA_HOME=/Users/olesirob/Library/Java/JavaVirtualMachines/openjdk-26/Contents/Home PATH="/Users/olesirob/Library/Java/JavaVirtualMachines/openjdk-26/Contents/Home/bin:$PATH" /Users/olesirob/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn test`
- Recent full test run: `230 tests`, `0 failures`, `0 errors`, `0 skipped`.
- Spotless is configured but not bound to any lifecycle phase. Use `mvn spotless:check` or `mvn spotless:apply` manually.
- Lombok must stay in `maven-compiler-plugin` `annotationProcessorPaths`; Boot 4.1 / compiler plugin 3.15 does not auto-discover processors.
- `@WebMvcTest` import is `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`.
- Web MVC test slices need `@Import({SecurityConfig.class, MockMvcSecurityTestConfig.class})`; otherwise security can produce unexpected 401s.
- Boot 4.x removed `@MockBean`; use `@MockitoBean` from `org.springframework.test.context.bean.override.mockito`.
- Telegram transitives are pinned in dependency management: `commons-io 2.21.0`, `commons-lang3 3.20.0`, `commons-beanutils 1.11.0`. Do not downgrade.

## Runtime Configuration
- PostgreSQL + Flyway, schema `investory`.
- `spring.jpa.hibernate.ddl-auto=none`; schema changes must go through migrations.
- Flyway locations: `classpath:sql/migration`; `out-of-order=true`.
- HTTP Basic uses in-memory `ADMIN` and `USER` accounts from `app.security.*`.
- CSRF is disabled. Static/dashboard/root/error are public. Mutating requests require `ROLE_ADMIN`; other GETs are currently permitted.
- Telegram bot is conditional on `app.telegram.enabled=true`.
- OpenAI chat is conditional/configured under `app.openai.*`; default model is `gpt-5-mini`, disabled by default.
- OpenAI portfolio context reads rendered dashboard HTML from `app.openai.portfolio-context.dashboard-url`, strips it to text, and caps it by `max-characters`.

## Controller Surface
- Public UI:
  - `GET /`
  - `GET /dashboard`
- Main REST/admin:
  - `POST /import/broker/{broker}`
  - `GET /export/generate`
  - `POST /admin/refresh-prices`
  - `POST /admin/rebuild-monthly`
  - `POST /admin/assets/{symbol}/price`
- Ghostfolio-compatible read surface exists under:
  - `/api/v1/info`, `/api/v1/health`, `/api/v1/auth/anonymous`
  - `/api/v1/user`, `/api/v1/user/settings`
  - `/api/v1/account/**`, `/api/v1/activities/**`, `/api/v1/portfolio/**`
  - `/api/v2/portfolio/performance`, `/api/assets/**`
- Ghostfolio compatibility security is only isolated and fully permissive under Spring profile `ghostfolio`. Do not broaden default security to support it.
- Removed legacy controllers should stay removed unless a concrete product need returns: stock sync/create API, currency refresh API, indicator API, history API, import-history listing API.

## Import Flow
- Import path: `ImportController` -> `ImportOrchestratorService.importFile()` -> `BrokerImportParser`.
- Current parser implementations: `XtbBrokerImportParser` and `IbkrBrokerImportParser`.
- XTB must keep using `XtbImportV2Service`; do not restore the legacy XTB importer.
- IBKR supports activity statements and transaction-only CSVs. Activity statement `Open Positions` and `Net Asset Value` sections replace reconstructed IBKR open rows and update account summary fields.
- Import orchestrator checks SHA-256. A previously applied file returns `duplicate=true` and must not mutate imported portfolio rows.
- Failure auditing goes through `ImportBatchAuditWriter` with `REQUIRES_NEW`; FAILED `import_history` rows should survive parser rollback. Raw payload is capped at 8 KB with truncation metadata.
- Telegram document imports use the same orchestrator with `ImportSourceType.TELEGRAM`; broker detection comes from filename and extension.

## Telegram And AI
- Telegram bot rejects unauthorized chats unless `app.telegram.chat-id` matches.
- Telegram supports broker document import, `/start`, `/reset`, deterministic portfolio commands, then OpenAI fallback.
- Deterministic commands include `/balance`, `/performance`, `/pnl`, `/cash`, `/positions`, `/dividends`, `/allocation`, `/risk`, `/alerts`, `/report`, `/help`.
- `PortfolioCommandService` intentionally reads the same dashboard-context text used by AI; keep deterministic Telegram values aligned with dashboard rendering.
- Scheduled AI reports require both `app.openai.analysis.enabled=true` and `app.telegram.enabled=true`.
- AI report cron defaults:
  - monthly: first day of month, 09:00 Europe/Warsaw
  - quarterly: second day of Jan/Apr/Jul/Oct, 09:15 Europe/Warsaw
  - annual: third day of January, 09:30 Europe/Warsaw
- AI services must not fabricate portfolio facts. Use supplied dashboard context, state missing data, and avoid direct buy/sell instructions.

## Database Guardrails
- Migration files currently include:
  - `V01.000__Initial_schema.sql`
  - `V01.001__Initial_data.sql`
  - `V01.002__checks_and_views.sql`
  - `V01.003__asset_price_history_import.sql`
- Currency domain is fixed to `USD`, `EUR`, `PLN`.
- Core live tables include `accounts`, `portfolios`, `assets`, `asset_price_history`, `cash_operations`, `positions`, `account_daily`, `account_monthly`, `account_statistics`, `import_history`, `exchange_rates`, and benchmark monthly close data.
- `positions` is the only position table. `OpenedPosition` and `ClosedPosition` are Java views over `positions`, filtered by `close_time`; do not recreate `opened_positions` or `closed_positions`.
- `assets` owns latest quote columns: `market_price`, `market_price_usd`, `price_source`, `price_updated_at`. `market_price_usd` must be populated.
- `asset_price_history` stores observed and estimated historical rows separately with source provenance, quality score/class, scale metadata, proxy flags, and price currency.
- `account_daily` is live and feeds monthly/statistics refresh. It should not be treated as disposable history.
- `account_monthly` feeds benchmark curves and account filtering.
- `account_statistics` feeds balance/cash/currency dashboard calculations and fallback account summaries.
- Materialized views kept by `V01.002`:
  - `mv_portfolio_kpi_summary` for dashboard totals.
  - `mv_portfolio_asset_allocation` for allocation API/UI compatibility.
  - `mv_portfolio_currency_breakdown` for dashboard/Ghostfolio-compatible currency data.
- Removed legacy database surface should stay removed: `stocks`, `ticker_monthly`, `monthly_position_summary`, `position_summary`, `portfolio_history`, old open-position history, indicator tables, and old diagnostic regular views.

## Market, FX, Projections
- `MarketService.fullPortfolioUpdate()` refreshes prices, applies prices to open positions, re-values IBKR positions/account equity, then refreshes projections/views.
- TwelveData client is `clients/market/TwelveDataService`.
- Quote sync sources `assets`, not a `stocks` table.
- Stored app symbols are usually broker-style, e.g. `NVDA.US`; TwelveData `assets.ticker` is often bare for US names, e.g. `NVDA`.
- `MarketService.CHUNK_SIZE = 8`; `app.market.chunk-pause-ms` defaults to 120 seconds.
- `app.market.skip-non-us-listings=true` by default; manual prices cover listings not available on the current TwelveData plan.
- `REMX.UK` has manual quote normalization. Preserve it unless quote-source mapping changes.
- `AssetPriceFallbackService` and `asset_price_history` support historical fallback values; preserve source/quality semantics when adding data imports.
- FX refresh calls exchangerate.host only for USD base, then derives EUR/PLN cross rates locally. Rows are monthly, keyed by month start.
- `CurrencyRateService.convertToBaseCurrency(...)` uses direct or inverse cached historical rates and falls back to unconverted amounts with warning when data is missing.
- `PortfolioProjectionService.recalculateAll()` rebuilds projection tables from imported rows. If imported cash dates are edited manually, refresh projections instead of patching dashboard calculations.

## Dashboard And Export
- Thymeleaf dashboard template lives under `classpath:/static/dashboard/`; `/` returns `templates/home.html`.
- Dashboard totals prefer `mv_portfolio_kpi_summary`, then enrich with account balances, currencies, taxes, performance, dividends, benchmark data, and FX cards.
- Preserve dashboard behavior:
  - Hide near-zero accounts from account lists and benchmark options.
  - Benchmark chart tooltips show percent and absolute P/L.
  - Dividend KPI popup shows Top 10: top 9 symbols plus `Other` when more than 10 symbols exist.
  - Dividend popup symbols are plain text; do not add avatars/icons there.
- `YahooExportService` writes Yahoo-compatible CSV from DB state.
- Yahoo export cash is emitted as `USDT-USD`.
- Yahoo export supports optional account filtering through `app.export.yahoo.accounts`.

## Notifications
- `NotificationService.sendDailyDigest()` and `runAlerts()` are scheduled weekdays at 22:22 Europe/Warsaw.
- Active alert rules: concentration, drawdown, stale import.
- `DrawdownAlertRule` stores peak equity in memory and resets on restart.
- `app.notifications.enabled=false` silences digest and alerts without removing beans.

## Cleanup Discipline
- Before deleting any entity/table/view, check Java, migrations, dashboard JavaScript/templates, Ghostfolio-compatible controllers, scheduler, reset, export, and tests.
- Do not reintroduce removed APIs or tables as shortcuts.
- Keep docs synchronized when adding endpoints, migrations, schedulers, or externally visible config keys.
