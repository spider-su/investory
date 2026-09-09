# Long-Term Assets regression scenario

## Composition

Run these scenarios through Playwright MCP against the same named environment, portfolio, fixture,
authentication, and fixed desktop/narrow viewport definitions:

1. [`smoke/long-term-assets.md`](../smoke/long-term-assets.md)
2. [`active/long-term-assets.md`](../active/long-term-assets.md)
3. [`facts/long-term-assets.md`](../facts/long-term-assets.md)

The regression layer composes those scenarios; it does not duplicate their selectors or assertions.

## Additional regression checks

Compare the collapsed baseline with the final overview after safe detail navigation and history
operations. Confirm required categories, available asset representations, responsive layout,
console/page errors, relevant first-party requests, and mutation safety remain consistent across the
composed run. Record any scenario that is blocked or not applicable separately.

## Result

Report per-layer results and one overall PASS, SUSPICIOUS, FAIL, or BLOCKED. A FAIL must identify the
page/context, expected behavior, observed behavior, why it is incorrect, and supporting evidence.
