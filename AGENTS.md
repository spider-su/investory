# AGENTS.md

Canonical engineering guidance for every coding agent working in this repository.
Tool-specific instruction files should contain only small overlays and must not duplicate
project facts from this file.

## Communication Style

- User-facing conversation in this repository should use brief, simple, direct English.
- Keep code, commands, file paths, API names, class names, and other technical identifiers exact.
- Do not simplify wording when doing so would make a technical point ambiguous.

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
- Main flow: broker XLSX/CSV import -> normalized operational tables -> FX and market sync ->
  `account_daily` rebuild -> derived reporting views -> Thymeleaf dashboard, Ghostfolio-compatible
  read APIs, Yahoo CSV export, Telegram notifications, and optional OpenAI answers/reports.
- Package boundaries:
  - `controllers`: UI, import/export/admin REST, Telegram bot, and Ghostfolio compatibility.
  - `services`: portfolio analytics, market/FX sync, reporting rebuilds, reset, tax, cash flow, and
    manual prices.
  - `services/imports`: broker parser SPI, XTB/IBKR implementations, and Yahoo export.
  - `services/openai`: Telegram AI replies, dashboard context, and scheduled reports.
  - `services/portfolio`: deterministic Telegram command routing.
  - `services/notifications`: digest and alert rules.
  - `infrastructure`: enums, JPA entities/repositories, and projection/view entities.
  - `clients`: native `java.net.http` clients for TwelveData and exchangerate.host.
  - `config`: security, scheduling, Thymeleaf, Telegram, and AI scheduling.

## Architectural Invariants

- Raw broker events are immutable source inputs.
- `account_daily` is the only persisted historical reporting fact table.
- Higher account, portfolio, monthly, KPI, allocation, currency, and symbol reports derive from
  `account_daily` through views or materialized views.
- Controllers must not classify broker operations or calculate financial values.
- Snapshot values and daily flow values are different concepts and must remain separate.
- Daily flow fields contain only events occurring on that date; they are never lifetime cumulative
  totals.
- Returns are compounded from daily returns. Never average return percentages.
- Missing FX, missing prices, or duplicate valuation matches must produce explicit diagnostics.
  Never assume an FX rate of 1 and never return an unconverted amount as a successful conversion.
- Before adding a reporting table, ask: **Can this be derived from `account_daily`?** If yes, use a
  view or materialized view.

## Build And Test

- No Maven wrapper is currently present. Use Maven directly: `mvn test`, `mvn clean package`,
  or `mvn spring-boot:run`.
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
  - `assets.symbol`: application/broker symbol, normally `TICKER.EXCHANGE`.
  - `assets.ticker`: TwelveData request symbol, often bare for US instruments.
  - `assets.ibkr`: IBKR alias or mapping.
  - `assets.yahoo`: Yahoo-specific symbol where needed.

## XTB Cash-Operation Semantics

XTB raw operation names are not sufficient to determine economic meaning. Classification must also
inspect amount sign, comment text, account relationship, and related nearby records.

Preserve every raw record. Store normalized meaning separately or derive it deterministically.

Apply the most specific rule first:

1. Explicit transfer-in or transfer-out comment.
2. Currency-conversion comment.
3. Correction or reversal comment.
4. Paired `SUBACCOUNT_TRANSFER` detection.
5. Raw operation mapping.
6. Sign-based subtype.
7. Unclassified fallback with a validation issue.

Required interpretations:

- Negative `DEPOSIT` with `Transfer out operation` -> `INTERNAL_TRANSFER_OUT`.
- Positive `DEPOSIT` with `Transfer in operation` -> `INTERNAL_TRANSFER_IN`.
- `TRANSFER` with `Transfer from <source> to <destination>` -> internal account transfer.
- `TRANSFER` identified as currency conversion -> `FX_CONVERSION`.
- Opposite paired `SUBACCOUNT_TRANSFER` rows -> internal bookkeeping; preserve both rows and group
  them deterministically.
- Negative `DIVIDEND` -> signed dividend reversal, not withdrawal.
- Positive `WITHHOLDING_TAX` -> signed tax reversal/refund, not income.
- Trade purchases and sales change cash and positions but are not external deposits or withdrawals.
- Zero-valued fee rows remain auditable and contribute zero.

At account level, internal transfers change cash. At portfolio level, matched internal transfer legs
must cancel and must not change contributed capital or investment performance.

Only normalized `EXTERNAL_DEPOSIT` and `EXTERNAL_WITHDRAWAL` records may populate the corresponding
`account_daily` flow fields.

## Database And Reporting Guardrails

- Current baseline migrations:
  - `V01.000__Initial_schema.sql`
  - `V01.001__Initial_data.sql`
  - `V01.002__checks_and_views.sql`
  - `V01.003__asset_price_history_import.sql`
- Currency is limited to `USD`, `EUR`, and `PLN`.
- Core operational tables include `accounts`, `portfolios`, `assets`, `asset_price_history`,
  `cash_operations`, `positions`, `import_history`, `exchange_rates`, and benchmark monthly closes.
