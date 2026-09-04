# Investory

Investory is a personal investment and financial-planning system. It consolidates brokerage investments
and manually managed long-term assets into a unified financial profile, separates contributed capital
from investment performance, and supports deterministic retirement planning with historical, live, and
projected years.

## What Investory answers

### What do I own today?

- Broker accounts, market positions, and cash.
- Manual long-term assets: real estate, bonds, deposits, cash reserves, and other assets.
- A read-only unified `InvestmentProfile` for planning and simulation.

### How much did I contribute and earn?

- External deposits and withdrawals, distinct from internal transfers and FX conversions.
- Realized/unrealized P/L, dividends, interest, and canonical portfolio performance.

### How am I performing?

- Monthly performance, historical account value, attribution, currency exposure, positions, and SPY
  comparison for selected accounts and periods.

### Can my assets fund future spending?

- Deterministic retirement simulation using spending need, rental/passive income, pension, safe reserve,
  asset returns, and configurable funding strategy.

### Am I following the plan?

- Past/Actual annual snapshots, Current/Live actual-versus-expected tracking, and Future/Projected
  simulation years with an explicit approval/reopen workflow.

## What works today

- Consolidated portfolio value and cash across multiple broker accounts.
- External deposits and withdrawals separated from investment earnings.
- Realized P/L, unrealized P/L, dividends, interest, and a separate capital-gains tax estimate.
- Monthly performance, historical account value, attribution, currency exposure, and position views.
- SPY comparison for selected active accounts and dashboard periods.
- IBKR and XTB statement imports with checksum-based duplicate detection and idempotent exact-file reprocessing.
- Scheduled market-price and FX refresh, historical price storage, and manual price overrides.
- Yahoo Finance export and developer reconciliation and pipeline-validation tooling.
- Manual long-term assets: real estate, bonds, contractual deposits, planning-only cash reserves, and
  generic other assets.
- `InvestmentProfile` aggregation of brokerage and manual assets without writing either source.
- Deterministic retirement simulation with independent inflation, rental-income-growth, spending-growth,
  and asset-return assumptions.
- Configurable Simple Waterfall and Reserve + equity harvest funding strategies. New plans default to a
  five-year safe-reserve target, 7% harvest gate, 75% eligible-gain fraction, and enabled emergency
  equity withdrawal.
- Contractual maturity redemption, PAY_OUT versus CAPITALIZE interest treatment, and planning-only
  manual cash reserve funding before market cash and fixed income.
- Planning display/input currency selection: PLN (default), USD, or EUR; planning storage remains
  canonical USD/base amounts.
- Natural money and percentage presentation; percentage inputs use percentage points.
- Past/Live/Projected planning timeline, historical annual approval/reopen, and stable current-year
  expected-versus-actual baseline tracking.

Reconciliation is currently an operator/developer workflow exposed through the dedicated
`/dashboard/reconciliation` page and internal REST adapter, rather than normal dashboard enrichment.
See
[`docs/quality/reconciliation.md`](docs/quality/reconciliation.md) for the validation contract,
implemented tooling, and known gaps.

## Supported brokers, currencies, and asset limitations

| Area | Current scope |
|------|---------------|
| IBKR | `.csv` activity statements and transaction-only exports. Activity-statement Open Positions sections can update reconstructed positions. Net Asset Value sections are not imported. |
| XTB | `.xlsx` statements and `.zip` statement packages. |
| Automatic detection | `.csv` is treated as IBKR; `.xlsx` and `.zip` are treated as XTB. The broker can also be selected explicitly through the import endpoint. |
| Currencies | `USD`, `EUR`, and `PLN`. |
| Market data | TwelveData supplies automatic quotes, historical prices, and SPY monthly closes. The scheduled market refresh runs on weekdays at 22:01 Europe/Warsaw. |
| FX data | exchangerate.host supplies USD-based daily rates; EUR and PLN cross-rates are derived locally. Each observation keeps its provider date and provenance. |
| Asset coverage | Imported asset symbols must resolve to exactly one existing canonical asset; unknown or ambiguous mappings fail instead of creating guessed assets. Automatic quote coverage depends on TwelveData mappings and plan limits. Non-US listings are skipped by default and may require manual prices. Real-time websocket pricing is not implemented. |

### Manual long-term assets

| Type | Current behavior |
|------|------------------|
| `REAL_ESTATE` | Current value, effective-dated income/expense periods, rental income, explicit tax base, and value growth. |
| `BOND` | Contractual fixed income with maturity/redemption and `PAY_OUT` or `CAPITALIZE` interest treatment. |
| `DEPOSIT` | Contractual deposit; locked until its configured maturity. |
| `CASH_RESERVE` | Planning-only manual liquid reserve; immediately spendable and used before market cash/fixed income under Reserve + Harvest. |
| `OTHER` | Generic long-term asset; not automatically sold by retirement simulation. |

