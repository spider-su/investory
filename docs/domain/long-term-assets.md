# Long-Term Assets

## Rental source of truth

Real-estate rental economics are stored and read through rental contracts and their terms. The
projection, current snapshot, historical snapshot, and asset summary paths do not read
`long_term_asset_cash_flows` for real estate. Contract terms carry dates, cadence, tax ownership,
and landlord/tenant expense ownership.

The checked-in bootstrap document may accept `cashFlows` only for `REAL_ESTATE` import input.
Bootstrap rejects cash-flow rows for every other asset type and converts accepted rows into rental
contracts before runtime use. This is real-estate import compatibility, not a second runtime model.

`long_term_asset_cash_flows` is legacy persistence compatibility and is not a supported generic
cash-flow model for non-real-estate assets.

## Rental contract lifecycle

Rental contracts support create, read, in-place update, early termination, and explicit deletion.
Contract identity remains stable during an update. Updating a contract atomically replaces its
tenant metadata, planned period, contract-level rental-tax ownership, and complete term collection;
removed terms are deleted. A contract contains at most one term for each cash-flow type.

`endDate` is the expected, planned end of a contract. `terminatedDate` records an actual early
termination. The effective end is the earlier of those dates. Ordinary editing changes the expected
end; early termination is a separate lifecycle action and cannot precede the start or follow the
expected end.

Contracts for one asset cannot overlap. Creating a contract never silently terminates another
contract. An explicit rollover option may set the immediately preceding contract's expected end to
the day before the new start, in the same transaction. It does not set `terminatedDate`.

Deletion is correction of incorrectly entered data, not a normal lifecycle transition. It removes
the selected contract and its terms after portfolio, asset, and real-estate ownership checks. It does
not reopen or extend adjacent contracts. Because historical projections read contract history,
deletion may change historical calculations.

Tenant name, email, and phone belong to the rental contract, not the property. They are optional and
may differ across successive contracts. Contract-level rental-tax ownership remains tri-state:
`null` inherits the property default, `false` means landlord-paid, and `true` means tenant-paid.

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

Bond interest paid out is a spendable fixed-income cash flow. Capitalized bond interest is retained
in Long-Term capital and is reported separately from cash income; it is never counted in both places.

Cash-flow rows are supported only as real-estate bootstrap input and are converted into rental
contracts. Non-real-estate assets cannot define or import generic cash-flow rows. The legacy
rental-period projection fallback is compatibility-only and is not the normal persisted runtime
path.

Expected real-estate value growth is informational. Deterministic Retirement ignores appreciation
and does not automatically sell property.

## Creation and review invariants

Generic asset creation accepts only `OTHER`. Bonds, deposits, cash reserves, and real estate use
atomic subtype workflows; deposits require a maturity date. Rental contracts are valid only for
`REAL_ESTATE` assets. New contracts persist only explicitly supplied terms; copying a previous
contract is a UI prefilling action and never mutates data before submission. Explicit bond redemption
is preserved by ordinary edits; a new bond uses acquisition value only when redemption was not
supplied.

Asset currency is immutable after creation. Changing the denomination requires creating a new asset
or an explicit conversion workflow; ordinary edits and bootstrap upserts must never relabel stored
amounts. A rental contract captures the property's monthly rental-tax base and tax-payer default when
the contract is created. Later property-default edits apply only to new contracts and do not rewrite
historical rental economics.

The Long-Term profile reader returns a persistence-free normalized planning snapshot. Retirement
uses that snapshot for the current view and stores it in a reviewed revision. Forward simulation
never re-reads live Long-Term records, rates, taxes, contracts, or allocations. A later source edit
therefore changes CURRENT only until the user explicitly rebaselines and reviews a new revision.
The snapshot freezes the rental-tax policy effective on its review date. Future-dated policy changes
become a new planning assumption when the user rebaselines on or after their effective date; they are
not scheduled automatically inside an already reviewed revision.
