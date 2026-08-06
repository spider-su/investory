# Investory

> Portfolio intelligence for long-term investors.
>
> Track investments across brokers, currencies, and asset classes by importing broker files
> into a normalized transaction ledger and deriving consistent portfolio analytics.

## The three questions

Investory is organized around three investor questions.

### How much is my portfolio worth?

Current portfolio value combines account cash and the market value of open positions in the portfolio
base currency. Broker accounts remain visible separately, while the headline shows the consolidated
portfolio.

### How much did I contribute, and how much did investments earn?

External deposits and withdrawals are reported separately from investment results. Internal transfers
and currency conversions move cash between accounts or currencies but do not change portfolio
contributed capital. Investment earnings are broken into realized profit, unrealized profit, dividends
net of recorded withholding tax, and interest.

### How did I perform versus the benchmark?

Investory compares selected active accounts with a benchmark over the dashboard period. The current
benchmark is SPY; the benchmark symbol itself is not yet selectable.

Investory combines normalized broker transactions with market prices and FX rates to reconstruct
account state and derive consistent daily and monthly analytics. Import batches and available source
identifiers are retained for traceability; the original broker files are not stored as a complete
immutable event archive.

## Calculation semantics

| Metric | Calculation | Period behavior |
|--------|-------------|-----------------|
| Portfolio value | Latest account equity: cash plus open-position market value, converted to the portfolio base currency | Current snapshot |
| Net contributions | External deposits minus external withdrawals; portfolio-level internal transfers and FX conversions are excluded | Since inception |
| Investment earnings | Realized P/L + unrealized P/L + dividends + recorded dividend withholding tax + net interest | Since inception, with unrealized P/L valued at the current snapshot |
| Headline ROI | `investment earnings / net contributions × 100` | Since inception; shown as zero when net contributions are not positive; not annualized and not TWR, MWR, or XIRR |
| Selected-period profit | Sum of monthly portfolio profit from the selected start month | `1M`, `3M`, `YTD`, `1Y` (default), `3Y`, `5Y`, or `MAX`; filtering is month-granular |
| Benchmark comparison | Cumulative monthly portfolio profit versus the gain or loss from investing each selected account's starting equity in SPY using monthly closes | Rebased to the selected period within the configured comparison history (`app.benchmark.comparison-start`, default `2026-01`); does not simulate later cash-flow-timed SPY purchases |
| Tax treatment | Recorded dividend withholding is included in investment earnings. Interest is net of recorded interest tax. The estimated 19% current-year capital-gains tax, including eligible loss carry-forward, is displayed separately and is not deducted from headline earnings or ROI | Capital-gains estimate covers the current tax year |

The period selector filters monthly performance and rebases the benchmark comparison. It does not
change current portfolio value, since-inception contribution totals, headline investment earnings, or
headline ROI.

---

## Features

### Portfolio overview

- Portfolio value
- Cash balance
- Since-inception investment earnings and ROI
- Multi-currency support
- Multi-currency valuation using scheduled current rates and historical monthly FX rates
- Multiple broker accounts

### Performance analytics

- Total Profit
- Realized Profit/Loss
- Unrealized Profit/Loss
- Dividend Income
- Separate current-year capital-gains tax estimate
- Selected-period monthly performance
- SPY benchmark comparison
- Historical portfolio value

### Position analysis

- Current positions
- Closed positions
- Position history
- Currency exposure
- Asset allocation
- Winners / Losers
- Performance by account
- Risk exposure summary
- Daily and monthly performance attribution
- Data quality status

### Data management

- Import broker statements
- Manual price overrides
- Historical price synchronization
- Yahoo Finance export
- Developer reconciliation and pipeline-validation tooling

Reconciliation is currently developer-facing rather than an end-user workflow. The staged checks
in [`docs/pipeline-testing-plan.md`](docs/pipeline-testing-plan.md) use `ReconRunner`, local broker
files, JDBC-based database checks, and manual verification. IBKR C1 reconciliation is not implemented,
C6 dashboard validation is not automated, C7 secondary-surface checks are optional, and the complete
golden pipeline is not yet enforced as a CI gate.

---

## Supported brokers

Currently implemented:

- Interactive Brokers (IBKR)
- XTB

Broker-specific importers map supported statement fields into a common operational model. Imported
rows retain available broker identifiers and original asset symbols for traceability, while reporting
uses canonical assets and normalized operations.

## Current scope and operational assumptions