## How calculations work

The concise table below is an overview. The canonical financial contract is
[`docs/domain/portfolio-accounting.md`](docs/domain/portfolio-accounting.md).

| Metric | Calculation | Period behavior |
|--------|-------------|-----------------|
| Portfolio value | Latest account equity: cash plus open-position market value, converted to the portfolio base currency | Current snapshot |
| Net contributions | External deposits minus external withdrawals; portfolio-level internal transfers and FX conversions are excluded | Since inception |
| Investment earnings | Realized P/L + unrealized P/L + dividends + recorded dividend withholding tax + net interest | Since inception, with unrealized P/L valued at the current snapshot |
| Headline ROI | `investment earnings / net contributions × 100` | Since inception; shown as zero when net contributions are not positive; not annualized and not TWR, MWR, or XIRR |
| Selected-period profit | Sum of monthly portfolio profit from the selected start month | `1M`, `3M`, `YTD`, `1Y` (default), `3Y`, `5Y`, or `MAX`; filtering is month-granular |
| Benchmark comparison | Cumulative monthly portfolio profit versus the gain or loss from investing each selected account's starting equity in SPY using monthly closes | Rebased to the selected dashboard period within the configured comparison history; later cash-flow-timed SPY purchases are not simulated |
| Tax treatment | Recorded dividend withholding is included in investment earnings as imported. Interest is net of recorded interest tax. The estimated Polish 19% current-year capital-gains tax applies eligible loss carry-forward and is displayed separately | The capital-gains estimate is not deducted from headline earnings or ROI and is not a tax filing |

The period selector filters monthly performance and rebases the benchmark comparison. It does not
change current portfolio value, since-inception contributions, headline investment earnings, or
headline ROI.

## Retirement planning

At a high level, recurring spending need minus rental/passive income and pension gives portfolio funding
need. Under Reserve + Harvest, funding uses:

```text
manual cash reserve -> market cash -> spendable fixed income -> optional emergency equity
```

In strong equity years, eligible positive equity gain first replenishes the safe reserve, but never beyond
its target; any remainder may refill the three-year bond ladder. The configurable deterministic defaults
are five years of recurring funding gap, a 7% return gate, and a 75% eligible-gain fraction. Below the
gate there is no normal harvest; transfers do not normally consume equity principal. These are planning
defaults, not financial advice.

The canonical contract, formulas, liquidity policy, and scenario semantics are in
[`docs/domain/retirement-simulation.md`](docs/domain/retirement-simulation.md).

## Planning calculations

| Metric | Concise calculation |
|--------|---------------------|
| Recurring funding gap | `max(core + discretionary - recurring passive income - pension, 0)` |
| Required portfolio funding | `max(total expenses - passive income - pension - event income, 0)` |
| Safe reserve | Manual cash reserve + market cash + spendable market fixed income |
| Safe reserve target | Recurring funding gap × safe-reserve years |
| Eligible equity gain | Positive investment return on equity remaining after funding |
| Equity-to-reserve transfer | Limited by reserve shortfall, eligible gain fraction, and equity balance |
| Actual portfolio withdrawal | Amount actually funded under the configured withdrawal policy |
| Unfunded amount | Required funding − actual withdrawal |

## Planning timeline

```text
Past                 Current               Future
Actual               Live                  Projected
approved snapshot    actual vs expected    deterministic simulation
```

- **Past/Actual**: approved annual planning snapshot, derived from authoritative sources where
  possible and corrected only with planning-domain values; immutable until explicitly reopened.
- **Current/Live**: current portfolio/manual assets compared with a stable expected baseline.
- **Future/Projected**: deterministic simulation only; never persisted as historical facts.

Planning reads accounting through `InvestmentProfile`; it does not mutate positions, cash operations,
`account_daily`, prices, FX history, or API response data. Simulated reserve transfers are
not real transactions. See [`docs/domain/planning-timeline.md`](docs/domain/planning-timeline.md).

### Financial interpretation

- Internal transfers change account cash but do not change portfolio contributed capital.
- Currency conversion is not a deposit, withdrawal, or investment profit.
- Broker corrections and reversals retain their imported signs.
- When no usable FX rate exists, or a daily/reference rate is outside the four-day safety window, the affected calculation
  fails with an `FX rate unavailable` error. An unconverted amount is not silently treated as target
  currency.

## Current limitations

