# Long-Term Assets — Manual QA Plan

## Scope

Manual real estate, bonds, cash reserves and personal assets. UI route: `/long-term-assets`.

## Before testing

- Use a disposable portfolio and capture the pre-test Investment Profile and plan values.
- Prepare valid and invalid dates, positive/negative money values, overlapping valuation periods, and two rental contracts.
- Treat archive/delete actions as state-changing: test only dedicated records.

## Test cases

| ID | UI action | Expected result | DB / Kubernetes evidence |
| --- | --- | --- | --- |
| LTA-01 | Open list with a portfolio having each supported type. | Type, value, allocation and status are understandable; empty state and portfolio switch are correct. | `long_term_assets` rows belong to selected portfolio; UI totals match active rows according to the current effective period. |
| LTA-02 | Create and edit a personal asset and a Cash Reserve. | Required fields validate; values persist once; edit reload is lossless. Personal assets are excluded from investment economics. | Explicit `personal_asset` and `cash_reserve` rows; verify no brokerage-accounting tables changed. |
| LTA-03 | Create real estate with current value and rental terms. | Current economics use the effective contract; invalid dates/amounts are rejected clearly. | Explicit `real_estate`, `rental_contract`, and term rows; check no overlapping or orphan contract. |
| LTA-04 | Add two rental contracts; confirm only the newest is expanded and older contracts show date range. Edit tenant/contact, end, terminate, and delete a dedicated test contract. | Contract lifecycle, dates, tenant details and derived rental income are correct. Ending/terminating affects only the selected contract; deletion requires an intentional action. | `long_term_asset_rental_contracts` and `_terms`; audit exact row IDs/dates and ensure active contract semantics agree with screen. |
| LTA-05 | Create a bond with current rate and maturity. | Contractual interest and maturity facts persist; impossible dates/rates are blocked. | Explicit `bond` row and the factual inventory view. |
| LTA-06 | Create an interest-bearing cash reserve with maturity, then attempt an invalid date ordering. | Rate and maturity persist, planning receives the reserve facts, and validation rejects invalid chronology. | Explicit `cash_reserve` row; check no partial record remains after rejected submit. |
| LTA-07 | Add, edit and delete valuation periods for a test asset; attempt overlaps/gaps/boundary dates. | Effective valuation selection is deterministic and displayed value changes only for applicable dates. | `long_term_asset_valuation_periods`; verify dates/value with a direct query. |
| LTA-08 | Change the global rental tax policy and review real-estate economics. | Policy changes are scoped correctly and reflected without contract-local tax fields. | Profile/global policy and explicit rental rows. |
| LTA-09 | Archive then reactivate a dedicated asset; revisit list/detail/profile. | Archived asset no longer contributes where inactive assets are excluded; reactivation restores it without loss of history. | `long_term_assets` status/lifecycle state and unchanged child records. |
| LTA-10 | Submit invalid numeric/date input and repeat submit/refresh/back navigation. | Clear validation, no duplicate record, no HTTP 500, and user-entered valid data is retained where feasible. | Application logs; transaction rollback evidence; no duplicate child rows. |

## Cross-module checks

- After each representative create/edit/archive action, open `/investment-profile` and `/simulation` in a test plan to verify source data is read, not copied into brokerage accounting.
- Inspect application logs for controller/domain validation errors versus unexpected exceptions; check pod restart/OOM events during bulk period/contract edits.

## Release exit

- Each asset type can be created, edited and rendered from persisted data.
- Contract, valuation, archive and tax-policy lifecycle tests leave no orphan/duplicate rows.
- Long-term changes never write `cash_operations`, `positions` or `account_daily`.
