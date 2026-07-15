# Build & Test Imperatives
- Never run builds using wrappers; call Maven directly.
- This project targets Java 25. If the shell default is older, point `JAVA_HOME` and `PATH` to Java 25+ before running Maven.
- On this machine the known-good command is:
  `JAVA_HOME=/Users/olesirob/Library/Java/JavaVirtualMachines/openjdk-26/Contents/Home PATH="/Users/olesirob/Library/Java/JavaVirtualMachines/openjdk-26/Contents/Home/bin:$PATH" /Users/olesirob/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn test`
- To check formatting, use `mvn spotless:check`. To fix formatting, use `mvn spotless:apply`. Do not bind Spotless to a build phase.
- Every controller test slice uses `@WebMvcTest`; explicitly import `@Import({SecurityConfig.class, MockMvcSecurityTestConfig.class})` to avoid 401 test failures.
- Boot 4.x removed `@MockBean`; use `@MockitoBean`.
- Pinned security transitives for Telegram: `commons-io 2.21.0`, `commons-lang3 3.20.0`, `commons-beanutils 1.11.0`. Do not downgrade.

## Current Architecture Notes
- Public controller surface is intentionally small:
  - `GET /`
  - `GET /dashboard`
  - `POST /import/broker/{broker}`
  - `GET /export/generate`
  - `POST /admin/refresh-prices`
- Removed controllers should stay removed unless there is a concrete product need: stock API, portfolio JSON API, currency refresh API, indicator API, history API, import-history listing API.
- Import is broker-parser based: `ImportController` -> `ImportOrchestratorService` -> `BrokerImportParser`. Current parser implementations are XTB V2 and IBKR.
- XTB must keep using `XtbImportV2Service`; do not route imports back to the legacy importer.
- Yahoo export uses DB state directly and exports cash as `USDT-USD`.

## Database Guardrails
- Current Flyway baseline is squashed into:
  - `V01.000__Initial_schema.sql`
  - `V01.001__Initial_data.sql`
  - `V01.002__checks_and_views.sql`
- Use the `investory` schema consistently.
- `positions` is the only position table. `OpenedPosition` and `ClosedPosition` are Java views over `positions`, filtered by `close_time`; do not recreate `opened_positions` or `closed_positions`.
- `assets` replaced the old `stocks` flow and also owns the latest quote columns. Do not reintroduce `stocks`, `asset_prices`, `ticker_monthly`, or `monthly_position_summary`.
- `portfolio_history`, open-position history, technical/fundamental indicator tables, and old diagnostic SQL views were removed.
- Keep `mv_portfolio_kpi_summary`; dashboard totals read it.
- Keep `mv_portfolio_asset_allocation`; it is intentionally retained for future allocation UI/API use.
- `position_summary` was removed. `PortfolioProjectionService` keeps per-position cost/unrealized values in memory while building `account_statistics`.
- `account_monthly` is live: it feeds the benchmark chart and account filtering.
- `account_statistics` is live: it feeds balance/cash/currency dashboard calculations.

## Market And FX Guardrails
- Market sync reads `assets`, requests TwelveData by `assets.ticker`, and writes quote fields back to `assets`.
- For US assets, TwelveData tickers should usually be bare (`NVDA`), while app symbols remain broker-style (`NVDA.US`).
- `assets.market_price_usd` must be populated.
- `REMX.UK` has a manual price normalization in `MarketService`; preserve it unless quote source mapping changes.
- FX refresh calls exchangerate.host for USD base only and derives cross rates locally. Exchange-rate rows are monthly, keyed by month start.

## Cleanup Status
- Legacy/unused controllers and tables have been pruned aggressively. Before deleting another entity/table, grep both Java and migrations and check whether it is read by dashboard, benchmark, export, scheduler, or reset flows.
- Account broker/projection summaries live on `accounts` (`balance`, `equity`, `summary_updated_at`, `summary_source`) and are used by importers, Yahoo export cash handling, account-statistics fallback, and IBKR revaluation. Projection fallback values are stored in the account's own currency and marked `PROJECTION`; broker-provided values are marked `BROKER` and are not overwritten.
- `mv_portfolio_asset_allocation` currently has no Java reader, but it is intentionally kept.
