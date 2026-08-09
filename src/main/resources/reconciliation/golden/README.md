# Investory golden regression fixtures

This is a deliberately small regression corpus built from real IBKR and XTB rows.

## What it covers

- IBKR direct US Treasury: percent-of-par valuation, accrued interest, coupon, full-call redemption.
- IBKR business-date boundary: the 8,793 USD deposit belongs to 2026-05-07, not 2026-05-06.
- IBKR cross-currency trade: EUR-priced JGPI, EUR.USD FX component, USD-priced VWRA.
- IBKR interest/dividend/tax classification.
- XTB VHYD investment-plan/subaccount rebooking that previously created a false ~6k P/L jump.
- XTB true tracked-account transfer: -325 / +325 across two accounts.
- XTB RESULT_ONLY NATGAS CFD with rollover and swap.
- XTB IKE PLN account holding a USD-quoted ETF plus a PLN equity.
- XTB cash-only account funding and internal IKE allocation.

## Files

- `ibkr/U17959259.GOLDEN.csv` — transaction-only IBKR fixture.
- `xtb/investory_xtb_golden.zip` — four reduced XTB workbooks importable as one ZIP.
- `reference/exchange_rates.csv` — minimal historical FX checkpoint rows for deterministic offline tests.
- `expected/checkpoints.json` — semantic assertions to enforce.
- `expected/provenance.json` — source SHA-256 values and exact source row numbers retained.

## Intended use

Use these fixtures in a clean Testcontainers PostgreSQL database. Run Flyway, import the fixtures,
load the local FX reference, rebuild projections, then execute the reconciliation gates.

Do not fetch current market/FX data during this golden test.

These are regression fixtures, not a full broker archive. The full private archive remains a separate
`archive` verification mode.

When a new real production bug is found:
1. isolate the smallest source rows that reproduce it;
2. add them here;
3. add one semantic checkpoint;
4. keep the corpus small.
