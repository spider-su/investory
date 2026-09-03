# Settlement valuation reconstruction investigation

Generated: 2026-08-14

## Population

The rebuilt settlement diagnostic has 159 `VALUATION_RECONSTRUCTION_FAILED` rows:

| Population | Rows | Settlement model | Accounts | Date range |
|---|---:|---|---:|---|
| Previous symbol market value is NULL | 156 | 155 `CASH_SETTLED`, 1 `MIXED` | 6 | 2025-02-26 .. 2026-07-07 |
| Current reconstructed symbol value is zero | 3 | `CASH_SETTLED` | 1 | 2025-03-03 .. 2025-10-24 |

The 156 previous-NULL rows all reference assets with `assets.exclude_from_import = true`.
151 have a raw price-history row, and 5 have no raw price-history row even before the
canonical asset filter. All 156 have a previous valuation date and positive previous
open quantity. FX availability is complete.

Account grouping for the 156 rows:

| Account | Rows |
|---|---:|
| Trading USD | 117 |
| Dividends | 23 |
| REITs USD | 8 |
| IKE Alex | 5 |
| IKE Olga | 2 |
| Trading USD Metal | 1 |

## AMT.US / 2025-02-26 trace

The previous valuation date is `2025-02-25`. For account `Trading USD`:

- previous `CASH_SETTLED` open quantity: `0.51870000`;
- raw price row: `asset_price_history`, source `STOOQ`, source symbol `amt.us`;
- price date: `2025-02-25`;
- price: `203.75000000 USD`;
- price origin: `EXACT_LISTING_MARKET_CLOSE`;
- FX: USD to portfolio USD, rate `1`, status `SAME_CURRENCY`.

The available raw inputs would produce `0.5187 * 203.75 * 1 = 105.685125` base-currency
market value.

The settlement `symbol_values` CTE does not read raw price history. It reads
`app_v_canonical_asset_daily_price`. That view joins assets with
`exclude_from_import = false`; AMT.US is excluded, so the canonical price leg returns no
row. The previous price is therefore NULL, and the CASE expression produces
`previous_symbol_market_value_base = NULL`.

Canonical reconstructed position views apply the same asset exclusion and contain no AMT.US
row for that date. This is not an FX, quantity, symbol identity, or LEFT JOIN defect.

## Classification

| Class | Rows | Finding |
|---|---:|---|
| `SETTLEMENT_DIAGNOSTIC_DUPLICATED_VALUATION_LOGIC` | 151 | Raw price exists, but settlement includes an excluded asset while canonical valuation excludes it. |
| `PREVIOUS_PRICE_NOT_AVAILABLE` | 5 | No raw price exists on or before the previous valuation date; these assets are also excluded. |
| `PREVIOUS_FX_JOIN_DEFECT` | 0 | No missing FX; AMT exact same-currency FX resolves. |
| `SYMBOL_IDENTITY_JOIN_DEFECT` | 0 | Settlement joins by canonical numeric `asset_id`. |
| `MARKET_QUANTITY_SEMANTIC_MISMATCH` | 0 proven | Affected dominant rows are cash-settled; no result-only valuation issue explains them. |
| `UNEXPLAINED_VALUATION_AVAILABILITY` | 0 proven | The asset-scope rule explains the population. |

The 3 zero-current-value rows are separate opening-boundary cases. Their previous open
quantity is zero while current open quantity is positive (`RTX.US` on 2025-03-03,
`OSCR.US` on 2025-07-02, and `ARM.US` on 2025-10-24). They are not part of the previous-NULL
population and should be handled as opening/boundary review rows after the scope correction,
not mixed into settlement cash accounting.

## Root cause and decision

The settlement view directly reconstructs closed-lot valuation while its canonical price
source excludes assets marked `exclude_from_import`. `assets.exclude_from_import` is defined
to exclude the asset from all asset-linked derived valuation, P/L, statistics, reconciliation,
and normalized cash calculations. The settlement diagnostic is therefore outside the intended
asset scope.

No production accounting formula, position valuation, cash formula, imported fact, FX rule, or
materiality threshold changed. No diagnostic fix was applied in this investigation.

The smallest justified implementation is to apply the canonical asset scope to settlement
`closed_lots` (or explicitly omit excluded assets from this diagnostic). Included assets with a
genuinely missing previous price must remain incomplete; they must not be converted to PASS.

## Reconciliation delta

No post-fix rebuild was run because this report records investigation only.

| Metric | Before | After |
|---|---:|---:|
| `VALUATION_RECONSTRUCTION_FAILED` | 159 | pending narrow diagnostic fix |
| Previous value NULL rows | 156 | pending |
| Current value zero rows | 3 | pending |
| Explicit review rows | 0 | pending |
| True incomplete rows | not established | pending |
| Confirmed settlement-accounting defects | 0 | 0 |
| Unexplained valuation-availability rows | 0 proven | pending |
| Cash unexplained gaps | 0 | 0 |
| Account/day material mismatches | 0 | 0 |
| Monthly material mismatches | 0 | 0 |
| Portfolio validation FAIL | 0 | 0 |
| Account statistics `VALUE_MISMATCH` | 0 | 0 |

Settlement diagnostics do not drive the current account quality summary. Current quality
remains `CRITICAL`, with 7 reconciled and 4 unreconciled accounts; the settlement population
does not provide evidence of a settlement-accounting defect or explain that quality state.

## Tests

No code or database changes were made. Read-only JDBC queries and SQL lineage inspection were
used. Regression tests are required when the narrow settlement-scope fix is implemented.

## Next target

Apply and test the narrow excluded-asset scope correction, refresh the rebuilt chain, then
classify the 3 opening-boundary rows and any remaining included-asset missing-price rows. After
that, inspect the exact diagnostics responsible for the 4 unreconciled accounts and `CRITICAL`
quality state.
