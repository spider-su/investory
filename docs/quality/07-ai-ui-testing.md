# AI-driven UI testing

## Purpose

Investory supports bounded exploratory UI QA by a Codex agent with browser/Playwright MCP access.
The agent uses the deployed develop environment and existing browser tooling; the repository adds
mission, safety, and evidence conventions but no custom agent framework and no CI job.

The working files are [`qa/ai/instructions.md`](../../qa/ai/instructions.md), the mission templates
under [`qa/ai/missions/`](../../qa/ai/missions/), and report/evidence guidance under
[`qa/ai/reports/`](../../qa/ai/reports/).

The first concrete mission is [`HappyInvestor Long-Term Assets`](../../qa/ai/missions/long-term-assets-happyinvestor-read-only.md);
its bounded initial report is [`2026-09-09 report`](../../qa/ai/reports/2026-09-09-long-term-assets-happyinvestor.md).

## Contract

### Financial source-of-truth rule

AI UI tests validate the canonical HappyInvestor story and existing domain/reporting contracts.
They must not redefine financial metric semantics locally. Expected values are canonical story facts
or values derived from tested reporting contracts. External deposits and withdrawals remain subject
to the existing reporting/domain contract. For a mismatch, identify the story fact, locate the
authoritative contract, reconcile the observation, and only then classify it as a product defect.
Deterministic fixture facts, derived reporting values, and live/non-deterministic market observations
such as Yahoo prices and FX must be reported separately.

AI QA is read-only by default. Navigation, reload, back/forward, viewport changes, read-only query
filters, and expansion of read-only sections are allowed. Form submissions and actions that create,
edit, delete, archive, reactivate, import, export, refresh, reconcile, schedule, or change provider
state are forbidden. The agent must not issue mutation requests through a console, API, fetch/XHR,
or browser-storage operation. If a mission needs a write, stop and ask for a separately authorized
test plan.

Every mission ends with exactly one of:

- **PASS**: all scoped checks completed with no unexplained application errors.
- **SUSPICIOUS**: exploration completed or mostly completed, but an observation needs human review.
- **FAIL**: an assertion or safety rule failed, backed by concrete evidence.

FAIL evidence includes the route and time, expected versus observed behavior, sanitized console and
network details, and a screenshot or trace when useful. Browser, deployment, and authentication
problems are recorded separately as environment blockers. Skipped and unexecuted checks stay
explicit.

## Execution through the browser

1. Read `AGENTS.md`, the mission, the relevant quality plan, and the referenced canonical
   `HappyInvestor*Facts` documentation/classes. Use existing UI tests as examples, especially
   `UiPageSmokeIT` for route/content/console/network checks and `HappyInvestorReadOnlyUiIT` for
   deterministic read-only financial assertions and artifact capture.
2. Confirm the exact deployed base URL, profile/user scope, viewport, browser, and authentication
   supplied by the operator. Never guess these values or put secrets in evidence.
3. Attach Playwright listeners before navigation for page errors, console errors/warnings, failed
   requests, and relevant response status/method/URL data. Visit only mission routes and exercise
   allowed actions.
4. Inspect visible content, URL/state, accessibility labels, geometry, reload stability, and
   responsive behavior. Compare financial values only with independent canonical facts; do not
   treat a REST response or rendered page as its own expected-value source.
5. Capture screenshots, traces, or sanitized HTML/network excerpts when they materially help prove
   a finding. Store or link them using the conventions in `qa/ai/reports/`.
6. Write one report with PASS, SUSPICIOUS, or FAIL, plus console/network results and all blocked,
   skipped, and unexecuted checks. Do not claim an application pass when the browser never reached
   the page.

## Relationship to executable tests

The canonical Java Playwright/Failsafe suite remains under `app/src/test`, with artifacts under
`app/target/ui-test-results`. Its snapshot-backed read-only contracts use the shared HappyInvestor
story and independent facts. AI QA should reuse those routes, selectors, source contracts, and
manual QA plans, but exploratory findings belong in a report until a deterministic regression is
promoted into an executable test.

See [`docs/development/testing.md`](../development/testing.md),
[`docs/quality/01-investment-dashboard-manual-qa.md`](01-investment-dashboard-manual-qa.md), and
[`test-support` HappyInvestor documentation](../../test-support/src/main/java/com/smartbox/investory/testsupport/happyinvestor/README.md).
