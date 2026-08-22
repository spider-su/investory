# Long-Term Assets

## Rental source of truth

Real-estate rental economics are stored and read through rental contracts and their terms. The
projection, current snapshot, historical snapshot, and asset summary paths do not read
`long_term_asset_cash_flows` for real estate. Contract terms carry dates, cadence, tax ownership,
and landlord/tenant expense ownership.

The checked-in bootstrap document may still accept `cashFlows` as import input. Bootstrap converts
that input into rental contracts before runtime use. It is import compatibility, not a second
runtime model.

`long_term_asset_cash_flows` remains available for supported non-real-estate recurring asset flows
and historical/import data. It is not a real-estate rental write path.

## Bond values

`acquisitionValue` is the historical acquisition/principal value. `currentValue` is the present
planning valuation. Updating a bond's current value does not overwrite its acquisition history.

## Public boundary

The management API exposes rental-contract commands and persistence-free read views. Legacy rental
period and cash-flow mutation endpoints are not part of the public API. JPA entities and repositories
remain inside Long-Term Assets.

For Retirement planning, Long-Term exposes normalized economic contracts rather than concrete asset
implementations. Long-Term owns the translation from properties, rental contracts, bonds, deposits,
cash reserves, maturities, tax treatment, and other internal rules into planning-level balances,
cash flows, capital availability, and projections.

Retirement must not inspect Long-Term entities or reproduce those calculations. A reviewed Retirement
plan may freeze the normalized values returned by this boundary for reproducibility; that snapshot is
Retirement planning provenance and does not replace Long-Term as the source of current asset state.

For annual retirement planning, `quote` is a non-consuming economic view of one year. It may be used
to determine cash flows and capital availability, but it never advances maturity or reinvestment
state. `plan` is the single committed annual transition; only its returned end state is passed to
the next year.

Generic cash-flow rows remain supported for non-real-estate assets and legacy/bootstrap imports.
Real-estate rental economics use rental contracts; the legacy rental-period projection fallback is
compatibility-only and is not the normal persisted runtime path.
