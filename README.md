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

```bash
git clone https://github.com/spider-su/investory.git

cd investory

mvn spring-boot:run
```

Open:

```text
http://localhost:8080
```

## Project goals

Investory is intended for investors who want to understand:

- where their returns come from
- how their portfolio evolves over time
- how multiple brokers combine into one investment strategy
- how taxes and currency movements affect long-term performance

Rather than replacing broker platforms, Investory provides a consolidated analytics layer focused on long-term portfolio management.

## License

MIT License
