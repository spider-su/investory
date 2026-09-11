# Investory application read-only baseline report

Status: `SUSPICIOUS`

The deployed local application was exercised through Playwright MCP against HappyInvestor portfolio
`2`. Browser coverage completed for all discovered routes. The Java smoke suite remains blocked by a
runtime classpath defect, and five stale heading literals remain in `UiPageSmokeIT`; neither was
treated as an application rendering failure.

## Coverage matrix

| Page / route family | Smoke | Active | Facts | Desktop | Narrow | Console/network | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Home | PASS | N/A | NOT-YET-DEFINED | PASS | PASS | PASS | PASS |
| Dashboard | PASS* | PASS | PARTIAL | PASS | PASS | PASS | SUSPICIOUS* |
| Investment asset detail | PASS | PASS / period N/A | PARTIAL | PASS | PASS | PASS | PASS |
| Asset not found | PASS expected 404 | Back/reload N/A | NOT-YET-DEFINED | PASS | PASS | expected 404 console noise | PASS |
| Reconciliation | PASS | navigation/reload | NOT-YET-DEFINED | PASS | PASS | PASS | PASS |
| Long-Term Assets | PASS | PASS | STRONG | PASS | PASS | PASS | PASS |
| Long-term create forms (4) | PASS | inspect/Cancel | NOT-YET-DEFINED | PASS | PASS | PASS | PASS |
| Long-term detail/edit (7 seeded assets) | PASS | PASS; real-estate uses Back | STRONG | PASS | PASS | PASS | PASS |
| Investment profile | PASS | source link/reload | PARTIAL | PASS | PASS | PASS | PASS |
| Retirement simulation | PASS | disclosures/selectors inspected | PARTIAL | PASS | PASS | PASS | PASS |
| Retirement sandbox | PASS | inspect only; Calculate not used | NOT-YET-DEFINED | PASS | PASS | PASS | PASS |
| Plan editor | PASS | inspect/Cancel scope | PARTIAL | PASS | PASS | PASS | PASS |
| Retirement analysis | PASS | navigation/reload | NOT-YET-DEFINED | PASS | PASS | PASS | PASS |
| Live year review | PASS | navigation/reload | NOT-YET-DEFINED | PASS | PASS | PASS | PASS |
| Past year review | PASS | navigation/reload | NOT-YET-DEFINED | PASS | PASS | PASS | PASS |
| Integration settings | PASS | inspect only; no action | NOT-YET-DEFINED | PASS | PASS | PASS | PASS |

`*` Dashboard rendered correctly in Playwright MCP, but the Java manifest expects the stale literal
`Portfolio structure`; the current page uses semantic dashboard content. The same stale-literal
condition exists for Profile (`Income sources`), Simulation (`Plan timeline`), Sandbox
(`Retirement sandbox`), and Analysis (`Economic risks`). These are test-maintenance findings.

## Smoke result

- Discovered route cases: **23**.
- Playwright MCP executions: **46** route visits, 23 at `1440x1000` and 23 at `390x844`.
- HTTP statuses: **23/23 expected** on both viewports.
- Titles, body/main rendering, and application-error text: PASS.
- Narrow whole-page overflow: none; all tested pages reported `scrollWidth == clientWidth`.
- Intentional `UI-NOT-FOUND` route returned 404 as expected. Its console 404 message is expected
  error-route noise, not an application failure.

## Active result

- Shared dashboard YTD navigation: PASS; URL became `/portfolios/2/dashboard?period=YTD...`.
- Long-Term Assets: six disclosures expanded, annual-income tooltip opened, all seven seeded asset
  routes inspected, and all four create-form routes opened and exited with Cancel.
- Apartment A/B real-estate detail routes exited with their `Long-Term Assets` Back link.
- Profile Long-term assets source link: PASS.
- Simulation: selector/disclosure/scenario-link inventory completed without changing state.
- Sandbox: inspected without Calculate.
- Settings: inspected without submitting integration controls.
- Final Long-Term Assets reload retained `980.0K` and `75,037`.

## Facts result

Long-Term Assets remains `FACTS-STRONG` based on the existing HappyInvestor facts scenario and
complete-reference report. Verified current values include:

- portfolio value `980.0K`;
- current forward net annual income `75,037` (`75,037.375` exact);
- matured Treasury 2026 income `0`;
- replacement Treasury income `354 / year`;
- Apartment A/B, cash, personal asset, category, and aggregate reconciliation.

Other pages remain `FACTS-PARTIAL` or `FACTS-NOT-YET-DEFINED` as shown in the matrix. No new
page-specific facts scenario was invented.

## Browser health and mutation safety

Across the MCP smoke and active passes:

- non-GET requests: **0**;
- failed first-party requests: **0**;
- page errors: **0**;
- unexpected console errors: **0**; only the intentional 404 route emitted expected 404 noise;
- no Save, Calculate, refresh, import, export, archive, delete, or integration action was confirmed;
- persistent data changed: **No**.

## Confirmed defects

No application rendering or data defect was confirmed by this run.

## Suspicious findings and gaps

1. `UiPageSmokeIT` has five stale exact heading literals although the corresponding pages render
   correct semantic content. Classify as test defect/maintenance drift.
2. The Java smoke command is blocked before browser execution after a clean reactor rebuild by:
   `NoClassDefFoundError: com/smartbox/investory/investment/api/importing/ImportBroker`.
   This is a build/classpath blocker, not a UI result.
3. Dedicated facts coverage is missing for dashboard, profile, simulation, sandbox, reconciliation,
   analysis, year reviews, settings, and error/create-form pages.
4. One narrow active attempt found a tooltip overlay intercepting a disclosure click when focus was
   not moved away first. Retrying after moving focus away passed; classify as interaction-order
   `SUSPICIOUS`, not a confirmed product defect.

## Evidence

- Java test artifacts were not produced because application context initialization failed before
  route execution.
- MCP route/active results are recorded in the Playwright MCP session output for this report.

## Commands and scenarios

- Scenario sources: `qa/ui/smoke/application-read-only-baseline.md`,
  `qa/ui/active/application-read-only-baseline.md`,
  `qa/ui/facts/long-term-assets.md`, and
  `qa/ui/regression/application-read-only-baseline.md`.
- Java attempt:
  `MAVEN_USER_HOME=$PWD/.m2 ./mvnw -pl app -am -Dmaven.repo.local=$PWD/.m2/repository clean test -Dtest=UiPageSmokeIT -Dsurefire.failIfNoSpecifiedTests=false`
- Browser execution: Playwright MCP against `http://localhost:8080`, portfolio `2`.

## Recommendation

Application-wide read-only browser coverage is broad and mutation-safe. Resolve the Java
`ImportBroker` classpath blocker and update the five stale heading assertions before treating the
repository smoke suite as fully green. Keep Long-Term Assets as the reference facts page and add
page-specific facts scenarios incrementally; do not claim factual validation for pages currently
marked partial or not-yet-defined.
