# Long-Term Assets smoke scenario

## Scope

Fast, read-only rendering check for the Long-Term Assets page in the named environment and portfolio.

## Route

- `/portfolios/{portfolioId}/long-term-assets`

The runner supplies the exact environment, portfolio, authentication, and fixture. Before fact
assertions confirm the visible profile and expected fixture; a wrong or missing fixture is `BLOCKED`,
not a product failure.

## Observations

Verify page identity and visible application shell, the four supported category areas when data exists,
useful summary/allocation content, and absence of loading, empty, access-denied, server-error, or
exception states at `2560x1440`. Inspect page errors, relevant console errors, failed or unexpected
first-party requests, and obvious clipping or overlap. Alternate viewport and breakpoint coverage
are out of scope. Capture screenshots only when they materially prove a visual or state finding.

Reconcile financial values with the canonical Happy Investor story and authoritative contracts;
classify findings as `PASS`, `EXPECTED ROUNDING / LIVE DATA DRIFT`, `STALE CONTRACT / TEST DEFECT`,
`PRODUCT DEFECT`, `BUILD DEFECT`, `ENVIRONMENT BLOCKER`, or `SUSPICIOUS / NEEDS RECONCILIATION`.

## References

Use `UiPageSmokeIT`, `HappyInvestorReadOnlyUiIT`, and `LongTermAssetsReadOnlyStress` as executable
coverage references; this scenario does not duplicate their selectors.
