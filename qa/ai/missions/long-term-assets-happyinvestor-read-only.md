# Mission: HappyInvestor Long-Term Assets exploratory read-only QA

- Status: `READY`
- Environment: deployed Investory develop environment
- Target portfolio: HappyInvestor portfolio, expected user/portfolio IDs `2`/`2`
- Browser/viewport: fixed by the run coordinator; use one desktop and one narrow viewport consistently
- Mission revision: `2026-09-09-complete-reference`

## Question

Can an authenticated read-only user inspect every HappyInvestor Long-Term Assets representation,
including collapsed and expanded tables, every asset detail/edit form, safe overlays, and return
navigation, while preserving cross-view and financial consistency without browser, network, loading,
empty-state, or visual defects?

## Required reading

- [`qa/ai/instructions.md`](../instructions.md)
- [`docs/domain/long-term-assets.md`](../../../docs/domain/long-term-assets.md)
- [`docs/quality/02-long-term-assets-manual-qa.md`](../../../docs/quality/02-long-term-assets-manual-qa.md)
- [`test-support` HappyInvestor scenario README](../../../test-support/src/main/java/com/smartbox/investory/testsupport/happyinvestor/README.md)
- `HappyInvestorLongTermFacts` and `HappyInvestorTestData`
- `HappyInvestorReadOnlyUiIT`, `LongTermAssetCrudUiIT`, `LongTermAssetsReadOnlyStress`,
  `UiPageSmokeIT`, `LongTermAssetsTemplateContractTest`, and relevant golden/read-only contracts

## Routes and scope

Start at `/portfolios/2/long-term-assets`. Mandatory checkpoints are the collapsed overview, every
expanded category, every currently seeded asset detail/edit route, safe tooltip/popover/disclosure
content, overview reload, and the fixed narrow viewport. Current seeded assets are Apartment A,
Apartment B, Treasury 2026, United States Treasury 4 3/8 07/31/33, Cash reserve, Term cash reserve,
and Family Car. Use the actual rendered asset links; do not assume the page's DOM shape.

Record a collapsed baseline first. Then expand every read-only category disclosure before evaluating
per-asset identities, values, income, tax, or maturity labels; collapsed summaries may not render
those rows. For every asset, open its GET detail/edit route, inspect every populated field, compare it
with the table and canonical facts, and leave through Cancel, Back, close, or browser Back without
saving. Return to the overview and verify the baseline remains unchanged.

Before any exploratory check, confirm that the visible profile is HappyInvestor and that the
canonical asset set is present. If another portfolio/data set appears, stop and report a
data-scope blocker. Do not call that mismatch an application defect without evidence that the
deployed target was meant to contain HappyInvestor.

## Goals and invariants

Let the browser agent choose observations and safe navigation paths. It should establish whether:

- the overview loads with the correct authenticated portfolio context, useful title/shell, and no
  unexpected loading, empty, server-error, or access-denied state;
- real estate, bonds, cash reserves, and personal assets are represented when present, with the
  expected HappyInvestor identities and the canonical six active assets;
- displayed category and overall totals reconcile internally, allocation shares are plausible,
  and income/yield/tax relationships are coherent under the Long-Term domain rules;
- values agree with independent facts where applicable: PLN reporting, Long-Term total `970000`,
  real estate `900000`, boundary-date bonds `10000` (post-reinvestment `20000`, including the matured
  historical row), cash reserves `50000`, personal assets `10000`, boundary-date
  rental net annual income `73873`, Apartment
  A/B tax bases `3200`/`3000`, and Apartment A annual tax `272`. Use the source classes for exact
  interpretation and formatting; do not derive expectations from the page or REST responses;
- labels distinguish value, payment, income, tax base, tax, gross/net yield, monthly/yearly period,
  maturity/end date, and personal assets' non-investment semantics;
