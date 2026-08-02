# AGENTS.md

Canonical engineering guidance for every coding agent working in this repository.
Tool-specific instruction files should contain only small overlays and must not duplicate
project facts from this file.

## Communication Style

- User-facing conversation in this repository should use brief caveman-style English across
  commentary and final responses: short sentences, simple wording, direct statements.
- Keep code, commands, file paths, API names, class names, and other technical identifiers exact.
- If caveman phrasing would make a technical point ambiguous, keep the technical point precise and
  only simplify the surrounding prose.

## Stack Discipline

- Treat current project stack as the default implementation target: Spring Boot, Java, Maven,
  PostgreSQL, Flyway, JPA, Thymeleaf, and the existing frontend/runtime choices already in repo.
- Prefer implementing inside the existing stack instead of proposing other languages, frameworks,
  database dialects, ORMs, build tools, or frontend stacks.
- Only suggest or introduce a different stack component when the user explicitly asks for it or a
  hard technical constraint in the repository makes the current stack unable to satisfy the task.

## Flyway Discipline

- Prefer updating the intended migration structure instead of stacking extra patch migrations when
  the migration is still part of active local work and has not become a stable shared baseline.
- Use additional follow-up patch migrations for already-shipped or shared schema history that must
  remain append-only.

## Execution Autonomy

- For routine work inside this repository, do not stop to ask for permission before editing project
  files or running normal build, test, lint, and verification commands.
- Treat repository-local implementation, compile, test, and check steps as pre-approved by the
  user unless the action would be destructive or would require platform-level sandbox escalation.
- If platform approval is technically required outside repository-local work, request it directly
  without first asking the user in chat.

## Project Snapshot

- Spring Boot 4.1 monolith for portfolio and investment tracking; Maven build targeting Java 25.
- Main flow: broker XLSX/CSV import -> normalized portfolio tables -> FX and market sync ->
  daily/monthly projections -> Thymeleaf dashboard, Ghostfolio-compatible read APIs, Yahoo CSV
  export, Telegram notifications, and optional OpenAI answers/reports.
- Package boundaries:
  - `controllers`: UI, import/export/admin REST, Telegram bot, and Ghostfolio compatibility.
  - `services`: portfolio analytics, market/FX sync, projections, tax, cash flow, and
    manual prices.
  - `services/imports`: broker parser SPI, XTB/IBKR implementations, and Yahoo export.
  - `services/openai`: Telegram AI replies, dashboard context, and scheduled reports.
  - `services/portfolio`: deterministic Telegram command routing.
  - `services/notifications`: digest and alert rules.
  - `infrastructure`: enums, JPA entities/repositories, and projection/view entities.
  - `clients`: native `java.net.http` clients for TwelveData and exchangerate.host.
  - `config`: security, scheduling, Thymeleaf, Telegram, and AI scheduling.

## Build And Test

- No Maven wrapper is currently present. Use Maven directly: `mvn test`, `mvn clean package`,
  or `mvn spring-boot:run`.
- Always start the app with the `local` Spring profile when running against the local database:
  `mvn spring-boot:run -Dspring-boot.run.profiles=local`. Without it the app binds the empty
  default datasource (`localhost:5432/investory`) and Flyway attempts a from-scratch migration,
  which surfaces as misleading errors (for example a missing `signed_position_quantity`
  function). This is an environment/run issue, not a code or migration change.
- Java 25 or newer is required. Ensure `java -version` and `mvn -version` use a compatible JDK;
  do not rely on a machine-specific JDK path in repository documentation.
- Spotless is configured but not bound to the lifecycle. Run `mvn spotless:check` or
  `mvn spotless:apply` manually.
- Lombok must remain in `maven-compiler-plugin` `annotationProcessorPaths`; compiler plugin 3.15
  does not auto-discover it from the project classpath.
- `@WebMvcTest` comes from
  `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`.
- MVC test slices that exercise security need
  `@Import({SecurityConfig.class, MockMvcSecurityTestConfig.class})`.
- Spring Boot 4 removed `@MockBean`; use `@MockitoBean` from
  `org.springframework.test.context.bean.override.mockito`.
- Telegram transitives are deliberately pinned in dependency management:
  `commons-io 2.21.0`, `commons-lang3 3.20.0`, and `commons-beanutils 1.11.0`.
  Do not downgrade or remove the overrides without checking the associated advisories.
