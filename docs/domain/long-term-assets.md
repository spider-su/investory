# Long-Term Assets

## Product model

Long-Term owns four closed asset types: `REAL_ESTATE`, `BOND`, `CASH_RESERVE`, and `PERSONAL_ASSET`.
They are explicit product concepts, not implementations of a generic persistent
asset hierarchy.

Persistence uses explicit tables for each type. The read-only internal `app_v_long_term_assets` view
unifies common factual inventory fields across those tables. It is not a writable persistence root
and is not exposed across module boundaries. Tax, income, yield, retirement eligibility, and
projection calculations remain application logic.

`PERSONAL_ASSET` represents non-investment property such as an own home or vehicle. Its closed
categories are `HOME`, `VEHICLE`, and `OTHER`. Personal assets contribute to whole-wealth/net-worth
reporting, but not to investment income, the investment-yield denominator, or retirement capital.
These semantics follow from the type rather than configurable per-row inclusion flags.

Profile yield uses Long-Term investment value as its denominator and therefore excludes personal
assets. A cash reserve enters current retirement reserve only when it has no future maturity lock.
Locked cash, bonds, real estate, and personal assets remain in net worth while staying outside the
current cash reserve. Their availability is a source-domain fact published to Profile.

Long-Term owns the asset taxonomy. Profile/Portfolio consumers receive financial meaning such as
investment value, personal-asset value, and annual income. Retirement receives only
retirement-relevant balances, income, and projection facts; it does not receive `PERSONAL_ASSET`.

## Financial assumptions

Tax and planning assumptions are profile/global policy, not per-asset configuration. The current
policy uses an 8.5% rental-income tax rate and a 19% profit-tax rate for bond and interest-bearing
cash-reserve income.
Expected real-estate growth is also a profile/global planning assumption. These values have one
source of truth and are not copied into individual assets or rental contracts.

Each property stores an annual rental-tax base in its own immutable asset currency. The overview
displays the monthly rental-tax figure consistently in both expanded and collapsed views. Category
values are direct sums of their property-row values. `NULL` means unspecified and zero means an
explicit zero base.

A bond has principal/current value, one current interest rate, and maturity. A cash reserve has an
optional current interest rate and optional maturity: a null or zero rate is plain cash, while a
positive rate represents interest-bearing cash under the same global-tax rule. Merely holding a
cash-reserve value does not create income or yield.

Yield is zero when current asset value is zero; income, expense, and tax amounts remain visible,
but Long-Term does not invent a denominator for a percentage.

Expected real-estate growth is an assumption, not a valuation fact. Long-Term stores the current
property value directly; it does not persist a synthetic dated valuation history.
`land_register_number` is the optional factual property-register identifier.

## Rental source of truth

Real-estate rental economics are stored and read through rental contracts and their terms. Projection,
current snapshot, historical snapshot, and asset-summary paths reuse those facts. Contract terms
carry dates, cadence, and landlord/tenant expense ownership.

Current snapshots annualize the contract effective on the requested boundary date. Historical
snapshots accrue each contract only across its calendar-year overlap, prorating partial months or
annual terms, subtract landlord-paid expenses and rental tax, then normalize the result to canonical
USD. These two values are intentionally different when rent changes during a year. Current balances
and current bond rates remain unavailable in a historical snapshot until backed by dated facts.

`paidByTenant` applies to expense terms. Payment Audit includes all rental-income terms plus
tenant-paid expenses in the tenant's monthly payment; landlord-paid expenses are excluded from that
payment and reduce property economics instead.

The checked-in bootstrap document may accept `cashFlows` only for `REAL_ESTATE` import input.
Bootstrap rejects cash-flow rows for every other asset type and converts accepted rows into rental
contracts before runtime use. This is import compatibility, not a second runtime model.

There is no generic runtime cash-flow persistence. Rental contracts reference real estate directly
and their terms are explicit persisted facts.

## Rental contract lifecycle

Rental contracts support create, read, in-place update, early termination, and explicit deletion.
Contract identity remains stable during update. Updating a contract atomically replaces its tenant
metadata, planned period, and complete term collection; removed terms are deleted. A contract
contains at most one term for each cash-flow type.

`endDate` is the expected, planned end of a contract. `terminatedDate` records an actual early
termination. The effective end is the earlier of those dates. Ordinary editing changes the expected
end; early termination is a separate lifecycle action and cannot precede the start or follow the
expected end.

Contracts for one property cannot overlap. Creating a contract never silently terminates another
contract. An explicit rollover option may set the immediately preceding contract's expected end to
the day before the new start, in the same transaction. It does not set `terminatedDate`.

Deletion is correction of incorrectly entered data, not a normal lifecycle transition. It removes
the selected contract and its terms after portfolio and real-estate ownership checks. It does not
reopen or extend adjacent contracts. Because historical projections read contract history, deletion
may change historical calculations.

Tenant name, email, and phone belong to the rental contract, not the property. They are optional and
may differ across successive contracts. Rental-tax rates are resolved from profile/global policy and
are not duplicated into each property or contract.

## Public boundaries

`LongTermAssetsApi` is the Web/REST management boundary for explicit asset and rental commands plus
persistence-free management views. `LongTermAssetProfileReader`,
`LongTermAssetAnnualSnapshotReader`, and `LongTermAssetPaymentAuditReader` are smaller consumer
boundaries for cross-module composition. JPA
entities, repositories, and `app_v_long_term_assets` remain internal to Long-Term.

The Long-Term overview may expose all four internal asset types and owns totals, allocation, tax,
income, and yield calculations used by the page.

Profile/Portfolio receives semantic aggregated facts, including Long-Term investment value,
personal-asset value, and annual income as required. It does not consume Long-Term entities,
repositories, SQL views, or the internal type taxonomy.

Retirement receives only retirement-relevant current and historical financial facts. Long-Term owns
the translation from real estate, rental contracts, bonds, cash reserves, maturities, and
global policy into those normalized facts. Retirement does not inspect Long-Term persistence,
reproduce Long-Term calculations, or receive PersonalAsset data.

A reviewed Retirement revision may freeze normalized Long-Term economic facts for reproducibility.
That frozen snapshot is Retirement planning provenance and does not replace Long-Term as the source
of current asset state.

## Creation and review invariants

Creation is explicit for each supported type. `PERSONAL_ASSET` replaces the old generic financial
meaning of `OTHER`; `OTHER` remains only as a PersonalAsset category. Bonds, cash reserves, real
estate, and personal assets use explicit workflows. Rental
contracts are valid only for `REAL_ESTATE`.

New rental contracts persist only explicitly supplied terms; copying a previous contract is a UI
prefilling action and never mutates data before submission.

All application-level rates are canonical decimal fractions: `0.085` means 8.5%. HTTP and
server-rendered form fields use percentage points for display/input and convert once at the adapter
boundary. Rates are validated again when entering Long-Term.

Asset currency is immutable after creation. Changing denomination requires creating a new asset or
an explicit conversion workflow; ordinary edits and bootstrap upserts must never relabel stored
amounts.

Historical/date-scoped behavior remains a business requirement where consumed by annual snapshots
and planning. The persistence representation may be simplified only when the same externally
observable historical behavior is preserved.

Forward simulation never re-reads live Long-Term persistence after a reviewed revision has frozen
its economic inputs. A later source edit changes Live/Current state only until the user explicitly
rebaselines and reviews a new revision.