- date-sensitive income follows the application's observed/as-of date. The canonical aggregate
  `75057.625` is a fixed `2025-12-31` boundary fact, not a timeless live-page expectation. The old
  Treasury principal `10000` at `4.625%` contributes net annual income `374.625` before `2026-02-28`, and
  contributes `0` on or after `2026-02-28`; the new `US91282CRC72` acquired on `2026-03-01` has
  coupon `4.375%`, maturity `2033-07-31`, and net annual income `354.375`. The aggregate must equal the sum of the date-valid
  components. On `2026-09-09`, the expected current forward values are Apartment A `38128`, Apartment
  B `35745`, old Treasury `0`, new Treasury `354.375`, term cash reserve `810`, and aggregate `75037.375`; do not hard-code this
  result without applying the maturity rule. Distinguish current forward annual income from YTD,
  historical, or boundary-date income;
- money, compact suffixes, currencies, percentages, dates, zero values, signs, precision, and
  responsive layout are sensible and not misleading;
- safe links, read-only disclosure/expansion, reload, back/forward, and any discovered read-only
  query/filter/sort controls behave consistently. If no safe sorting/filtering control exists,
  record that control class as `N/A - no applicable control`, rather than inventing an action;
- collapsed summaries, expanded rows, detail pages, and edit forms describe the same asset. Record
  a compact per-asset consistency result; use N/A for fields not represented in a view. Inspect safe
  hover/focus tooltips, popovers, menus, dialogs, and confirmation surfaces when present, and close
  them without confirming a mutation;
- every meaningful persisted field and calculated field is inspected on every asset form, including
  name, type/category, currency, value, acquisition date, income/rent, tax, rate/coupon, maturity,
  notes, and valuation fields where applicable. Distinguish persisted inputs from calculated outputs;
- bond coupons are compared using the documented presentation boundary: persisted ratios remain
  exact (`4.625%` and `4.375%`), while the edit-form percentage input intentionally displays two
  decimal places (`4.63%` and `4.38%`). Treat that representation rounding as expected, not as a
  cross-view inconsistency; do not save the form to infer persistence behavior;
- no unexpected mutation is attempted or requested, and the pre/post overview remains unchanged;
- first-party requests succeed, no relevant browser console/page errors occur, and no obvious
  clipping, overlap, horizontal overflow, broken link, or inconsistent state is visible.

## Forbidden actions

Do not submit create, edit, archive, delete, reactivate, import, export, refresh, reconciliation,
integration, scheduler, Save, Confirm, or any other write control. Opening a GET edit/detail route,
expanding a disclosure, hovering a tooltip, opening a safe dialog, and using Cancel/Back are allowed.
Do not issue direct API/fetch/XHR or browser-storage mutations. Monitor all requests; any unexpected
POST/PUT/PATCH/DELETE is a finding and must not be allowed to persist. Stop if the selected
environment is not the HappyInvestor fixture or if continuing would require persistent-data changes.

## Evidence requirements

Attach listeners before navigation for page errors, console errors/warnings, failed requests,
unexpected first-party responses, and all non-GET requests. Capture screenshots or traces when they
materially prove a visual, content, state, console, network, popup, or cross-view finding. Record URL,
route/context, timestamp, expected versus observed behavior, why a suspected defect is incorrect,
and sanitized evidence. Do not screenshot every successful asset. Use the report format under
`qa/ai/reports/`.

## Completion

Return exactly `PASS`, `SUSPICIOUS`, or `FAIL`. A FAIL must state what was observed, what was expected,
why it is incorrect, URL/context, and supporting evidence. Browser/deployment/authentication
unavailability is an environment blocker and must be reported separately, not as an application FAIL.
The final report must include collapsed/expanded results, every asset's form result, popup/dialog
results, a cross-view consistency matrix, financial reconciliation, desktop/narrow coverage,
console/network health, mutation safety, evidence, and whether Long-Term Assets coverage is
`COMPLETE` for Phase 4 preparation.
