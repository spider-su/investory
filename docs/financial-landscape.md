# Financial Landscape — decisions and local draft

Status: **Calculation and reserve mapping reconciled; final live snapshot check incomplete**. The development database returned changing asset valuations during the read window, and a later Profile request timed out. See the evidence and remaining check below. First implementation branch: `codex/financial-landscape-draft`.

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
- `FinancialLandscapeController`: resolves the active plan once, loads a single `RetirementProjection`, and derives profile assets plus LIVE timeline values from that shared projection.
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
- [x] Run controller and related Profile/Retirement tests.
- [x] Application compiles and starts; authenticated pages rendered.
- [x] Income/expenses match Simulation LIVE annual amounts and monthly averages.
- [x] Liquid/illiquid values matched Profile in the first same-snapshot comparison.
- [x] Investment returns and unrealized P/L are excluded from cash income.
- [ ] Fullscreen works on desktop and mobile; prefers-reduced-motion stops stream animation.
- [ ] No portfolio data is visible without normal authorization.
- [x] Controller tests cover portfolios with missing plan or incomplete LIVE data.
- [ ] No significant performance regression.

## Known limitations / next iteration
- The present prototype uses static geometric dunes; their height is not yet proportional to capital.
- The sand animation is constant-speed decorative motion, not physical simulation.
- Categories, target reserve horizon, interactive asset breakdown, scenario/year selection, automated UI tests, HA integration, and accessibility polish remain follow-ups.
- Current LIVE year and shared display currency were verified against Profile and Simulation.
- Keep the GET read-only; do not call explicit planning rollover/ensure operations from this page.

## Compact view (same source of truth)
- URL: `/portfolios/{portfolioId}/financial-landscape?view=compact`.
- Full URL remains `/portfolios/{portfolioId}/financial-landscape`.
- Both render from the **same** controller data map; Compact also shows the cash reserve included in liquid assets.
- Compact has no separate backend, persistence, or calculations; it links back to Full.
- Full and Compact were rendered in an authenticated local browser session.

## Screenshot test follow-up (2026-10-13)

The first full-mode browser screenshot exposed overlapping mountain artwork, wrapped net cash-flow values, insufficient contrast for asset totals and a fragile absolute-position layout. Follow-up commits on this same PR:
- Use CSS grid regions for income, hourglass, net cash flow, expenses and asset panels.
- Reduce the opacity of purely decorative mountains; keep data on solid, contrasting surfaces.
- Add narrow-screen stacking and wrapping safeguards.
- Color net cash flow according to sign, rather than showing a negative number in positive green.
- Show **illustrative cash-reserve runway** only for a negative planned net flow and a known nonnegative available reserve: `reserve / (-annualNet / 12)`. This is a simplified static ratio, not a retirement projection.
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

No automated browser suite or device verification is claimed. Local authenticated browser rendering was checked during the database reconciliation below.

## Financial hierarchy iteration (2026-10-13)

A subsequent set of commits on PR #135 adds:
- A bounded 0–100% visual coverage bar; the text retains the actual coverage percentage, including values over 100%.
- A projected annual gap derived from the same annual income/expense inputs as monthly net flow.
- Net worth from the existing landscape payload, without recomputing or modifying the domain model.
- Corrected `LIQUID ASSETS` label (the previous `LIQUID RESERVE` label was misleading).
- Consistent sign coloring for full and compact net flow, and a responsive three-value asset summary.

These presentation changes are covered by the local database reconciliation and verification report below.

## Live database reconciliation (2026-10-13)

### Scope and identity

- IDE data source and `application-local.yml` both point to PostgreSQL `inventory` on `192.168.1.60:5432`, schema `investory`. The identity query ran in a `READ ONLY` transaction and returned `transaction_read_only=on`.
- Local profile configuration selects portfolio `1`; the database confirms base USD and local/display PLN. The authenticated Simulation page selected plan `1` and BASE, with the 2026 row marked LIVE.
- Values below came from the development database at verification time. The first screenshot values are comparison observations, not database expectations.
- Local application started with the `local` profile and Flyway disabled for this read. Hibernate schema validation remained enabled. Full, Compact, Profile, Simulation and Profile JSON requests returned HTTP 200.

