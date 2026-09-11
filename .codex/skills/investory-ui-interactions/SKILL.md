---
name: investory-ui-interactions
description: Verify active Investory UI controls produce expected URL, visible-state, validation, and navigation results using focused browser regression checks.
metadata:
  short-description: Investory UI interaction regression
---

# Investory UI interaction regression

Use this skill after route smoke or when the user asks whether buttons, links, forms, tabs, selectors,
or expanders work. For deployed exploratory work, use the real browser through Playwright MCP and
follow the relevant `qa/ui/active/` and `qa/ui/facts/` scenario.

## Workflow

1. Inspect the current UI tests, templates, controllers, JavaScript, and the selected `qa/ui/` scenario. Preserve unrelated dirty files.
2. Use disposable `test-fast` fixtures and authenticated browser contexts. Do not submit mutations against a live database unless explicitly requested.
3. Define each check as: locate element, assert visible/enabled, perform one action, wait for the expected state change, assert the result, and capture browser errors.
4. Prefer stable `data-testid` or semantic role selectors. Add stable selectors only when implementation work is requested; otherwise report selector fragility as a test-quality issue.
5. Assert both observable outcomes where relevant:
   - URL/query parameter and selected/active state;
   - visible panel, table, validation message, or returned page;
   - expected network response status for asynchronous actions.
   Keep exact domain facts separate from intentionally rounded form/table presentation values.
6. Use a 20-second default action/navigation timeout and a 120-second timeout only for known slow calculations. Wait for a specific response or DOM marker instead of arbitrary sleeps.
7. Run independent read-only interaction checks in parallel only when browser and database isolation is safe. Keep CRUD and stateful flows sequential.

## Coverage model

Cover representative controls on every page class: shared navigation, period selectors, tabs, expand/collapse sections, Add/Cancel links, form validation, table/detail links, and slow simulation controls. Record untested control classes instead of claiming all active elements passed.

For read-only Long-Term Assets coverage, inspect collapsed and expanded summaries, every available
GET detail/edit representation, safe exit paths, tooltips/dialogs, responsive layout, console/page
errors, relevant first-party requests, and mutation safety. Treat absent control classes as N/A.

## Failure handling

Do not retry assertion failures. Retry only browser startup, container startup, or transient connection failures once. Save a failure screenshot, HTML, trace, URL, console log, and request log.

## Report

For every failure state the page, control, action, expected outcome, actual outcome, evidence path, and category: product bug, test defect, build defect, or environment blocker.