- Exact files are detected using broker plus file SHA-256. For IBKR and XTB, an existing successful
  batch creates a new linked reprocess attempt while the parser and derived projections are rerun
  to repair reconstructed state; the original attempt remains immutable.
- Overlapping but non-identical exports rely on stable broker identifiers or synthetic row IDs.
  Partial-overlap idempotency is not a formal guarantee and needs stronger validation.
- SPY is the only benchmark. Benchmark selection is limited to accounts and dashboard period.
- Portfolios belong to application users through the required `app_users` -> `portfolios` relation.
  Portfolio-backed HTTP reads are ownership-checked; calculations and reports require a portfolio scope.
- Uploaded broker artifacts and parsed broker rows are retained as immutable provenance evidence in
  `import_source_files` and `import_source_rows`; normalized portfolio rows remain the accounting
  truth consumed by projections and reporting. Failed text payload previews are capped at 8 KB.
- Backups are operator-managed. Back up PostgreSQL and retain original broker exports separately; a
  persistent Docker volume is not a backup.
- Reconciliation remains operator/developer tooling based on the current-state application report,
  `GoldenRebuildIT`, `ReconRunner`, local broker files, JDBC checks, and manual verification. The
  dashboard now maps the available portfolio data-quality and risk
  indicators through its dashboard application view models; these are reporting indicators, not a
  replacement for reconciliation tooling or a separate financial source of truth. The standalone
  `ReconRunner` IBKR C1 path checks aggregate source-to-ledger cash conservation only; the deterministic
  golden rebuild additionally checks operation, currency, business date, row count, and signed amount.
  Dashboard checkpoint C6 has automated database evidence coverage, secondary checkpoint C7 is
  optional when no Yahoo snapshot exists, and repository branch protection must still require the
  dedicated golden job before treating it as a release gate.
- The capital-gains estimate follows Polish assumptions. Other tax jurisdictions are not modeled.
- Retirement planning is a deterministic annual model: it has no Monte Carlo, sequence-of-returns
  randomness, automated real-world trades, automatic real-estate sale, or early contractual redemption.
- Current-year planning uses a deterministic remaining-year bridge, not detailed monthly cash-flow
  simulation. Planning corrections are annual planning values, not personal expense transactions.
- Simulation assumptions are planning inputs, not forecasts. Market aggregation is currently shared;
  planning is not safe for independently scoped multi-portfolio market data.

## Architecture overview

```text
Broker Files
        │
        ▼
Normalized Accounting
        │
        ▼
account_daily / reporting views
        │
        ▼
Portfolio services ───────────────> Dashboard / APIs / Exports
        │
        └───────────────┐
                        ▼
Manual Long-Term Assets ──> InvestmentProfile ──> Profile page
                                      │
                                      └──> Planning -> Retirement simulation
                                                       -> Past / Live / Projected
```

Manual long-term assets are planning/manual-domain data; they do not enter brokerage accounting.
Real-estate rentals use rental contracts as the runtime source of truth. Reviewed Retirement plans
freeze normalized Long-Term economics; live edits affect current views until explicit rebaseline.
`import_history` records import metadata, status, counts, and failures. Normalized rows retain available
broker identifiers and original asset symbols for traceability, but Investory is not an immutable raw
event store.

`account_daily` is the persisted reporting boundary. Each row represents one account on one date and
combines end-of-day state with that day's flows. Monthly and portfolio summaries are derived from it
through database views and materialized views.

The application stack is Java 25, Spring Boot 4.1, PostgreSQL, Spring Data JPA, Thymeleaf, Chart.js,
and Maven. Stable architecture and reporting data lineage live in
[`docs/architecture/`](docs/architecture/); financial semantics live in
[`docs/domain/`](docs/domain/). [`AGENTS.md`](AGENTS.md) is intentionally small and routes coding
agents to the relevant source of truth.

## Exact local setup

Requirements:

- Java 25 or newer
- PostgreSQL
- Maven

For a reproducible Java, Maven, and PostgreSQL environment, use the Dev Container described in
[`docs/development/dev-container.md`](docs/development/dev-container.md).

### 1. Clone the repository

```bash
git clone https://github.com/spider-su/investory.git
cd investory
```

### 2. Create and configure PostgreSQL

Flyway creates the `investory` schema, tables, views, and initial data. It does not create the
PostgreSQL database itself.

```bash
psql -U postgres -c "CREATE DATABASE investory;"
```

