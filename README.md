# Investory

> **Portfolio intelligence for long-term investors.**
>
> Track your investments across multiple brokers, currencies and asset classes while preserving a complete transaction history and producing accurate portfolio analytics.
>
> **Built with Spring Boot + PostgreSQL. Developed with OpenAI Codex and GPT-5.6 as AI engineering assistants for implementation, architecture, debugging, documentation and UX design.**

---

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

Investory combines complete transaction history with market prices to reconstruct portfolio history and calculate meaningful long-term investment metrics.

---

# Features

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

# Supported brokers

Currently implemented:

- Interactive Brokers (IBKR)
- XTB

The architecture allows additional brokers to be added without changing portfolio analytics.

---

# Technology

| Layer | Technology |
|--------|------------|
| Backend | Java 21 |
| Framework | Spring Boot |
| Database | PostgreSQL |
| ORM | Spring Data JPA |
| Frontend | Thymeleaf |
| Charts | Chart.js |
| Build | Maven |

---

# Architecture

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

Instead of storing daily balances, Investory reconstructs portfolio state from transactions.

This provides:

- deterministic calculations
- reproducible history
- complete auditability
- easier reconciliation
- accurate realized P/L

---

# Key capabilities

## Multi-broker portfolio

Manage investments across multiple brokerage accounts as one portfolio while still being able to analyze each account independently.

---

## Multi-currency support

Native support for:

- USD
- EUR
- PLN

with automatic FX conversion for:

- portfolio value
- realized profit
- unrealized profit
- dividends
- historical performance

---

## Portfolio reconciliation

Investory keeps two complementary views of the portfolio:

- immutable transaction history
- historical account valuation

Small differences caused by missing historical prices or FX interpolation can be reconciled while preserving the integrity of the transaction ledger.

---

## Performance analytics

Instead of only displaying balance, Investory separates:

- realized gains
- unrealized gains
- dividends
- taxes
- currency impact

making long-term investment performance much easier to understand.

---

# Dashboard

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

---

# AI-assisted development

Investory was developed using **OpenAI Codex** and **GPT-5.6** throughout the project.

AI was used as an engineering assistant—not as an automatic code generator—with every significant change reviewed before integration.

## OpenAI Codex

Codex accelerated implementation by assisting with:

- Spring Boot development
- PostgreSQL query optimization
- Repository and service refactoring
- Materialized view implementation
- DTO and mapper generation
- Portfolio analytics
- Historical reconstruction logic
- Debugging reconciliation issues
- Test generation
- Code reviews

Development remained iterative, with Codex acting as a pair programmer rather than generating complete applications.

---

## GPT-5.6

GPT-5.6 supported both engineering and product development by helping with:

- architecture reviews
- portfolio reconciliation algorithms
- financial calculation validation
- dashboard UX improvements
- interface wording
- feature brainstorming
- documentation
- README generation
- Devpost submission
- CSS and UI refinement
- API design discussions

GPT-5.6 was also used extensively to evaluate new portfolio analytics ideas and translate investment concepts into practical product features.

---

## Examples of AI-assisted work

During development AI contributed to work such as:

- portfolio reconciliation between Interactive Brokers and XTB
- historical account reconstruction
- multi-currency portfolio calculations
- tax-aware profit calculations
- dashboard redesign
- PostgreSQL materialized view optimization
- documentation improvements
- performance tuning
- developer experience improvements

---

## Human responsibility

All portfolio calculations, accounting logic and investment analytics were designed, validated and tested by the project author.

AI accelerated development, but architectural decisions, financial calculations and final implementations were reviewed before being accepted.

---

# Roadmap

Planned improvements include:

- additional broker integrations
- portfolio benchmarking
- factor exposure
- dividend forecasting
- portfolio rebalancing suggestions
- risk metrics
- Monte Carlo simulations
- portfolio attribution
- richer tax reporting

---

# Running locally

Requirements:

- Java 21
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

---

# Project goals

Investory is intended for investors who want to understand:

- where their returns come from
- how their portfolio evolves over time
- how multiple brokers combine into one investment strategy
- how taxes and currency movements affect long-term performance

Rather than replacing broker platforms, Investory provides a consolidated analytics layer focused on long-term portfolio management.

---

# License

MIT License
