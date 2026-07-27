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

Investory combines transaction history with market prices to reconstruct portfolio state and derive
daily and monthly analytics.

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

### Data management

- Import broker statements
- Manual price overrides
- Historical price synchronization
- Yahoo Finance export
- Portfolio reconciliation tools

---

## Supported brokers

Currently implemented:

- Interactive Brokers (IBKR)
- XTB

The architecture allows additional brokers to be added without changing portfolio analytics.

---

## Technology

| Layer | Technology |
|--------|------------|
| Backend | Java 25 |
| Framework | Spring Boot |
| Database | PostgreSQL |
| ORM | Spring Data JPA |
| Frontend | Thymeleaf |
| Charts | Chart.js |
| Build | Maven |

---

## Architecture

Investory is built around an immutable transaction ledger.

```
Broker Imports
        │
        ▼
Transaction Ledger
        │
        ▼
Portfolio Engine
        │
 ┌──────┴────────┐
 ▼               ▼
Valuation     Performance
Engine         Engine
 └──────┬────────┘
        ▼
 Dashboard
```

Investory reconstructs portfolio state from transactions and stores derived daily and monthly
account projections for fast, reproducible analytics.

This provides:

- deterministic calculations
- reproducible history
- complete auditability
- easier reconciliation
- accurate realized P/L

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
- risk metrics
- Monte Carlo simulations
- portfolio attribution
- richer tax reporting

## Running locally

Requirements:

- Java 25 or newer
- PostgreSQL
- Maven

```bash
git clone https://github.com/<your-user>/investory.git

cd investory

mvn spring-boot:run
```

Open:

```
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
