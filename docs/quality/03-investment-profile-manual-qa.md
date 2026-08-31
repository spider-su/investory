# Investment Profile — Manual QA Plan

## Scope

Read-only whole-wealth composition of Investment and Long-Term facts. UI route: `/investment-profile`.

## Preconditions

- Use a portfolio with brokerage holdings/cash and at least one active long-term asset of each relevant class.
- Record source totals from the Investment dashboard and Long-Term Assets screens before testing.

## Test cases

| ID | UI action | Expected result | DB / Kubernetes evidence |
| --- | --- | --- | --- |
| PRF-01 | Open profile for a populated portfolio. | Brokerage and manual assets are presented as one coherent whole-wealth view, with no duplicated source row. | Sum the source Investment reporting value and active long-term value; compare to profile total within documented rounding. |
| PRF-02 | Switch portfolio; test empty portfolio and one with only brokerage or only manual assets. | Scope changes everywhere consistently; empty/partial states are intentional and no stale prior-portfolio value remains. | Filter source tables/views by portfolio ID; inspect browser network requests for scope. |
| PRF-03 | Change a long-term asset value/period, then reload profile. | Profile reflects source change, with no manual profile edit path or copied persistence. | Confirm changes only in long-term tables; no profile-owned table/write is created. |
| PRF-04 | Import or alter a test brokerage fact, rebuild required reporting data, then reload profile. | Profile reflects canonical investment reporting result after the normal pipeline completes. | Trace to `account_daily`/reporting sources; ensure profile access did not mutate source facts. |
| PRF-05 | Compare currency/asset-category allocation against source details. | Categories and converted values are consistent; missing/stale FX is surfaced safely, never hidden as a valid amount. | Sample FX source rows and application logs; check value lineage for a non-base-currency holding. |
| PRF-06 | Exercise reload, browser back/forward and narrow viewport. | Read-only page remains stable, no duplicated totals/charts and no console/template errors. | Browser console/network and pod logs. |

## Integrity checks

- Profile has no write side effects: compare source row counts/timestamps before and after repeated page loads.
- Planning consumers must receive the same source-derived economics: sample profile totals against the selected simulation input/baseline.
- Any mismatch is a source-lineage defect; record the portfolio, as-of time, input rows, displayed value and full-precision difference.

## Release exit

- Populated, empty, brokerage-only and manual-only portfolios render correctly.
- A sampled portfolio total and allocation reconcile to their two source domains.
- Repeated reads create no database writes or application errors.
