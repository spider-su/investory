# Neon historical asset-price integrity audit

Date: 2026-09-09

Database: Neon `investory`, PostgreSQL 18.6, read-only audit. The supplied connection was used over SSL. No production rows, views, refreshes, or rebuilds were changed.

## Executive result

The audit analyzed 136,908 `asset_price_history` rows across 308 assets, 234 source mappings, 9,400 positions, and 9,250 `account_daily` rows.

| Check | Result |
|---|---:|
| observed transitions | 135,964 |
| transitions >10% / >20% / >30% / >50% / >100% | 2,885 / 668 / 286 / 101 / 25 |
| ratio >=5x or <=0.2x; >=10x or <=0.1x; >=100x or <=0.01x | 14 / 8 / 1 |
| short-gap (1-3 days) >10%; medium (4-14); long (>14) | 1,571 / 307 / 1,007 |
| history-to-mapping currency mismatches | 2,548 rows |
| in-series currency switches | 36 |
| history-to-mapping scale mismatches / scale switches | 0 / 0 |
| isolated short-gap spike/reversion patterns | 1 |
| existing quality / contract / currency-integrity issues | 1,126 / 890 / 2,548 |
| price-mismatch rows selected by reconstructed valuation | 0 |
| NUCL valuation rows carrying stale PLN attribution | 215 across 3 accounts |

## Confirmed bugs

### P0: stale derived valuation metadata for NUCL.UK

Raw history and `app_v_canonical_asset_daily_price_mv` contain `NUCL.UK` 2025-11-03 at `64.16 USD`, matching mapping 114 (`STOOQ nucl.uk`, USD, scale 1). However, the materialized `app_v_normalized_daily_price_mv` and downstream `recon_v_reconstructed_position_daily_mv` expose the same selected price as `PLN` and apply the PLN-to-base rate `0.27076057`.

On 2025-11-03 this affects:

* account `51499241` (`Trading USD`): stale value `119.35`, correct USD-base value `440.79`, understatement `321.44`;
* account `51551301` (`IKE Alex`): stale value `281.85`, correct USD-base value `1,040.96`, understatement `759.11`.

The account row for `IKE Alex` shows `daily_return_pct = -0.11787652`, matching the known apparent approximately -11.8% move. The defect is in stale derived/materialized valuation state, not the current raw NUCL history row. Minimum repair scope is profile/portfolio 1, the three affected accounts, and the affected reconstructed account-date range; do not perform a global rebuild.

### P1: exact STOOQ history currency attribution mismatches

The following exact, non-alternate mappings require USD and `requires_fx_conversion=false`, but their history rows carry another currency:

| Asset | Stored currency | Mapping | Rows | Date range | Position impact |
|---|---|---|---:|---|---|
| EIMI.UK | PLN | USD | 513 | 2024-07-02..2026-07-02 | no positions |
| LQDA.UK | PLN | USD | 512 | 2024-07-02..2026-07-02 | no positions |
| AGGG.UK | PLN | USD | 509 | 2024-07-02..2026-07-02 | no positions |
| EMIM.UK | EUR | USD | 507 | 2024-07-02..2026-07-02 | no positions |
| IGLN.UK | PLN | USD | 507 | 2024-07-02..2026-07-02 | no positions |

These are confirmed metadata/import defects because the mapping is exact, the mapping disallows implicit FX, and the mismatch persists across the full STOOQ history. They were not selected by reconstructed valuation in this Neon dataset.

### P1: EMIM.UK isolated 100x price spike

`EMIM.UK` has the sequence `0.2724 EUR` on 2024-12-31, `27.2400 USD` normalized from raw `2724` on 2025-01-01, and `0.2757 EUR` on 2025-01-02. The mapping is exact USD with scale `0.01`; all three rows use scale `0.01`. This is an isolated 100x spike and currency attribution anomaly, not a scale-factor mismatch. No EMIM positions exist, so no valuation impact is present in this database.

## Likely or externally verifiable anomalies

* `NFLX.US` has selected short-gap trade observations around 2025-11-16..18 with approximately 10x transitions (`1110.38 -> 119.34`, `1181.21 -> 114.23`). This is consistent with a corporate-action boundary and the rows are selected for account `51499241`; classify `EXTERNAL_VERIFICATION_REQUIRED`, not as a confirmed provider error. The account-day movement is not attributable to a currency or scale mismatch from this audit alone.
* Other short-gap STOOQ moves include SBET, NKTR, APLD, RGTI, QBTS, MP, and similar symbols. They do not show the isolated neighbor-recovery pattern. They require market/corporate-action verification before classification.
* `PALL.US` switches between STOOQ prices near 27 and XTB trade prices near 129-140 on the same dates. This is a provider/quote-convention transition candidate, but the source metadata does not prove corruption; retain as `EXTERNAL_VERIFICATION_REQUIRED`.

## Explained or separate noise

* 7,085 stale carry-forward rows cover 95 assets; maximum source age is 582 days. These are stale valuation freshness risks, not daily market returns.
* 36 in-series currency switches are mostly source/provider or listing boundaries; the audit found no simultaneous scale-factor switch. Legitimate alternate/cross-currency listings remain valid when mapping metadata explicitly describes them.
* 25 >100% transitions are dominated by long gaps, stale carry-forward transitions, trade observations, or corporate-action candidates. They must not be treated as one-day market moves.

## Pipeline evidence

* `asset_source_symbols` stores provider/listing currency, mapping flags, and scale.
* `asset_price_history` stores the observation currency and scale; the trigger binds or validates `source_mapping_id` but does not overwrite the row currency.
* Canonical selection materializes one row per asset/date, then normalized selection applies `close_price * price_scale_factor` exactly once and carries that row's currency.
* Reconstructed valuation joins the selected price currency to the valuation FX rate and calculates market value from quantity × selected price × contract multiplier × FX.
* Current Neon has Flyway version `01.014` installed, while this checkout contains migrations through `01.011` and no local `01.014`. Therefore Neon derived-view state is ahead of, and not fully reproducible from, this checkout. The stale normalized materialized view must be treated as live-database evidence, not assumed to be current develop behavior.

## Existing coverage and gaps

Existing Neon views detect currency mismatches, same-date source disagreement, selected-price status, missing prices, alternate listings, interpolated prices, and trade observations. They do not provide complete thresholded gap-aware classification, a persisted isolated-spike issue in the installed schema, or a reliable stale-materialized-view freshness invariant.

## Recommended next actions

* P0: refresh/rebuild only the affected profile/portfolio derived valuation state after preserving before/after evidence; verify raw history, normalized selection, reconstructed valuation, and account-day deltas.
* P1: regenerate the five exact STOOQ series from authoritative provider observations with mapping currency preserved; separately regenerate EMIM's 2025-01-01 row and verify one-time scaling.
* P1: add a materialized-view freshness/version invariant so corrected history cannot coexist with stale normalized valuation metadata.
* P2: add regression cases for NUCL-style stale derived currency, EMIM isolated spike, exact-once scaling, legitimate alternate listings, and corporate-action/provider transitions.

