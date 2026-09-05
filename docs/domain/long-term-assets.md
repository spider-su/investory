# Long-Term Assets

## Rental source of truth

Real-estate rental economics are stored and read through rental contracts and their terms. The
projection, current snapshot, historical snapshot, and asset summary paths read those contracts.
Contract terms carry dates, cadence, tax ownership,
and landlord/tenant expense ownership.

The checked-in bootstrap document may accept `cashFlows` only for `REAL_ESTATE` import input.
Bootstrap rejects cash-flow rows for every other asset type and converts accepted rows into rental
contracts before runtime use. This is real-estate import compatibility, not a second runtime model.

There is no generic runtime cash-flow persistence. Existing legacy rows are migrated into rental
contracts and rental terms before the legacy table is removed.

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

Cash reserves and bonds each have one current planning-rate assumption. Saving either asset replaces
that assumption; users do not maintain effective-dated cash-return or bond-rate history. When a bond
or cash reserve expires, archive it and create a new asset. Rate-period rows remain an internal
persistence compatibility detail. Bootstrap accepts at most one cash-reserve valuation period and
requires exactly one bond-rate period; importing either asset replaces any previously stored rate
rows. Real-estate valuation growth remains effective-dated.

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

The current normalized Long-Term profile snapshot is the only live input to Retirement planning.
Reviewed Retirement revisions persist that snapshot before simulation; Long-Term does not maintain
a second quote/plan transition API or infer projection behavior from legacy object shapes.

Bond interest paid out is a spendable fixed-income cash flow. Capitalized bond interest is retained
in Long-Term capital and is reported separately from cash income; it is never counted in both places.

Cash-flow rows are supported only as real-estate bootstrap input and are converted directly into
rental contracts and terms. Non-real-estate assets cannot define or import generic cash-flow rows.
Persisted contracts carry explicit bootstrap ownership. A repeated import atomically replaces
only bootstrap-owned contracts; manual and pre-ownership contracts are protected from importer
rewrites even when tenant identity and notes are empty. The bootstrap document is authoritative for
its owned contracts: rebuilding them captures the document asset's current tax base and tax-payer
default for every supplied rental period. Use separate assets or post-import contract corrections
when historical periods require different tax snapshots; a later bootstrap import will apply its
authoritative defaults again.

Expected real-estate value growth is informational. Deterministic Retirement ignores appreciation
and does not automatically sell property.

## Creation and review invariants

Generic asset creation accepts only `OTHER`. `OTHER` is notes-only: it remains viewable and editable,
but contributes no value, income, annual result, or retirement projection total. Bonds, deposits,
cash reserves, and real estate use
atomic subtype workflows; deposits require a maturity date. Rental contracts are valid only for
`REAL_ESTATE` assets. New contracts persist only explicitly supplied terms; copying a previous
contract is a UI prefilling action and never mutates data before submission. Explicit bond redemption
is preserved by ordinary edits; a new bond uses acquisition value only when redemption was not
supplied.

All application-level rates are canonical decimal fractions: `0.085` means 8.5%. HTTP and
server-rendered form fields use percentage points for display and input, and convert exactly once at
their boundary. Return, tax, and growth rates are validated again when they enter Long-Term so an
in-process caller cannot persist percentage points accidentally.

Generic asset PATCH requests are partial updates. Omitted fields preserve their current value;
explicit null is not used to clear a value. Specialized asset workflows remain the contract for
bond, deposit, cash-reserve, and real-estate fields.

Asset currency is immutable after creation. Changing the denomination requires creating a new asset
or an explicit conversion workflow; ordinary edits and bootstrap upserts must never relabel stored
amounts. A rental contract captures the property's monthly rental-tax base and tax-payer default when
the contract is created. Interactive property-default edits apply only to new contracts and do not
rewrite historical rental economics. Authoritative bootstrap replacement follows the import contract
described above.

The Long-Term profile reader returns a persistence-free normalized planning snapshot. Retirement
uses that snapshot for the current view and stores it in a reviewed revision. Forward simulation
never re-reads live Long-Term records, rates, taxes, contracts, or allocations. A later source edit
therefore changes CURRENT only until the user explicitly rebaselines and reviews a new revision.
The snapshot freezes the rental-tax policy effective on its review date. Future-dated policy changes
become a new planning assumption when the user rebaselines on or after their effective date; they are
not scheduled automatically inside an already reviewed revision.
