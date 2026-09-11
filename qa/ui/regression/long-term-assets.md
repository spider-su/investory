# Long-Term Assets regression scenario

## Composition

Run these scenarios through Playwright MCP against the same named environment, portfolio, fixture,
authentication, and the fixed `2560x1440` viewport:

1. [`smoke/long-term-assets.md`](../smoke/long-term-assets.md)
2. [`active/long-term-assets.md`](../active/long-term-assets.md)
3. [`facts/long-term-assets.md`](../facts/long-term-assets.md)

The regression layer composes those scenarios; it does not duplicate their selectors or assertions.

## Additional regression checks

Compare the collapsed baseline with the final overview after safe detail navigation and history
operations. Confirm required categories, available asset representations, obvious desktop rendering
defects,
console/page errors, relevant first-party requests, and mutation safety remain consistent across the
composed run. Record any scenario that is blocked or not applicable separately.

## Result

Report per-layer results and one overall `PASS`, `EXPECTED ROUNDING / LIVE DATA DRIFT`, `STALE
CONTRACT / TEST DEFECT`, `PRODUCT DEFECT`, `BUILD DEFECT`, `ENVIRONMENT BLOCKER`, or `SUSPICIOUS /
NEEDS RECONCILIATION`. A product defect must identify
the page/context, expected behavior, observed behavior, why it is incorrect, and supporting evidence.
