# Application read-only baseline smoke scenario

## Scope

Base rendering coverage for every meaningful rendered page discovered from the MVC controllers,
templates, navigation, and `UiPageSmokeIT`. API, health, static-resource, and mutation endpoints
are out of scope. Portfolio routes use portfolio `2` and plan `9201`, matching the existing smoke
fixture; the canonical HappyInvestor facts run uses portfolio `1` where applicable.

## Route inventory

| Area | Routes |
| --- | --- |
| Entry and investment | `/`, `/portfolios/2/dashboard`, `/portfolios/2/dashboard/assets/AAPL.US`, `/portfolios/2/dashboard/assets/UI-NOT-FOUND`, `/portfolios/2/dashboard/reconciliation` |
| Long-term assets | `/portfolios/2/long-term-assets`, four create forms, four asset detail/edit routes, and the real-estate detail route |
| Profile and retirement | `/portfolios/2/investment-profile`, `/portfolios/2/simulation`, `/portfolios/2/simulation/sandbox`, `/portfolios/2/simulation/plan/edit`, `/portfolios/2/analysis`, current-year timeline, and 2025 timeline |
| Settings | `/settings/integrations` |

The sandbox route was found in `RetirementSimulationController` and added to the executable smoke
manifest; it was not invented from the prompt.

## Checks

For each route, the runner checks response status, title, visible body/main content, stable page
heading, application-error text, page/console errors, failed first-party requests, and browser
requests for unexpected non-GET methods. The same route set is checked at `1440x1000` and
`390x844`; narrow coverage additionally checks whole-page horizontal overflow. Failures capture
HTML, screenshot, and trace artifacts under `app/target/ui-test-results`.

## Safe boundary

Navigation, reload, browser history, and non-persistent presentation controls are allowed. Forms,
Save, archive/reactivate, refresh, import/export, reconciliation, simulation commands, and
integration actions are never submitted.

## References

- `UiPageSmokeIT`
- `HappyInvestorReadOnlyUiIT`
- [`exploratory-read-only.md`](../../ai/missions/exploratory-read-only.md)
- [`Long-Term Assets smoke`](long-term-assets.md)