The default local datasource is:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/investory
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
```

Set different values when your PostgreSQL host, database, or credentials differ.

### 3. Start the application

```bash
./mvnw -pl app -am spring-boot:run
```

The base configuration reads `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` and otherwise uses the local
defaults shown above. The Dev Container also activates the `local` profile as a development convention,
but that profile is not required to select the local datasource.

On startup, Flyway creates the `investory` schema when needed and applies migrations from
`app/src/main/resources/sql/migration`.

Open `http://localhost:8080`. The development administrator credentials are:

```text
username: admin
password: change-me-admin
```

These credentials are for local development only.

### 4. Import the first broker statement

Use the dashboard import form or the REST API:

```bash
curl --fail-with-body \
  -u admin:change-me-admin \
  -F "file=@/path/to/statement.csv" \
  http://localhost:8080/import
```

Use `/import/broker/ibkr` or `/import/broker/xtb` to select the broker explicitly.

Asset-bearing rows must resolve to exactly one existing canonical asset. Add or correct the asset
mapping before retrying an import that reports an unknown or ambiguous source symbol; see
[`docs/domain/asset-identity-and-money.md`](docs/domain/asset-identity-and-money.md).

### 5. Refresh market data and projections

Set provider keys before application startup when automatic quote and FX refresh is required:

```bash
export TWELVEDATA_API_KEY=your-key
export EXCHANGERATE_API_KEY=your-key
```

Refresh current prices and portfolio projections:

```bash
curl --fail-with-body \
  -u admin:change-me-admin \
  -X POST \
  http://localhost:8080/admin/refresh-prices
```

Rebuild projections without requesting new market prices:

```bash
curl --fail-with-body \
  -u admin:change-me-admin \
  -X POST \
  http://localhost:8080/admin/rebuild-monthly
```

## Security and deployment status

- HTTP Basic uses in-memory `ADMIN` and `USER` credentials.
- `POST`, `PUT`, and `DELETE` routes require the `ADMIN` role. Read routes require authentication by
  default, including the dashboard and read-only API routes. Set
  `APP_SECURITY_READ_AUTHENTICATION_REQUIRED=false` only for a trusted local network.
- `/actuator/health` is public for liveness checks; health details are shown only to authenticated users.
- CSRF protection is currently disabled.
- Portfolio routes are scoped by `portfolioId` and checked against the authenticated user's portfolio
  ownership. Administrators retain explicit cross-portfolio access for operational diagnostics.
- The `prod` profile requires explicit database credentials and all four `APP_SECURITY_*` variables.
- This status is appropriate for a trusted single-owner deployment behind network controls. Do not
  expose it as a public multi-user service without addressing the security and isolation roadmap items.

Production example:

```bash
export DB_URL=jdbc:postgresql://database-host:5432/investory
export DB_USERNAME=investory
export DB_PASSWORD=replace-me
export APP_SECURITY_ADMIN_USERNAME=admin
export APP_SECURITY_ADMIN_PASSWORD=replace-me
export APP_SECURITY_USER_USERNAME=user
export APP_SECURITY_USER_PASSWORD=replace-me

./mvnw -pl app -am spring-boot:run "-Dspring-boot.run.profiles=prod"
```

Use deployment secrets or a secret manager. Do not commit credentials.

## Documentation links

- [`README.md`](README.md): product scope, calculations, limitations, and setup.
- [`AGENTS.md`](AGENTS.md): single coding-agent entry point and task-to-document router.
- [`docs/README.md`](docs/README.md): complete documentation index and source-of-truth map.
- [`docs/domain/`](docs/domain/): canonical financial/domain contracts.
- [`docs/architecture/`](docs/architecture/): stable architecture and reporting data flow.
- [`docs/development/`](docs/development/): testing and development environment procedures.
- [`docs/quality/`](docs/quality/): reconciliation and validation contracts.
  Manual QA plans: [`dashboard`](docs/quality/01-investment-dashboard-manual-qa.md),
  [`long-term assets`](docs/quality/02-long-term-assets-manual-qa.md),
  [`investment profile`](docs/quality/03-investment-profile-manual-qa.md),
  [`retirement simulation`](docs/quality/04-retirement-planning-simulation-manual-qa.md),
  [`integrations`](docs/quality/05-integrations-manual-qa.md), and
  [`reconciliation/data quality`](docs/quality/06-reconciliation-data-quality-manual-qa.md).
- [`ROADMAP.md`](ROADMAP.md): future work and current priorities.
- [`CHANGELOG.md`](CHANGELOG.md): completed work and documentation history.

## Roadmap

[`ROADMAP.md`](ROADMAP.md) is the canonical living plan for future work and current priorities.
Completed work is recorded in [`CHANGELOG.md`](CHANGELOG.md), not retained as crossed-out roadmap
items.

## License

[MIT License](LICENSE)