| Area | Current behavior |
|------|------------------|
| Statement formats | IBKR `.csv` activity statements and transaction-only exports; XTB `.xlsx` statements and `.zip` packages. Automatic broker detection is based on the file extension. |
| Duplicate and overlapping imports | An exact previously applied file is skipped using its broker and SHA-256 hash. Different exports with overlapping date ranges can still double-count operations; partial-overlap idempotency is not implemented. |
| Market data | TwelveData supplies automatic quotes and SPY monthly closes. The scheduled market refresh runs on weekdays at 22:01 Europe/Warsaw and rebuilds portfolio projections. Non-US listings are skipped by default and may need manual prices. |
| FX data | exchangerate.host supplies USD-based rates; EUR and PLN cross-rates are derived locally. FX refresh runs on weekdays at 15:00 Europe/Warsaw, and rates are stored at month start. |
| Supported currencies | `USD`, `EUR`, and `PLN` only. |
| Ownership model | Investory uses one shared portfolio dataset with no per-user data isolation. It is intended for one trusted owner, not as a multi-tenant service. |
| Retained import data | Investory stores normalized positions, cash operations, assets, import batch metadata, hashes, counts, and available broker identifiers or source symbols. Original broker files are not retained; failed text payload previews are limited to 8 KB. |
| Backups | Investory has no application-managed backup and restore workflow. Back up PostgreSQL with normal database tooling and retain original broker exports separately. A persistent Docker volume is not a backup. |
| Benchmark | SPY is the only benchmark. Active accounts and dashboard periods can be selected, but the benchmark symbol cannot. |
| Tax assumptions | The capital-gains estimate implements the Polish 19% current-year tax assumption and applies eligible losses from the previous five years. Imported dividend withholding and interest taxes use broker data. This is an estimate, not a tax filing. |

---

## Technology

| Layer | Technology |
|--------|------------|
| Backend | Java 25 |
| Framework | Spring Boot 4.1 |
| Database | PostgreSQL |
| ORM | Spring Data JPA |
| Frontend | Thymeleaf |
| Charts | Chart.js |
| Build | Maven |

---

## Architecture

Investory separates broker-file ingestion, normalized portfolio data, and reporting projections.

```text
Broker Files
        │
        ▼
Import Audit + Normalized Ledger
        │
        ▼
Positions + Cash Operations + Assets
        │
        ▼
Historical Prices + FX
        │
        ▼
account_daily
        │
        ▼
Views / Materialized Views
        │
        ▼
Dashboard / APIs / Exports
```

`import_history` records each import batch's broker, file name, SHA-256, status, row counts, and failure
details. Normalized rows retain broker identifiers and source asset symbols where available. Failed
text payload previews are limited to 8 KB; Investory does not retain every imported file as an
immutable raw-event store.

`account_daily` is the persisted historical reporting boundary. Each row represents one account on one
date.

It combines end-of-day snapshots such as cash, market value, equity, cost basis, and unrealized profit
with flows that occurred on that date, such as deposits, withdrawals, dividends, interest, fees, taxes,
and realized profit.

Daily flow columns are not cumulative. Monthly and portfolio reporting derives from `account_daily`
through database views and materialized views.

This provides:

- deterministic projection calculations
- repeatable projection rebuilds from normalized data
- import traceability through batch metadata and preserved source identifiers
- easier reconciliation
- consistent account and portfolio analytics
- safe monthly aggregation

### Financial interpretation

- Internal transfers change account cash but do not change portfolio contributed capital.
- Currency conversion is not a deposit, withdrawal, or investment profit.
- Broker corrections and reversals retain their signs.
- Current FX rates refresh on weekdays at 15:00 Europe/Warsaw. Rates are stored at the first day
  of each month and reused for valuations within their supported age window.
- When no usable FX rate exists, or the newest rate is more than 45 days old, the affected
  calculation fails with an `FX rate unavailable` error. Investory does not silently treat an
  unconverted amount as if it were already in the target currency.
- Headline ROI is a simple since-inception capital ratio. It is not TWR, MWR, XIRR, or an annualized return.

## Dashboard

The dashboard includes:

- Portfolio Value
- Available Cash
- Since-inception ROI
- Profit
- Unrealized P/L
- Realized P/L
- Dividend Income
- Monthly Returns
- Top Gainers
- Top Losers
- Historical Performance
- Currency Breakdown
- Position Tables
- Import Tools
- Data Quality and Risk Exposure
- Daily and Monthly Attribution

## Documentation

- `README.md`: product overview and local run basics.
- `AGENTS.md`: canonical engineering guide for current architecture, DB, API surface, invariants,
  and workflow.
