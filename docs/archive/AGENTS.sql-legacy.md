# SQL And Data Agent Guidance

Applies to Flyway, PostgreSQL, reporting views, projections, reconciliation, and live DB analysis.
Read together with root `AGENTS.md`.

## Flyway Discipline

- PostgreSQL + Flyway use schema `investory`.
- `spring.jpa.hibernate.ddl-auto=none`; schema changes go through versioned migrations under
  `src/main/resources/sql/migration`.
- During active local migration work, prefer correcting the intended migration instead of stacking
  patch migrations.
- Once migration history is shared/shipped, keep it append-only and add a follow-up migration.

## Core Data Model

- Supported currencies are `USD`, `EUR`, and `PLN`.
- Core raw data includes `accounts`, `portfolios`, `assets`, `asset_price_history`,
  `cash_operations`, `positions`, `import_history`, `exchange_rates`, and benchmark closes.
- `positions` is the only position table. `OpenedPosition` and `ClosedPosition` are filtered views
  of the same table via `close_time`; do not recreate split position tables.
- `assets.id` is canonical identity for transactional/price foreign keys.
- `assets` owns current quote fields: `market_price`, `market_price_usd`, `price_source`,
  `price_updated_at`. Projections/export require `market_price_usd`.
- `asset_price_history` preserves observed/estimated state plus source, quality, scale, proxy, and
  currency metadata. Do not collapse these semantics.

## Position And Money Invariants

- Position money has explicit `price_currency`, `cost_currency`, `profit_currency`, and
  `commission_currency`; never infer these from `accounts.currency`.
- Stored `volume` is absolute. Use the shared signed-quantity helper/function for BUY/SELL direction.
- `positions.settlement_model` is per position. The same account/symbol may contain cash-settled
  shares and result-only CFDs.
- Never value `RESULT_ONLY` positions at full notional.
- Raw broker symbols in `source_asset_symbol` are provenance, not identity.

## Projection Boundary

- `account_daily` is the persisted daily projection/read-model boundary rebuilt from raw tables.
  It is live reporting data, not disposable legacy history.
- Higher summaries above `account_daily` remain database-derived views/materialized views, not
  separately persisted application-owned tables.
- Materialized views are reporting snapshots, not independent sources of truth.
- Refresh/rebuild projections after imports, price changes, FX changes, or cash classification
  changes instead of patching derived totals directly.

## Reporting Lineage

- `normalized_cash_operations` is the canonical classified ledger over `cash_operations`; it adds
  categories, sign rules, account/base-currency amounts, and reconciliation metadata.
- `account_daily` combines position results, cash-ledger movements, prices, and FX by account/date.
- `v_portfolio_daily` is the database-derived portfolio daily layer above `account_daily`.
- `account_statistics` summarizes latest `account_daily` plus normalized cash flows.
- `portfolio_kpi_summary` rolls account/projection values into headline totals.
- `portfolio_asset_allocation` rolls current open positions into symbol totals.
- Direct `cash_operations` reads are valid for ledger-detail supplements/reconciliation, not as a
  second position-valuation source. Apply the same `cash_only` and `CashFlowAggregator` semantics.

## Reporting Surfaces

Ordinary reporting views:
- `v_portfolio_daily`
- `reporting_trade_settlement_reconciliation_by_account`

Materialized reporting views:
- `account_monthly_mv`
- `portfolio_daily_mv`
- `portfolio_monthly_mv`
- `account_statistics`
- `portfolio_kpi_summary`
- `portfolio_asset_allocation`
- `portfolio_currency_breakdown`
- `symbol_performance`
- `reporting_trade_settlement_reconciliation`

Removed database surface must stay removed unless a real product requirement returns:
`stocks`, `ticker_monthly`, `monthly_position_summary`, `position_summary`, `portfolio_history`,
old open-position history, indicator tables, and obsolete diagnostic views.

## Import/Write Behavior

- Hibernate import writes use JDBC batches of 50 with ordered inserts/updates.
- Asset and `account_daily` sequence allocation windows are 50.
- Cash operations and positions use application-assigned IDs during broker imports; do not assume
  their sequences are used.

## Local Database Investigation

- Use the `local` Spring profile configuration and connect through JDBC for live DB checks.
  Do not switch profiles, infer credentials elsewhere, or assume/use `psql` first.
- For data discrepancies, validate live DB state before concluding from screenshots or code.
- Check Flyway version, required schema columns, projection row counts, and date ranges before
  interpreting results. Empty/stale projections or an old schema are not evidence about current UI.
- For suspicious jumps/peaks, compare the same account/date across:
  `account_daily`, `account_monthly_mv`, `cash_operations`/`normalized_cash_operations`,
  positions, asset prices, and FX.
- Separate external cash flow from market/valuation movement and state clearly when local data
  cannot reproduce the reported discrepancy.