- Manual API smoke requests live in `src/test/manual/api.http`.
- Do not record an exact test count here; it becomes stale. Report the result of the current
  verified run when handing off a change.

## Runtime And Security

- PostgreSQL + Flyway use schema `investory`.
- `spring.jpa.hibernate.ddl-auto=none`; make schema changes through versioned Flyway migrations
  under `src/main/resources/sql/migration`.
- HTTP Basic uses in-memory `ADMIN` and `USER` credentials from `app.security.*`.
- Root, dashboard, error, and static resources are public. Mutating routes require `ROLE_ADMIN`;
  CSRF is currently disabled.
- Ghostfolio compatibility has a separate, permissive security chain only under the
  `ghostfolio` profile. Do not weaken default security to support it.
- Devtools is not on the application classpath. `application-prod.yml` requires explicit
  `APP_SECURITY_*` overrides for production credentials; do not rely on local defaults in prod runs.
- Telegram wiring is conditional on `app.telegram.enabled=true`.
- OpenAI chat is configured by `app.openai.*`, uses `gpt-5-mini` by default, and is disabled by
  default. Portfolio context is rendered dashboard text capped by the configured character limit.
- Notifications are controlled by `app.notifications.enabled`.

## HTTP Surface

- Public UI:
  - `GET /`
  - `GET /dashboard`
- Import/export/admin:
  - `POST /import/broker/{broker}`
  - `GET /export/generate`
  - `POST /admin/refresh-prices`
  - `POST /admin/rebuild-monthly`
  - `POST /admin/assets/{symbol}/price`
- Ghostfolio-compatible surface:
  - `GET /api/v1/info`, `GET /api/v1/health`
  - `POST /api/v1/auth/anonymous`
  - `/api/v1/user`, `/api/v1/user/settings`
  - `/api/v1/account/**`, `/api/v1/activities/**`, `/api/v1/portfolio/**`
  - `/api/v2/portfolio/performance`, `/api/assets/**`
- Removed legacy controllers should remain removed unless a concrete product need returns:
  stock sync/create, currency refresh, indicators, history, and import-history listing.

## Import Flow

- `ImportController` -> `ImportOrchestratorService.importFile()` -> `BrokerImportParser`.
- Current parser implementations are `XtbBrokerImportParser` and `IbkrBrokerImportParser`.
  Add a broker with a `@Component` implementation and a new `BrokerType`.
- XTB uses `XtbImportV2Service`; do not restore the legacy importer.
- IBKR supports activity statements and transaction-only CSVs. Activity-statement
  `Open Positions` and `Net Asset Value` sections replace reconstructed open rows and update
  account summaries.
- Imports are deduplicated by SHA-256. A previously applied file returns `duplicate=true`
  without mutating portfolio rows.
- Failure auditing uses `ImportBatchAuditWriter` with `REQUIRES_NEW`, so failed
  `import_history` rows survive parser rollback. Raw payload is capped at 8 KB with truncation
  metadata.
- Telegram document imports use the same orchestrator with `ImportSourceType.TELEGRAM`;
  filename and extension drive broker detection.
- Asset identity:
  - `assets.id`: canonical application identity used by every transactional and price foreign key.
  - `assets.symbol`: canonical display/provider-routing symbol, normally `TICKER.EXCHANGE`.
  - `assets.ticker`: TwelveData request symbol, often bare for US instruments.
  - `assets.ibkr`: IBKR alias or mapping.
  - `assets.yahoo`: Yahoo-specific symbol where needed.
  - `positions.source_asset_symbol` and `cash_operations.source_asset_symbol` preserve raw imports;
    broker symbols are provenance, never identity.

## Database And Projection Guardrails

- Current baseline migrations:
  - `V01.000__Initial_schema.sql`
  - `V01.001__Initial_data.sql`
  - `V01.002__checks_and_views.sql`
  - `V01.003__asset_price_history_import.sql`
- Currency is limited to `USD`, `EUR`, and `PLN`.
- Core raw tables include `accounts`, `portfolios`, `assets`, `asset_price_history`,
  `cash_operations`, `positions`, `import_history`, `exchange_rates`, and benchmark monthly closes.
