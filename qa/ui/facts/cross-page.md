# HappyInvestor cross-page facts reconciliation

## Scope

Small cross-page guard for concepts intentionally shared by Dashboard, Profile/Income, Portfolio
reporting, and Long-Term Assets. It does not duplicate page assertions.

## Relationships

Verify only after each source page passes its fixture precondition:

| Concept | Source | Target | Relationship |
| --- | --- | --- | --- |
| Portfolio identity/currency | Dashboard page data | Profile and reporting pages | same portfolio and reporting currency |
| Long-Term value | Long-Term Assets overview | Profile allocation/source card | same current value, presentation-rounded |
| Long-Term forward income | Long-Term Assets overview | Profile Long-Term annual income | same as-of-date forward concept |
| Market value | Dashboard reporting model | Profile Market investments value | same reporting scope and as-of date |
| Combined net worth | Profile | Dashboard/reporting where exposed | compare only where the page labels the same whole-wealth scope |
| YTD investment result/income | Dashboard/Profile | reporting view | compare only with matching period and definition |

Do not equate current forward Long-Term income with market YTD income, projected income, or
historical boundary income. Record date, period, scope, currency, and rounding for every comparison.

Use the canonical chain `Happy Investor story → scenario / source-operation ledger → domain and
reporting contracts → canonical facts → UI expectations` for all metric meaning. External deposits
and withdrawals retain the contracted flow treatment. If pages disagree, reconcile the story fact
and authoritative contract before classifying a product defect; live market prices and FX are
separate non-deterministic observations.

## Result rules

Use `PASS`, `EXPECTED ROUNDING / LIVE DATA DRIFT`, `STALE CONTRACT / TEST DEFECT`, `PRODUCT
DEFECT`, `BUILD DEFECT`, `ENVIRONMENT BLOCKER`, or `SUSPICIOUS / NEEDS RECONCILIATION`. If one
source page is the wrong fixture, block the cross-page comparison rather than manufacturing a
reconciliation.
