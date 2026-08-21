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
