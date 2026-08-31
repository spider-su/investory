---
name: investory-ui-route-smoke
description: Run a fast, read-only Investory UI route smoke check covering every known page and reporting rendering, HTTP, browser, and environment failures.
metadata:
  short-description: Fast Investory route smoke test
---

# Investory UI route smoke

Use this skill for a quick regression pass over every rendered Investory page.

## Workflow

1. Inspect `git status --short` and preserve unrelated changes.
2. Read the current route cases in `app/src/test/java/com/smartbox/investory/ui/UiPageSmokeIT.java` and current test configuration. Do not invent routes when the application or test manifest can provide them.
3. Use the disposable `test-fast` database and fixture data. Never write to the user's live database for a smoke check.
4. Start the application and browser once per run. Preflight the Playwright browser driver before starting the test.
5. Visit every route with authentication supplied by the test profile. Use a normal 20-second timeout; allow up to 120 seconds only for documented slow routes.
6. For each route assert response status, title, visible body/main, stable heading, absence of application error text, browser page errors, console errors, and failed first-party requests.
7. Save screenshot, HTML, trace, URL, and request/console evidence only for failures.
8. Classify results as product bug, test defect, build defect, or environment blocker. A JDBC/API check is not a UI pass.

## Speed and reliability

- Prefer `DOMContentLoaded` plus a stable `data-ui-ready` marker over `networkidle`.
- Reuse the browser and authenticated context where isolation permits.
- Retry browser/container startup once for infrastructure errors only. Do not retry assertions.
- Install or validate Playwright browsers before the run; report driver lock/permission errors separately.

## Report

Return counts for routes passed/failed/blocked, a table of each failure with evidence path and root-cause category, the exact command, and whether live data was changed.
