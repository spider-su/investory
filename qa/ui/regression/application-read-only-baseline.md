# Application read-only baseline regression

## Composition

Run the application smoke and active scenarios over the discovered route inventory, with the
Long-Term Assets smoke, active, and facts scenarios retained as the deep reference page. Run each
page independently enough that one page failure does not hide unrelated page results.

## Result rules

Use `PASS`, `EXPECTED ROUNDING / LIVE DATA DRIFT`, `STALE CONTRACT / TEST DEFECT`, `PRODUCT
DEFECT`, `BUILD DEFECT`, `ENVIRONMENT BLOCKER`, or `SUSPICIOUS / NEEDS RECONCILIATION`. Keep skipped, blocked, and unexecuted checks separate. A
browser, deployment, authentication, or fixture problem is an environment/precondition blocker,
not an application failure. A product defect needs concrete expected-versus-observed evidence after
source-ledger and contract reconciliation.

## Cross-page checks

Only compare intentionally shared concepts: dashboard/profile investment totals, profile/long-term
asset totals, and dashboard/profile income or investment-result summaries. Respect date, period,
scope, currency, and presentation rounding. Do not compare unrelated dashboard and retirement
projection values.

## Facts maturity

Long-Term Assets remains `FACTS-STRONG`. Dashboard, profile, investment asset detail, plan editor,
and simulation projection are `FACTS-PARTIAL` through existing HappyInvestor coverage. Error pages,
forms, sandbox, reconciliation, retirement analysis, year reviews, and integration settings are
`FACTS-NOT-YET-DEFINED` for dedicated factual assertions; their baseline is structural/read-only
only until a canonical facts scenario is added.
