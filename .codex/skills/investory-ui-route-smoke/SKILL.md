---
name: investory-ui-route-smoke
description: Run a fast, read-only Investory UI route smoke check covering every known page and reporting rendering, HTTP, browser, and environment failures.
metadata:
  short-description: Fast Investory route smoke test
---

# Investory UI route smoke

Use this skill for a quick regression pass over every rendered Investory page. For deployed AI-QA
checks, use Playwright MCP and the canonical `qa/ui/smoke/` scenario; Java smoke tests are supporting
evidence, not a substitute for a real browser run.

## Workflow

1. Inspect `git status --short` and preserve unrelated changes.
2. Read the current route cases in `app/src/test/java/com/smartbox/investory/ui/UiPageSmokeIT.java` and current test configuration. Do not invent routes when the application or test manifest can provide them.
3. Use the disposable `test-fast` database and fixture data for Java tests. For a deployed check,
confirm the operator-supplied environment, portfolio, authentication, and fixture before assertions;
wrong or missing HappyInvestor data is BLOCKED, not an application failure. Never write to live data.
4. Start the application and browser once per run. Preflight the Playwright browser driver before starting the test.
5. Visit every route with authentication supplied by the test profile or operator. Use a normal
20-second timeout; allow up to 120 seconds only for documented slow routes.
6. For each route assert response status, title, visible body/main, semantic page content, absence of
application error text, browser page errors, console errors, and failed first-party requests. Treat
the manifest's `headingText` as a check, not as unquestionable truth: if the page renders the same
semantic section under a renamed heading, classify the mismatch as a test defect and record both
strings. Expected error routes (for example a deliberate 404) may produce matching console noise;
do not classify that expected response as an application failure.
7. Save screenshot, HTML, trace, URL, and request/console evidence only for failures.
8. Classify results as product bug, test defect, build defect, or environment blocker. A JDBC/API check is not a UI pass.

## Speed and reliability

- Prefer `DOMContentLoaded` plus a stable `data-ui-ready` marker over `networkidle`.
- Reuse the browser and authenticated context where isolation permits.
- Retry browser/container startup once for infrastructure errors only. Do not retry assertions.
- Install or validate Playwright browsers before the run; report driver lock/permission errors separately.
- If a clean reactor build still fails before the browser starts with `ClassNotFoundException`,
  `NoClassDefFoundError`, or missing class files, report a build/classpath blocker separately from
  route results; do not call it a UI failure.

## Report

Return counts for routes passed/failed/blocked, a table of each failure with evidence path and root-cause category, the exact command, and whether live data was changed.
