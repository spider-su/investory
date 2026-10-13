# Financial Landscape — decisions and local draft

Status: **Draft / local validation required**. Controller unit tests have been added but have **not been executed**; compile and browser checks remain required. First implementation branch: `codex/financial-landscape-draft`. Do not merge before running and reviewing the page.

## Purpose
A calm, ambient financial landscape inside Investory. The hourglass represents average planned income and expenses; sand dunes represent liquid and illiquid capital. This is a visualization of existing financial facts, **not** transaction replay, an accounting engine, or a replacement for Profile/Retirement Planning.

## Decisions (2026-10-13)
1. Deliver as an independent read-only Investory page at `/portfolios/{portfolioId}/financial-landscape`; Home Assistant integration is deferred.
2. Reuse existing Profile and Retirement Planning readers. No new database tables, financial formulas, background jobs, or external data providers.
3. Default to the BASE scenario and current LIVE planning year. Use full-year values divided by 12 for monthly averages, and by 365.2425×24×60 for per-minute display. These are **averages**, not actual monthly accounting.
4. Source annual cash income from `PlanningTimelineMoney.totalIncome()`; annual spending from `annualCosts()`. **Never** add `bondReturn`, `equityReturn`, withdrawals, or unrealized gains to income. Withdrawals are funding, not earnings.
5. Source asset mountains from `InvestmentProfile.liquidAssets()` and `illiquidAssets()`, in the Profile display currency. Do not imply all illiquid assets are passive reserves.
6. The sand stream is purely decorative and continuously smooth. Client animation does not change actual balances. Data is loaded once with the page.
7. Do not invent category breakdowns: no spending-category streams until a real categorized spending source exists.
8. No publicly exposed anonymous finance API. Reuse existing authenticated portfolio route.
9. Fullscreen is a presentation mode, not a separate service.
10. Follow-up: optional HA iframe/ambient mode only after local UX and security validation.

## Implementation in this draft
- `FinancialLandscapeController`: reads `ProfileClient`, `RetirementPlanClient`, `RetirementProjectionClient`, `RetirementTimelineClient`; selects LIVE row.
- `financial-landscape.html`: Thymeleaf page; server data embedded into a page-local JS object.
- `financial-landscape.css`: responsive dark landscape, sand dunes, SVG hourglass.
- `financial-landscape.js`: currency formatting, averages, fullscreen toggle, no polling.

## Local run
```bash
git fetch origin
git switch codex/financial-landscape-draft
./mvnw -pl app -am spring-boot:run
```
Log in with the development account as described in the project README and open
`http://localhost:8080/portfolios/<YOUR_PORTFOLIO_ID>/financial-landscape`.

The page depends on the same local DB, portfolio, and retirement plan setup as Profile and Simulation. It may show "Unavailable" when no LIVE planning year or annual income/expenses exist. The current version does not add a navigation menu entry: access it directly by URL.

## Local acceptance checklist
- [ ] Run controller unit tests: `./mvnw -pl adapters/web-ui -am -Dtest=FinancialLandscapeControllerTest -Dsurefire.failIfNoSpecifiedTests=false test` (verify Maven module selector in local checkout).\n- [ ] Application compiles and starts; page renders under authenticated portfolio.
- [ ] Income/expenses match Simulation LIVE annual amounts divided by 12.
- [ ] Liquid/illiquid values match Profile in the same display currency.
- [ ] Annual investment result/unrealized P/L are **not** counted as cash income.
- [ ] Fullscreen works on desktop and mobile; prefers-reduced-motion stops stream animation.
- [ ] No portfolio data is visible without normal authorization.
- [ ] No errors for portfolios with missing plan or incomplete LIVE data.
- [ ] No significant performance regression.

## Known limitations / next iteration
- The present prototype uses static geometric dunes; their height is not yet proportional to capital.
- The sand animation is constant-speed decorative motion, not physical simulation.
- Categories, target reserve horizon, interactive asset breakdown, scenario/year selection, automated UI tests, HA integration, and accessibility polish remain follow-ups.
- Verify current LIVE year semantics and ensure money fields share a display currency before shipping.
- Verify whether timeline `ensurePlanningTimeline` has writes in the request path; if so, move to an existing safe read-only initialization flow before calling this feature production-ready.

## Compact view (same source of truth)
- URL: `/portfolios/{portfolioId}/financial-landscape?view=compact`.
- Full URL remains `/portfolios/{portfolioId}/financial-landscape`.
- Both render from the **same** controller data map; compact shows income, expenses, net monthly flow, and liquid assets.
- Compact has no separate backend, persistence, or calculations; it links back to Full.
- Added a compact controller unit test; browser/Playwright validation still pending.

## Screenshot test follow-up (2026-10-13)

The first full-mode browser screenshot exposed overlapping mountain artwork, wrapped net cash-flow values, insufficient contrast for asset totals and a fragile absolute-position layout. Follow-up commits on this same PR:
- Use CSS grid regions for income, hourglass, net cash flow, expenses and asset panels.
- Reduce the opacity of purely decorative mountains; keep data on solid, contrasting surfaces.
- Add narrow-screen stacking and wrapping safeguards.
- Color net cash flow according to sign, rather than showing a negative number in positive green.
- Show **illustrative liquid-asset runway** only for a negative planned net flow and a known nonnegative liquid-asset balance: `liquidAssets / (-annualNet / 12)`. This is a simplified ratio, not a retirement projection or a guarantee that all assets are available for spending.
- Reject non-finite numeric inputs for display; do not silently present NaN as a monetary amount.
- Hide decorative landscape layers from assistive technology.

### Required validation before merge
- [ ] Run Maven controller tests and application compile.
- [ ] Test Full and Compact in browser at 1440px, 1024px, 768px, 390px and 320px.
- [ ] Verify no overlap, clipping, horizontal scroll or unreadable contrast at those widths.
- [ ] Compare income/expenses/net, currency and year against the authoritative LIVE planning timeline; validate same-currency assumption for liquid assets.
- [ ] Verify runway for negative, zero, positive and unavailable cash flow; distinguish total liquid assets from the separately reported reserve amount.
- [ ] Check fullscreen, reduced-motion, keyboard focus and missing-data behavior.
- [ ] Verify that read-only rendering does not mutate retirement planning data.

No automated browser/device verification is claimed by these follow-up commits.

## Financial hierarchy iteration (2026-10-13)

A subsequent set of commits on PR #135 adds:
- A bounded 0–100% visual coverage bar; the text retains the actual coverage percentage, including values over 100%.
- A projected annual gap derived from the same annual income/expense inputs as monthly net flow.
- Net worth from the existing landscape payload, without recomputing or modifying the domain model.
- Corrected `LIQUID ASSETS` label (the previous `LIQUID RESERVE` label was misleading).
- Consistent sign coloring for full and compact net flow, and a responsive three-value asset summary.

These are presentation changes only. Do not merge before local real-DB reconciliation, browser responsiveness review, controller tests and verification of runway/reserve semantics. No runtime verification is claimed by these commits.