- `account_daily` is the persisted projection/read-model boundary rebuilt from raw tables.
- Higher summaries above `account_daily` must stay database-derived views/materialized views, not
  separately persisted application-owned tables.
- `positions` is the only position table. `OpenedPosition` and `ClosedPosition` map to it and
  are filtered by `close_time`; never recreate split position tables.
- Position money uses explicit `price_currency`, `cost_currency`, `profit_currency`, and
  `commission_currency`; never infer them from `accounts.currency`. Stored `volume` is absolute;
  use the shared signed-quantity helper/function for BUY/SELL direction. The
  `positions.settlement_model` contract is per position because one account and symbol can hold
  both cash-settled shares and result-only CFDs. Never value `RESULT_ONLY` at full notional.
- `assets` owns current quote columns: `market_price`, `market_price_usd`, `price_source`, and
  `price_updated_at`. Projections and export require `market_price_usd`.
- `asset_price_history` distinguishes observed and estimated prices and preserves source,
  quality, scale, proxy, and currency metadata. Do not collapse those semantics.
- `account_daily` is live projection data and feeds all higher reporting layers; it is not
  disposable legacy history.
- Hibernate import writes use JDBC batches of 50 with ordered inserts and updates. Asset and
  `account_daily` sequence allocation windows are 50. Cash operations and positions use
  application-assigned IDs, so broker imports do not use their sequences.
- Ordinary reporting views:
  - `v_portfolio_daily`
  - `reporting_trade_settlement_reconciliation_by_account`
- Materialized reporting views:
  - `account_monthly_mv`
  - `portfolio_daily_mv`
  - `portfolio_monthly_mv`
  - `account_statistics`
  - `portfolio_kpi_summary`
  - `portfolio_asset_allocation`
  - `portfolio_currency_breakdown`
  - `symbol_performance`
  - `reporting_trade_settlement_reconciliation`
- Removed database surface must stay removed: `stocks`, `ticker_monthly`,
  `monthly_position_summary`, `position_summary`, `portfolio_history`, old open-position
  history, indicator tables, and obsolete diagnostic views.

## Market, FX, And Projections

- `MarketService.fullPortfolioUpdate()` refreshes prices, applies them to open positions,
  re-values IBKR positions/account equity, and refreshes projections/views.
- TwelveData access lives in `clients/market/TwelveDataService`; symbols come from `assets`.
- Quote sync uses `MarketService.CHUNK_SIZE = 8` and `app.market.chunk-pause-ms` defaults to
  120 seconds.
- `app.market.skip-non-us-listings=true` by default; manual prices cover unavailable listings.
- Preserve the `REMX.UK` quote normalization unless provider mapping changes.
- `AssetPriceFallbackService` uses historical prices while preserving provenance and quality.
- FX refresh fetches USD-base rates from exchangerate.host and derives EUR/PLN crosses locally.
  Rows are stored per currency pair at month start.
- Currency conversion tries direct and inverse cached historical rates, then warns and returns
  the unconverted amount when no rate exists.
- `PortfolioProjectionService.recalculateAll()` rebuilds daily/monthly/statistics projections.
  Refresh projections after correcting imported dates rather than patching dashboard math.

## Dashboard, Export, Telegram, And Notifications

- Dashboard template: `classpath:/static/dashboard/dashboard.html`; `/` returns
  `templates/home.html`.
- Dashboard totals prefer `mv_portfolio_kpi_summary` and are enriched with account balances,
  currencies, taxes, performance, dividends, benchmark data, and FX cards.
- Dashboard data flow: `HomeController` calls `PortfolioService.calculateTotalProfitLoss()`;
  the main header balance, cash, deposits, withdrawals, and headline P/L come from
  `mv_portfolio_kpi_summary` when available, with cash-flow and interest supplements from
  non-cash-only `account_statistics` and `cash_operations`. The account table is calculated
  separately from `account_daily`/account statistics and converted to portfolio base currency.
  The open-position popup reads `portfolio_asset_allocation`, rebuilt by
  `PortfolioProjectionService` from open positions, current/historical prices, and FX. Keep
  these three surfaces aligned after projection or price changes; refresh projections before
  diagnosing differences. Position price currency is the instrument quote currency; position
  and account currencies are used for cost and cash conversion, not for relabeling a quote.
