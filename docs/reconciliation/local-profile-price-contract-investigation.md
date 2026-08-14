# Price / currency / scale contract investigation

Generated: 2026-08-12

Scope: local profile database. Material discrepancy rule remains:
`abs(diff) > max($20, 1% * max(abs(expected), abs(actual)))`.

## Finding

No safe data or SQL correction was made in this pass.

The remaining price diagnostics are explicit historical metadata conflicts, not
missing values. The current reconciliation views correctly compare the stored
`asset_price_history.price_currency` with the current source mapping and the
canonical asset currency. Relabeling the historical rows would require provider
payload/provenance evidence that is not stored in the row.

## SQL path

The relevant paths are:

```text
asset_price_history
  -> reporting_asset_price_quality_issues
  -> recon_v_asset_price_quality_issues

asset_price_history
  + assets
  + asset_source_symbols
  -> reporting_price_history_contract_issues
```

`reporting_asset_price_quality_issues` reports `PRICE_CURRENCY_MISMATCH` when
the stored price currency differs from the canonical asset currency without an
intentional FX mapping. It reports `PRICE_SCALE_MAPPING_MISMATCH` when the
stored scale differs from the mapping, or when the stored currency differs from
the mapping currency for a non-FX mapping. The latter means the issue name is
broader than its label; it is not a scale-only count.

`reporting_price_history_contract_issues` independently reports provider
mapping conflicts, including `STOOQ_CURRENCY_MISMATCH`.

## Before investigation counts

| Diagnostic | Count |
|---|---:|
| `PRICE_CURRENCY_MISMATCH` | 2,633 |
| `PRICE_SCALE_MAPPING_MISMATCH` | 2,038 |
| `EXTREME_SAME_DATE_SOURCE_DISAGREEMENT` | 7 |
| `STOOQ_CURRENCY_MISMATCH` | 2,038 |
| `XTB_QUOTE_CURRENCY_MISMATCH` | 393 |
| `GENERATED_PRICE_CURRENCY_MISMATCH` | 541 |

All affected price rows have an explicit stored currency. The problem is
conflicting metadata, not a NULL that can be filled from the account currency.

## Main STOOQ conflict

The 2,038 STOOQ currency conflicts are concentrated in four assets:

| Asset | Canonical asset currency | Current mapping currency | Stored history currency | Rows |
|---|---|---|---|---:|
| `IDUS.UK` | USD | USD | EUR | 512 |
| `CNDX.UK` | USD | USD | PLN | 512 |
| `NUCL.UK` | USD | USD | PLN | 507 |
| `XDWT.DE` | EUR | EUR | PLN | 507 |

The current V01.004 mappings explicitly define the first three as USD and
`XDWT.DE` as EUR. `XDWT.DE` is an approved alternate listing. The historical
rows cover mostly 2024-07-02 through 2026-07-02, with one later correctly
mapped row. This pattern is consistent with an earlier import/normalization
path storing the wrong row currency, followed by a corrected current mapping.
The repository has no mapping-version or raw-provider-currency snapshot that
proves which currency was correct for each historical row.

Other `PRICE_CURRENCY_MISMATCH` rows are distributed across generated
carry-forward/interpolated XTB prices, IBKR, and XTB trade-derived prices. They
need separate provenance checks; they were not changed here.

## Propagation check

Current local reconciliation after the position-currency fix remains:

| Metric | Count |
|---|---:|
| Material position valuation mismatches | 307 |
| Those sharing an asset/date with a price currency/scale issue | 6 |
| Material mismatches with `MISSING_PRICE` | 0 |
| Cash-flow material gaps | 282 |
| Account/day material mismatches | 78 |
| Monthly material mismatches | 39 |
| Portfolio validation `FAIL` | 222 |
| Settlement `VALUATION_RECONSTRUCTION_FAILED` | 159 |

The current price-contract group therefore explains only 6 of 307 material
position valuation mismatches by asset/date overlap. No causal propagation into
the broader counts was proven. The full post-position-currency report remains
in `local-profile-mv-report-after-position-currency-fix.md`.

## Decision

Do not:

- overwrite imported historical currencies;
- use asset, account, or portfolio currency as a fallback;
- apply FX conversion based only on the current mapping;
- widen the materiality threshold;
- change import behavior without provider-row evidence.

The clean migration integration fixture already passes its price-history contract
diagnostic, so this is local historical-data/provenance state rather than a
demonstrated migration-view defect.

## Next target

Investigate **position valuation**, starting with the six material valuation
rows that overlap a price currency/scale issue. Compare their stored price row,
source mapping, FX conversion, and selected valuation source. If those six do not
explain the large valuation differences, continue with the 301 remaining
valuation mismatches before investigating cash-flow or account/day layers.

