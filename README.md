# Investory

Investory consolidates broker activity into portfolio analytics that separate contributed capital from investment performance.

## The three investor questions

### How much is my portfolio worth?

Current portfolio value combines account cash and the market value of open positions in the portfolio
base currency. Individual broker accounts remain visible, while the headline value represents the
consolidated portfolio.

### How much did I contribute, and how much did investments earn?

External deposits and withdrawals are reported separately from investment results. Internal transfers
and currency conversions move money between accounts or currencies but do not change portfolio-level
contributed capital.

Investment earnings are separated into realized profit, unrealized profit, dividends net of recorded
withholding tax, and interest.

### How did I perform versus my benchmark?

Investory compares selected active accounts with SPY over the selected dashboard period. Account and
period selection are supported; the benchmark symbol is currently fixed.

## What works today

- Consolidated portfolio value and cash across multiple broker accounts.
- External deposits and withdrawals separated from investment earnings.
- Realized P/L, unrealized P/L, dividends, interest, and a separate capital-gains tax estimate.
- Monthly performance, historical account value, attribution, currency exposure, and position views.
- SPY comparison for selected active accounts and dashboard periods.
- IBKR and XTB statement imports with exact-file duplicate detection.
- Scheduled market-price and FX refresh, historical price storage, and manual price overrides.
- Yahoo Finance export and developer reconciliation and pipeline-validation tooling.

Reconciliation is currently developer-facing rather than an end-user workflow. See
[`docs/pipeline-testing-plan.md`](docs/pipeline-testing-plan.md) for implemented checkpoints and known
gaps.

## Supported brokers, currencies, and asset limitations

| Area | Current scope |
|------|---------------|
| IBKR | `.csv` activity statements and transaction-only exports. Activity-statement Open Positions and Net Asset Value sections can update reconstructed positions and account summaries. |
| XTB | `.xlsx` statements and `.zip` statement packages. |
| Automatic detection | `.csv` is treated as IBKR; `.xlsx` and `.zip` are treated as XTB. The broker can also be selected explicitly through the import endpoint. |
| Currencies | `USD`, `EUR`, and `PLN`. |
| Market data | TwelveData supplies automatic quotes, historical prices, and SPY monthly closes. The scheduled market refresh runs on weekdays at 22:01 Europe/Warsaw. |
| FX data | exchangerate.host supplies USD-based rates; EUR and PLN cross-rates are derived locally. FX refresh runs on weekdays at 15:00 Europe/Warsaw, and rows are stored at month start. |
| Asset coverage | Automatic quote coverage depends on TwelveData symbol mappings and plan limits. Non-US listings are skipped by default and may require manual prices. Real-time websocket pricing is not implemented. |

## How calculations work

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

### Financial interpretation

- Internal transfers change account cash but do not change portfolio contributed capital.
- Currency conversion is not a deposit, withdrawal, or investment profit.
- Broker corrections and reversals retain their imported signs.
- When no usable FX rate exists, or the newest rate is more than 45 days old, the affected calculation
  fails with an `FX rate unavailable` error. An unconverted amount is not silently treated as target
  currency.

## Current limitations

- Exact duplicates are skipped using broker plus file SHA-256. Different exports with overlapping date
  ranges can still double-count operations; partial-overlap idempotency is not implemented.
- SPY is the only benchmark. Benchmark selection is limited to accounts and dashboard period.
- Investory uses one shared portfolio dataset. Per-user data isolation is not implemented.
- Original broker files are not retained. Investory stores normalized portfolio rows, import metadata,
  file hashes, counts, available broker identifiers, and source symbols. Failed text payload previews
  are capped at 8 KB.
- Backups are operator-managed. Back up PostgreSQL and retain original broker exports separately; a
  persistent Docker volume is not a backup.
- Reconciliation remains developer tooling based on `ReconRunner`, local broker files, JDBC checks,
  and manual verification. IBKR C1 is not implemented, dashboard checkpoint C6 is not automated,
  secondary checkpoint C7 is optional, and the full golden pipeline is not yet a CI gate.
- The capital-gains estimate follows Polish assumptions. Other tax jurisdictions are not modeled.

## Architecture overview

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

`import_history` records import metadata, status, counts, and failures. Normalized rows retain available
broker identifiers and original asset symbols for traceability, but Investory is not an immutable raw
event store.

`account_daily` is the persisted reporting boundary. Each row represents one account on one date and
combines end-of-day state with that day's flows. Monthly and portfolio summaries are derived from it
through database views and materialized views.

The application stack is Java 25, Spring Boot 4.1, PostgreSQL, Spring Data JPA, Thymeleaf, Chart.js,
and Maven. Detailed architecture, database invariants, and implementation rules live in
[`AGENTS.md`](AGENTS.md).

## Exact local setup

Requirements:

- Java 25 or newer
- PostgreSQL
- Maven

For a reproducible Java, Maven, and PostgreSQL environment, use the Dev Container described in
[`docs/DEV_CONTAINER.md`](docs/DEV_CONTAINER.md).

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

### 3. Start with the local profile

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Do not omit the profile outside the Dev Container. Without it, the application can connect to the
wrong datasource and produce misleading Flyway failures.

On startup, Flyway creates the `investory` schema when needed and applies migrations from
`src/main/resources/sql/migration`.

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
- The root page, dashboard, error page, and static resources are currently public. Mutating routes
  require the administrator role.
- CSRF protection is currently disabled.
- All authenticated users see the same portfolio because per-user data scoping is not implemented.
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

mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

Use deployment secrets or a secret manager. Do not commit credentials.

## Documentation links

- [`README.md`](README.md): product scope, calculations, limitations, and setup.
- [`AGENTS.md`](AGENTS.md): canonical engineering guide for architecture, database invariants, runtime
  behavior, API surface, and development workflow.
- [`docs/README.md`](docs/README.md): index of focused supporting documents.
- [`CHANGELOG.md`](CHANGELOG.md): completed work and documentation history.
- [`CLAUDE.md`](CLAUDE.md) and [`.github/copilot-instructions.md`](.github/copilot-instructions.md):
  tool-specific overlays that defer canonical project facts to `AGENTS.md`.

## Roadmap

[`ROADMAP.md`](ROADMAP.md) is the canonical living plan for future work and current priorities.
Completed work is recorded in [`CHANGELOG.md`](CHANGELOG.md), not retained as crossed-out roadmap
items.

## License

MIT License