- Reporting data lineage:
  - Raw import rows land in `positions`, `cash_operations`, `accounts`, `assets`, and
    `exchange_rates`. `normalized_cash_operations` is the canonical classified ledger over
    `cash_operations`; it adds categories, sign rules, account/base-currency amounts, and
    reconciliation metadata.
  - `account_daily` is the persisted daily projection. It combines open/closed position
    results, cash ledger movements, prices, and FX for one account/date. `v_portfolio_daily`
    exposes the database-derived portfolio daily layer above it.
  - Materialized views are reporting snapshots, not independent truth. `account_statistics`
    summarizes latest `account_daily` plus normalized cash flows; `portfolio_kpi_summary`
    rolls account/projection values into the header totals; `portfolio_asset_allocation`
    rolls current open positions into symbol totals. `PortfolioProjectionService` must
    rebuild/refresh them after imports, market prices, FX, or cash classification changes.
  - `cash_operations` is still read directly for cash-flow details because the header needs
    deposits, withdrawals, dividends, withholding tax, and interest with current ledger
    semantics, including cash-only accounts. A stale materialized view can lag a newly
    imported operation, and `account_daily` is a valuation projection rather than the full
    transaction ledger. Direct reads are therefore for ledger-detail supplements and
    reconciliation, not a second source for position valuation. Apply the same
    `cash_only` filter and `CashFlowAggregator` rules when reading it.
- Preserve these UI rules:
  - Exclude near-zero accounts from account lists and benchmark choices.
  - Benchmark tooltips show percent and absolute P/L.
  - Dividend Top 10 shows nine symbols plus `Other` when more than ten symbols exist.
  - Dividend popup symbols are plain text, without avatars.
- Dashboard data quality, risk exposure, daily performance detail, and monthly attribution are
  derived from the current ledger and `account_daily` projection.
- Detailed data-quality issue provenance is guarded by
  `app.portfolio.data-quality-issues-enabled` and defaults to `false` because its reconstructed
  history scan is too slow for normal dashboard loads.
- Yahoo export emits cash as `USDT-USD` and supports account filtering through
  `app.export.yahoo.accounts`.
- Telegram rejects unauthorized chats unless `app.telegram.chat-id` matches. It supports broker
  documents, `/start`, `/reset`, deterministic portfolio commands, then OpenAI fallback.
- `PortfolioCommandService` reads the same dashboard context as AI; keep command values aligned
  with dashboard rendering.
- Scheduled AI reports require both AI analysis and Telegram to be enabled. Services must use
  supplied portfolio context, state missing data, and avoid fabricated facts or direct
  buy/sell instructions.
- Active alerts are concentration, drawdown, and stale import. Drawdown peak equity is currently
  in memory and resets on restart.
- Scheduler times, all in `Europe/Warsaw`:
  - FX refresh: weekdays 15:00.
  - Market close update/projection refresh: weekdays 22:01.
  - Notification digest and alerts: weekdays 22:22.
  - AI reports: monthly day 1 at 09:00; quarterly day 2 at 09:15; annual January 3 at 09:30.

## Change Discipline

- Before deleting an entity, table, view, or endpoint, check Java, migrations, dashboard assets,
  Ghostfolio controllers, schedulers, reset/export paths, and tests.
- Do not reintroduce removed APIs or tables as shortcuts.
- Update this file when a change adds or removes endpoints, migrations, schedulers, core tables,
  or externally visible configuration.
- Keep `ROADMAP.md` future-facing. Completed work belongs in history/release notes, not among
  active priorities.

## Local Database Investigation

- Always use the `local` Spring profile configuration and connect through JDBC when checking live
  database data or validating live calculation results. Do not try another Spring profile, infer
  credentials from another configuration source, or use `psql` for these checks.
- For data discrepancies, connect to the configured local PostgreSQL database before drawing
  conclusions from screenshots or code alone.
- First verify database freshness and schema: Flyway version, required columns such as
  `accounts.cash_only`, and row counts/date ranges for `account_daily`, `account_monthly_mv`,
  and `portfolio_monthly_mv`. A local database with empty projections or an older schema is
  not evidence about the current dashboard.
- For a suspicious peak, compare the same account and period across `account_daily`,
  `account_monthly_mv`, `cash_operations`/`normalized_cash_operations`, prices, and FX.
  Separate external flows from valuation movement. Report clearly when the local DB cannot
  reproduce the user screenshot.
