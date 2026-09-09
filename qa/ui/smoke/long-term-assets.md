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
exception states. Check desktop and narrow/mobile rendering for catastrophic clipping, overlap, or
horizontal overflow. Inspect page errors, relevant console errors, and failed or unexpected
first-party requests. Capture screenshots only when they materially prove a visual or state finding.

## References

Use `UiPageSmokeIT`, `HappyInvestorReadOnlyUiIT`, and `LongTermAssetsReadOnlyStress` as executable
coverage references; this scenario does not duplicate their selectors.
