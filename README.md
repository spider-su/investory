# Investory

> Portfolio intelligence for long-term investors.
>
> Track investments across brokers, currencies, and asset classes while preserving a complete
> transaction ledger and producing reproducible portfolio analytics.

## Why Investory?

Most broker dashboards answer only one question:

> **"What is my portfolio worth today?"**

Investory answers much more:

- How much did I actually earn?
- What came from dividends?
- What was realized vs unrealized?
- What is my after-tax performance?
- Which broker contributes most?
- How did each account perform?
- How did currency movements affect my returns?
- How close am I to simply buying SPY?

Investory combines immutable broker events with market prices and FX rates to reconstruct a canonical
account state and derive consistent daily and monthly analytics.

---

## Features

### Portfolio overview

- Portfolio value
- Cash balance
- ROI / Total Return
- Multi-currency support
- Live FX conversion
- Multiple broker accounts

### Performance analytics

- Total Profit
- Realized Profit/Loss
- Unrealized Profit/Loss
- Dividend Income
- After-tax estimates
- Monthly performance
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
- Portfolio reconciliation tools

Broker imports use Hibernate JDBC batching. Asset and `account_daily` IDs use pooled sequence
allocation; cash operations and positions keep deterministic application-assigned IDs.

---

## Supported brokers

Currently implemented:

- Interactive Brokers (IBKR)
- XTB

Broker-specific importers preserve source records and map them into a common operational model without
changing the higher reporting architecture.

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

Investory separates broker events, reconstructed state, and reporting projections.

```text
Broker Imports
        │
        ▼
Immutable Raw Events
        │
        ▼
Normalized Operations + Positions
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

`account_daily` is the persisted historical reporting boundary. Each row represents one account on one
date.

It combines end-of-day snapshots such as cash, market value, equity, cost basis, and unrealized profit
with flows that occurred on that date, such as deposits, withdrawals, dividends, interest, fees, taxes,
and realized profit.

Daily flow columns are not cumulative. Monthly and portfolio reporting derives from `account_daily`
through database views and materialized views.

This provides:

- deterministic calculations
- reproducible history
- complete auditability
- easier reconciliation
- consistent account and portfolio analytics
- safe monthly aggregation

### Financial interpretation

- Internal transfers change account cash but do not change portfolio contributed capital.
- Currency conversion is not a deposit, withdrawal, or investment profit.
- Broker corrections and reversals retain their signs.
- Missing FX or ambiguous valuation inputs are reported as validation issues rather than treated as
  successful conversions.
- Returns are compounded from daily returns; percentages are not averaged.

## Dashboard

The dashboard includes:

- Portfolio Value
- Available Cash
- Total Return
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

Planned improvements include:

- additional broker integrations
- factor exposure
- dividend forecasting
- portfolio rebalancing suggestions
- Monte Carlo simulations
- richer tax reporting

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
