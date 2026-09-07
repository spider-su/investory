# Long-Term Assets module --- quick KT

> **Goal:** understand manual, non-brokerage assets and their planning-facing financial facts.

## What it owns

Long-Term owns four explicit types: real estate, bonds, cash reserves, and personal assets.
Real estate owns rental-contract history and its current factual value. Tax and expected real-estate
growth are profile/global assumptions rather than per-asset configuration.
Long-Term does not use Investment persistence and does not perform retirement funding.

```text
explicit asset tables + rental facts
              |
              v
       Long-Term economics
              |
      +-------+--------+
      |       |        |
      v       v        v
  overview  profile  retirement
    UI      wealth     facts
            facts
```

`app_v_long_term_assets` is an internal read-only factual UNION view. It does not restore a generic
writable asset root.

## Where to start

- Management boundary: the Long-Term public API consumed by Web and REST adapters.
- Overview reads: one coherent calculated Long-Term page model.
- Profile/Portfolio reads: semantic investment value, personal-asset value, and income totals.
- Retirement reads: retirement-relevant current/historical financial facts only.
- Commands/calculations: `longterm.application.*`.
- Persistence: explicit per-type entities/repositories under `longterm.infrastructure.*`; entities,
  repositories, and SQL views never cross the public API boundary.
- Historical behavior: preserve externally observable date-scoped facts required by planning using
  the smallest explicit representation supported by the final schema.

## Domain shape

- `REAL_ESTATE`: current property facts, optional `land_register_number`, and rental contracts/terms.
- `BOND`: current value/principal, one current interest rate, maturity.
- `CASH_RESERVE`: explicit capital/liquidity with optional current interest rate and maturity; no
  invented return merely from value.
- `PERSONAL_ASSET`: `HOME`, `VEHICLE`, or `OTHER`; contributes to net worth but not investment
  income/yield or retirement capital.
- Rental tax, bond/cash-reserve profit tax, and expected RE growth come from profile/global policy.
- Long-Term owns asset taxonomy. Profile/Portfolio and Retirement consume financial meaning, not
  Long-Term types.

## Safe-change rules

- Do not recreate a generic JPA hierarchy or repository abstraction over the four explicit tables.
- Do not put tax, income, yield, retirement eligibility, or projection assumptions in
  `app_v_long_term_assets`.
- Do not turn expected growth into historical valuation facts.
- Keep rental terms: they are genuine persisted business facts.
- Preserve required historical behavior; simplify its persistence only when behavior remains
  equivalent.
- Keep Long-Term calculations in Long-Term, not UI adapters, Profile/Portfolio, Retirement, or SQL.
- `PERSONAL_ASSET` must not enter Retirement contracts.
- Prefer canonical Happy Investor facts in normal-path tests; invent values only for deliberate edge
  cases.
- Schema changes use append-only Flyway migrations under `app`; align the fast test snapshot.

Canonical detail: `docs/domain/long-term-assets.md`.
