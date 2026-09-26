# AI QA report: HappyInvestor Long-Term Assets exploratory read-only QA

- Result: `SUSPICIOUS`
- Environment: deployed Investory develop endpoint
- Target: HappyInvestor portfolio, expected portfolio ID `1`
- Browser / viewport: not executed; live browser/Playwright MCP was unavailable
- Started / finished: 2026-09-09 Europe/Warsaw; bounded GET probe only
- Mission: [`long-term-assets-happyinvestor-read-only.md`](../missions/long-term-assets-happyinvestor-read-only.md)

## Summary

The endpoint was reachable with a read-only authenticated `GET` and returned HTTP 200, but the
rendered portfolio does not match the canonical HappyInvestor fixture. The page showed a different
portfolio state (`6.42M` all-assets value, `203,356` net income/year, five real-estate holdings),
so the mission stopped before browser exploration. This is an environment/data-scope blocker, not
an application FAIL.

## Checks

| Check | Result | Observation / evidence |
| --- | --- | --- |
| Endpoint reachability | PASS | HTTP 200, HTML response, expected Long-term assets title. |
| HappyInvestor portfolio identity/data | SUSPICIOUS / blocked | Expected Apartment A/B, Family Car, Treasury 2026, Term cash reserve, and canonical totals; observed unrelated asset names/counts and totals. |
| Navigation and safe interactions | UNEXECUTED | No browser MCP available. |
| Totals, facts, labels, and formatting | UNEXECUTED | Cannot validate the intended fixture against rendered browser state. |
| Loading/empty/error states | UNEXECUTED | No browser lifecycle or alternate state exploration. |
| Visual/responsive behavior | UNEXECUTED | No screenshot or viewport run. |
| Console and failed network requests | UNEXECUTED | Browser listeners could not be attached. |
| Persistent-data safety | PASS for probe | Only an authenticated `GET` was issued; no mutation request was sent. |

## Console and network

- Page errors: not observable without browser access.
- Console errors/warnings: not observed; browser inspection was unavailable.
- Failed or unexpected first-party requests: not observable from the bounded HTML GET probe.

## Environment blocker

The browser tool could not open the deployed URL: the available web connector rejected the URL as
unsafe to open, and no browser/Playwright MCP tool is exposed in this task. The fallback `curl`
probe reached the page but cannot provide DOM interaction, console, network-event, screenshot, or
responsive-layout evidence.

## Follow-up

Run this mission with a live browser/Playwright MCP against a deploy whose portfolio `1` is the
HappyInvestor fixture. Confirm the target before exploration; do not reinterpret the current live
portfolio as canonical facts. Do not fix application defects as part of this mission.