- `account_daily` is the persisted reporting/read-model boundary rebuilt from operational tables.
- Higher summaries above `account_daily` must stay database-derived views/materialized views, not
  separately persisted application-owned tables.
- `positions` is the only position table. `OpenedPosition` and `ClosedPosition` map to it and
  are filtered by `close_time`; never recreate split position tables.
- `assets` owns current quote columns: `market_price`, `market_price_usd`, `price_source`, and
  `price_updated_at`. Projections and export require `market_price_usd`.
- `asset_price_history` distinguishes observed and estimated prices and preserves source,
  quality, scale, proxy, and currency metadata. Do not collapse those semantics.
- `account_daily` is live reporting data and feeds all higher reporting layers; it is not
  disposable legacy history.

`account_daily` uses `(account_id, snapshot_date)` as its identity.

Snapshot fields represent closing state:

- cash balance
- market value
- equity
- cost basis
- unrealized profit

Flow fields represent only that day's activity:

- deposits
- withdrawals
- dividends
- interest
- fees
- taxes
- realized profit

Monthly reports sum daily flows and compound daily returns. They must not sum cumulative lifetime
values.

Ordinary reporting views:

- `v_portfolio_daily`

Materialized reporting views:

- `account_monthly_mv`
- `portfolio_daily_mv`
- `portfolio_monthly_mv`
- `account_statistics`
- `portfolio_kpi_summary`
- `portfolio_asset_allocation`
- `portfolio_currency_breakdown`
- `symbol_performance`

Removed database surface must stay removed: `stocks`, `ticker_monthly`,
`monthly_position_summary`, `position_summary`, `portfolio_history`, old open-position
history, indicator tables, and obsolete diagnostic views.

## Market, FX, And Reporting Refresh

- `MarketService.fullPortfolioUpdate()` refreshes prices, applies them to open positions,
  re-values applicable positions/accounts, and starts the reporting refresh pipeline.
- TwelveData access lives in `clients/market/TwelveDataService`; symbols come from `assets`.
- Quote sync uses `MarketService.CHUNK_SIZE = 8` and `app.market.chunk-pause-ms` defaults to
  120 seconds.
- `app.market.skip-non-us-listings=true` by default; manual prices cover unavailable listings.
- `REMX.UK` currently has source-specific quote normalization. Preserve behavior until it is replaced
  by explicit asset/source metadata such as quote unit, multiplier, currency, and provider symbol.
  Do not spread symbol-specific hard-coded scaling rules.
- `AssetPriceFallbackService` uses historical prices while preserving provenance and quality.
- FX refresh fetches USD-base rates from exchangerate.host and derives EUR/PLN crosses locally.
  Rows are stored per currency pair at month start.
- Currency conversion may use direct or inverse historical rates. If neither exists, fail the
  conversion or mark the result unavailable and create a validation issue. Never return the original
  amount as though conversion succeeded.

The authoritative reporting refresh order is:

1. Refresh prices and FX inputs.
2. Rebuild `account_daily` deterministically.
3. Refresh dependent views/materialized views.
4. Run reporting validation.
5. Invalidate application caches if applicable.

`PortfolioProjectionService.recalculateAll()` must follow this dependency order. Do not rebuild
monthly/statistics projections independently from imported rows, and do not patch dashboard math to
compensate for stale reporting data.

## Reporting Validation

Reporting rebuilds must detect or expose at least:

- `equity != cash_balance + market_value`
- `unrealized_profit != market_value - cost_basis`
- missing prices
- missing FX rates
- duplicate price matches
- duplicate position/lot joins
- external flows derived from internal transfers
- unmatched internal transfers
- unclassified non-zero operations
- implausible valuation jumps without trade, split, or quantity justification

Do not hide anomalies by clipping values, substituting arbitrary prices, or forcing returns into a
range. Fix or surface the source-data or valuation problem.

## Dashboard, Export, Telegram, And Notifications

- Dashboard template: `classpath:/static/dashboard/dashboard.html`; `/` returns
  `templates/home.html`.
- Dashboard totals prefer `mv_portfolio_kpi_summary` and are enriched with account balances,
  currencies, taxes, performance, dividends, benchmark data, and FX cards.
- Preserve these UI rules:
  - Exclude near-zero accounts from account lists and benchmark choices.
  - Benchmark tooltips show percent and absolute P/L.
  - Dividend Top 10 shows nine symbols plus `Other` when more than ten symbols exist.
  - Dividend popup symbols are plain text, without avatars.
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
  - Market close update/reporting refresh: weekdays 22:01.
  - Notification digest and alerts: weekdays 22:22.
  - AI reports: monthly day 1 at 09:00; quarterly day 2 at 09:15; annual January 3 at 09:30.

## Change Discipline

- Before deleting an entity, table, view, or endpoint, check Java, migrations, dashboard assets,
  Ghostfolio controllers, schedulers, reset/export paths, and tests.
- Do not reintroduce removed APIs or tables as shortcuts.
- Update this file when a change adds or removes endpoints, migrations, schedulers, core tables,
  reporting invariants, or externally visible configuration.
- Keep `ROADMAP.md` future-facing. Completed work belongs in history/release notes, not among
  active priorities.