### Source and calculation reconciliation

| Metric | Database source and calculation | Independently calculated | Landscape output | Difference | Status |
| --- | --- | ---: | ---: | ---: | --- |
| Annual cash income | Profile rental/bond cash income plus plan employment, pension and event amounts; employment/pension inactive and no one-off events in selected year | Computed from the stated sources | Landscape agrees with source calculation | Below cent | Match |
| Annual expenses | Plan living expenses + discretionary extras; no one-off expense in selected year | Computed from plan assumptions | Landscape agrees with plan calculation | Below cent | Match |
| Monthly income | Annual income / 12 | Correct whole-PLN rounding | Same displayed amount | None at display precision | Match |
| Monthly expenses | Annual expenses / 12 | Correct whole-PLN rounding | Same displayed amount | None at display precision | Match |
| Monthly cash flow | (Annual income − annual expenses) / 12 | Correct whole-PLN rounding | Same displayed amount | None at display precision | Match |
| Income coverage | Annual income / annual expenses | Correct whole-percent rounding | Same displayed amount | None at display precision | Match |
| Liquid assets | Profile allocation: brokerage positions and cash plus Long-Term cash available for funding | Matched in first same-snapshot comparison | Same Profile value | None | Match in first snapshot; later snapshot pending |
| Illiquid Long-Term assets | Profile allocation for non-available Long-Term assets | Matched | Same Profile value | None | Match |
| Net worth | Profile market portfolio value + total Long-Term asset value | Matched | Same Profile value | None | Match in first snapshot; later snapshot pending |
| Cash reserve | Positive brokerage cash + Long-Term cash marked funding-available; included in liquid assets | Matched | Same Profile value | None | Match in first snapshot |
| Annual gap | max(annual expenses − annual income, 0) | Derived from authoritative timeline gap | Same timeline value | Below cent | Match |
| Annual surplus | max(annual income − annual expenses, 0) | Zero for the selected year | Same | None | Match |

In the first matched Profile/Landscape snapshot, Profile's market, Long-Term, net-worth, liquid, illiquid and reserve fields matched Landscape's corresponding source values. Liquid plus illiquid differed from net worth by less than one grosz due to conversion precision. Net worth uses market plus Long-Term total; the reserve is not added a second time.

The separate reserve is the appropriate cash input for runway. Profile defines it as positive brokerage cash plus available Long-Term cash. `liquidAssets` also includes brokerage positions, so it is not a spendable-cash proxy. The illustrative static runway is reserve divided by monthly deficit; positive or zero flow shows no deficit; missing flow or missing/negative reserve shows unavailable. A zero reserve with a known deficit shows zero months.

### Cross-screen comparison and observed differences

- Full and Compact showed the same current data and rounded output in the first matched read. Profile API values for net worth, liquid assets, reserve, currency and current rental/bond cash income matched Landscape inputs. Profile's “Annual Income / year planned” is a different measure: it combines expected brokerage investment result with Long-Term annual income. Landscape uses current-year cash income and excludes investment returns.
- Simulation's LIVE row showed spending and cash income consistent with Landscape. Its `Gap / surplus` includes investment returns, as the Simulation table tooltip states; Landscape reports cash-only gap/surplus.
- Compared with the first screenshot observations, annual income, rounded monthly values, coverage, illiquid Long-Term assets and rounded annual gap remain the same. Liquid assets, reserve and net worth differ from screenshot observations; the old screenshot has no matching database snapshot or valuation timestamp, so the change cannot be attributed further from available evidence.
- A later post-restart Full render's liquid-assets value differed from the first matched snapshot. The Profile JSON request then timed out, so that later valuation could not be independently matched or attributed. Do not treat the earlier exact asset rows as a final same-snapshot reconciliation.
- Per-minute rates use the documented average-year denominator `365.2425 × 24 × 60`. These are annual averages, not transaction timing.

### Read-path correction and verification