- `ROADMAP.md`: future work only.
- `docs/`: focused supporting documents such as compatibility reports and refactor notes.

## Roadmap

[`ROADMAP.md`](ROADMAP.md) is the canonical living plan for future work and current priorities.
Roadmap items are not duplicated here because they change more frequently than the product overview.

## Running locally

Requirements:

- Java 25 or newer
- PostgreSQL
- Maven

For a reproducible environment with Java, Maven, and PostgreSQL already configured, use the Dev
Container described in [`docs/DEV_CONTAINER.md`](docs/DEV_CONTAINER.md).

### 1. Clone the repository

```bash
git clone https://github.com/spider-su/investory.git
cd investory
```

### 2. Create and configure PostgreSQL

Flyway creates the `investory` schema, tables, views, and initial data. It does **not** create the
PostgreSQL database itself.

Create the default local database:

```bash
psql -U postgres -c "CREATE DATABASE investory;"
```

Configure a different database through environment variables:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/investory
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
```

The values above match the application defaults. Set them explicitly when your PostgreSQL host,
database, or credentials differ.

### 3. Start the application

Always activate the `local` Spring profile for a normal local run:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Do not omit the profile outside the Dev Container. Without it, the application can connect to the
default datasource instead of the intended local database and produce misleading Flyway errors.

On startup, Flyway creates the `investory` schema when needed and applies migrations from
`src/main/resources/sql/migration`.

Open:

```text
http://localhost:8080
```

The default local administrator credentials are:

```text
username: admin
password: change-me-admin
```

These defaults are for local development only.

### 4. Import the first broker statement

Use the dashboard import tools, or upload a file through the REST API. Auto-detection accepts:

- `.csv` for IBKR
- `.xlsx` or `.zip` for XTB

```bash
curl --fail-with-body \
  -u admin:change-me-admin \
  -F "file=@/path/to/statement.csv" \
  http://localhost:8080/import
```

To select the broker explicitly, use `/import/broker/ibkr` or `/import/broker/xtb`.

### 5. Refresh prices and rebuild projections

Set the market-data keys before starting the application when automatic quote and FX refresh is
required:

```bash
export TWELVEDATA_API_KEY=your-key
export EXCHANGERATE_API_KEY=your-key
```

After the first import, refresh current prices, positions, daily projections, and reporting views:

```bash
curl --fail-with-body \
  -u admin:change-me-admin \
  -X POST \
  http://localhost:8080/admin/refresh-prices
```

To rebuild projections without requesting new market prices:

```bash
curl --fail-with-body \
  -u admin:change-me-admin \
  -X POST \
  http://localhost:8080/admin/rebuild-monthly
```

### Environment variables

| Variable | Required | Purpose |
|----------|----------|---------|
| `DB_URL` | Production; local when defaults differ | PostgreSQL JDBC URL |
| `DB_USERNAME` | Production; local when defaults differ | PostgreSQL username |
| `DB_PASSWORD` | Production; local when defaults differ | PostgreSQL password |
| `APP_SECURITY_ADMIN_USERNAME` | Production | Administrator username |
| `APP_SECURITY_ADMIN_PASSWORD` | Production | Administrator password |
| `APP_SECURITY_USER_USERNAME` | Production | Read-only username |
| `APP_SECURITY_USER_PASSWORD` | Production | Read-only password |
| `TWELVEDATA_API_KEY` | For automatic market-price refresh | TwelveData API key |
| `EXCHANGERATE_API_KEY` | For automatic FX refresh | exchangerate.host API key |

Telegram and OpenAI variables are optional. Their integrations remain disabled until explicitly
enabled.

### Production profile

Production must not use the local security defaults. Set the database and all four
`APP_SECURITY_*` variables, then start with the `prod` profile:

```bash
export DB_URL=jdbc:postgresql://database-host:5432/investory
export DB_USERNAME=investory
export DB_PASSWORD=replace-me
export APP_SECURITY_ADMIN_USERNAME=admin
export APP_SECURITY_ADMIN_PASSWORD=replace-me
export APP_SECURITY_USER_USERNAME=user
export APP_SECURITY_USER_PASSWORD=replace-me

mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

Use a secret manager or deployment environment for production credentials. Do not commit them to the
repository.


## Project goals

Investory is intended for investors who want to understand:

- where their returns come from
- how their portfolio evolves over time
- how multiple brokers combine into one investment strategy
- how taxes and currency movements affect long-term performance

Rather than replacing broker platforms, Investory provides a consolidated analytics layer focused on long-term portfolio management.

## License

MIT License
