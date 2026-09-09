# Codex AI UI QA instructions

## Mission contract

AI UI QA is exploratory browser QA against a named deployed environment. It complements, but does
not replace, Java Playwright/Failsafe tests. Read the mission, `AGENTS.md`, the relevant quality
plan, and the referenced HappyInvestor facts before using the browser.

The default mode is read-only:

- Navigate, reload, resize, use back/forward, change URL query parameters, expand read-only sections,
  and use controls that are documented as non-mutating.
- Do not submit forms or invoke create, edit, delete, archive, reactivate, import, export, refresh,
  integration, scheduler, reconciliation, or simulation-write actions.
- Do not send mutation requests directly through the browser console, fetch/XHR, or an API client.
- Do not change persistent data, settings, provider state, files, or credentials. Stop and report if
  the mission would require a mutation to continue.
- Authentication may be performed with credentials supplied by the operator, but never put secrets,
  cookies, tokens, or personal portfolio data in a report.

## Reproducible exploration loop

1. Record the exact environment URL, UTC/local start time, browser and fixed viewport, authenticated
   user/profile scope, mission revision, and starting route. Do not guess an environment or portfolio
   ID. Repeat runs should use the same values.
2. Check the mission preconditions before exploring: authentication succeeded, the expected profile
   identity is visible, and the fixture/data scope matches the mission. If a precondition fails, stop
   and classify it as an environment/data-scope blocker; do not reinterpret another portfolio as a
   failed application assertion.
3. Visit the mission's mandatory route checkpoints in a stable order, then allow exploratory safe
   links and controls within those checkpoints. The checkpoints define coverage, not selectors or a
   click script. Check HTTP status, title, visible shell/heading, URL state, layout, and obvious
   loading/error/empty states.
4. Follow safe links and non-mutating controls. Prefer rendered content and accessible labels over
   implementation assumptions. Use canonical facts only for explicitly scoped values; do not derive
   expected values from the page, REST response, or production view model.
5. Install browser listeners before the first navigation and keep them for the mission. Record page
   errors and relevant console errors; record failed or unexpected first-party requests with method,
   URL, status, and a short sanitized message. Ignore ordinary third-party noise unless it visibly
   breaks the page or is itself in scope.
6. Capture a screenshot only when it materially proves a visual defect, missing/error state, or
   content mismatch. Do not capture screenshots for every successful checkpoint. Capture HTML,
   trace, or network details only when useful and safe.
7. Reload the primary checkpoint and repeat the meaningful observation. Use back/forward or one safe
   control per discovered control class, recording controls that do not exist as `N/A`; do not force
   sorting/filtering or treat absent optional controls as failures. Stop on a destructive control,
   unexpected mutation request, authentication boundary, environment failure, or unsafe data.

## Result rules

Use exactly one result:

- **PASS**: all mandatory checkpoints and applicable checks completed; no unexplained application
  console/page errors or failed first-party requests; observations and evidence are recorded.
- **SUSPICIOUS**: exploration completed or mostly completed, but an observation needs human review
  or a precondition/environment issue prevents completion. Record the uncertainty and next check.
- **FAIL**: a mission assertion or safety rule failed. FAIL requires concrete evidence: route and
  timestamp, expected versus observed behavior, why it is incorrect, and sanitized page/console/
  network evidence. Add a screenshot or trace when useful, but do not require noisy artifacts for a
  clear textual/network failure. Subjective visual concern, transient uncertainty, absent optional
  controls, and agent navigation differences are SUSPICIOUS or N/A, never FAIL by themselves. An
  unavailable browser, deployment, authentication setup, or wrong fixture is an environment/data
  blocker and must not be reported as an application FAIL.

Report skipped routes, blocked steps, and unexecuted checks separately. Never turn missing evidence
into PASS.