- Removed `timeline.ensurePlanningTimeline(portfolioId)` from the GET controller. That operation invokes annual rollover in a write transaction and can create planning rows. The page now uses the existing read-only forward timeline; the controller test verifies that it never calls ensure/rollover.
- Full and Compact render from the same controller map. Profile, Simulation and Profile JSON were read for comparison; no mutation request was issued. The planning timeline loader is `@Transactional(readOnly = true)`.
- Added a separately visible reserve and clarified it is included in liquid assets. Renamed the display to illiquid Long-Term assets so the label matches `InvestmentProfile.illiquidAssets()`.
- Runway behavior was exercised for negative, positive, zero and unavailable cash flow. All four passed. A negative case with zero reserve also returns zero months.
- Implementation and reconciliation report: commit `3d6c1005` on the existing PR branch.

### Verification completed

- `FinancialLandscapeControllerTest`, `ProfileQueryServiceTest`, `PlanningCurrencyPresentationServiceTest`, `PlanningTimelineFacadeTest`, and `RetirementPlanningApplicationServiceTest`: passed (40 tests total).
- `spotless:check` initially identified formatting in the two changed Java files; `spotless:apply` corrected those files. Re-run the check after final edits.
- Local app build/start and authenticated Full and Compact GETs succeeded. Browser snapshots confirmed monthly values, coverage, reserve runway and asset labels. Simulation LIVE 2026 and Profile views were inspected.
- No real-data integration test was run; the live app GET used the development database with Flyway disabled. Unit tests and the rendered live read path were used instead.
- **Final recommendation: NOT READY** until a fresh read-only Profile and Landscape comparison completes against the same stable database snapshot. Formulas, cash-only income mapping, currency and reserve semantics passed; exact current liquid-assets/net-worth reconciliation remains outstanding.

## Landscape visual encoding v2
- Full and Compact now share CSS variables populated by the existing browser-side presentation script.
- Dune height uses a bounded square-root normalization of liquid assets / (liquid + illiquid assets).
- Mountain height uses the same normalization for illiquid assets. These are **relative visual encodings**, not absolute comparable quantities across portfolios.
- Decorative sand opacity and speed vary with the ratio of planned annual income to planned annual expenses; no simulation time is advanced, and no actual account balance is animated or persisted.
- Full includes a foreground dune and stronger layered mountain silhouettes; Compact includes a small version of both.
- Reduced-motion preference disables sand animation.
- Manual acceptance still required: inspect at 1440×900, 1024×768, 390×844; check overlay collisions, contrast, fullscreen, zero/missing amounts and console errors. No Playwright screenshots have been captured in this environment.
## Profile reconciliation and semantic color scheme
- Primary projected economic income comes from `InvestmentProfile.incomeSummary().combinedNetAnnualIncome()`, consistent with Profile.
- Planning cash income remains separately available as `annualCashIncome` from the LIVE timeline's `totalIncome()`; it is **not** additive to the Profile economic result.
- Profile's market result is shown in blue; spendable planning cash in gold. These are distinct perspectives, not necessarily a partition of the same sum. In particular, do not assume that all long-term asset returns are spendable.
- Annual expenses remain from LIVE Retirement Planning.
- Neutral cash-flow presentation applies when absolute annual economic difference is at most 2% of annual expenses. Display the signed amount regardless of color.
- Reserve accent direction is determined by LIVE planning funding gap or surplus, **not** economic return.
- A reserve-direction highlight is illustrative only; it does not mutate balances. This iteration does not yet depict a physically connected particle stream between the dune and the hourglass.
- Check the live Profile numbers against the page and inspect if combinedNetAnnualIncome is based on a different reporting horizon from the planning expenses.

## Cinematic Full prototype
- Full scene now uses layered sunset gradients, distant/near mountains, a reflective lake, a luminous foreground dune, six decorative sand trails and a dedicated Canvas particle layer.
- Canvas follows the rendered hourglass bounding box and scales to scene dimensions; it is decorative, not an accounting simulation.
- Reduced-motion preference disables the Canvas layer and CSS particle motion. The Canvas animation pauses while the page is hidden.
- This is a first implementation pass, **not** a pixel-perfect reproduction of the reference artwork. The generated design concept is not bundled as a binary background asset in this commit; artwork is CSS/SVG/Canvas.
- Expense trails are decorative placeholders, **not** six claimed spending categories. Actual category amounts require a verified Investory source.
- Required before merge: compile, run, inspect desktop/mobile and fullscreen screenshots, check animation performance and semantic income-versus-cash-flow consistency.
