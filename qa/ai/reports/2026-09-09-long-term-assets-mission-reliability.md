# AI QA mission reliability: HappyInvestor Long-Term Assets

- Runs: `3`
- Target: same deployed develop URL and portfolio path on every run
- Browser execution: unavailable on all runs; repeated authenticated HTTP GET fallback only
- Mission under test: [`long-term-assets-happyinvestor-read-only.md`](../missions/long-term-assets-happyinvestor-read-only.md)

## Consistency assessment

The environment/data-scope finding was fully consistent across the three bounded probes. Every run
returned HTTP 200, the same title, the same 28,545-byte response, and the same SHA-256:
  `1000967aedc4b966b0c42f99e62b0dc06d1c0d959d3b1fd8b56b97aa5abb1222`. Detailed probe evidence
  was not retained in the repository.

This is not a browser-agent reliability result. No live browser/Playwright MCP was available, so
navigation, interaction, console, failed-request, screenshot, and responsive checks were not run.
The repeated result proves stable server-rendered mismatch detection only.

## Findings across runs

### Consistently detected

- HTTP 200 and `Long-term assets · Investory` title.
- Wrong data scope for the mission: header showed `6.42M` all-assets value and `203,356` net
  income/year instead of the canonical HappyInvestor context.
- Five real-estate holdings and unrelated asset names appeared instead of the expected canonical
  Apartment A/B story.
- Allocation response showed real estate `56.6% · 3.64M`, bonds `12.4% · 800.0K`, cash reserve
  `1.2% · 75.0K`, and personal assets `29.7% · 1.91M`.
- No persistent data changed; only GET requests were issued.

### Inconsistent findings

None among the three HTTP probes. Browser-agent navigation variance could not be measured.

### False positives

None established. The mismatch is a valid mission precondition failure, but it is not evidence of
an application defect because the deployed portfolio may intentionally be a different live profile.

### Unsupported assumptions exposed

- The mission previously assumed portfolio `1` in develop was the HappyInvestor fixture.
- It allowed the agent to choose viewport and detail-page coverage, making runs incomparable.
- It treated discovered optional sorting/filtering as a possible required check without defining an
  explicit N/A result.
- It did not separate third-party network noise from relevant first-party failures.

### Missed checks

All browser-level checks were missed: navigation, detail-page behavior, safe controls, totals and
fact comparison in rendered content, labels/formatting, loading/empty/error states, visual layout,
console, failed browser requests, and reload/back/forward behavior.

### Screenshots and noise

No screenshot was taken. This was appropriate: the browser never reached a state where a screenshot
could prove a UI finding. The instructions now require screenshots only when materially useful and
allow textual/network evidence for objective failures.

### Agent-navigation differences

Not measurable. The three runs used the same direct GET and did not exercise an agent's navigation
choices. The mission now fixes mandatory checkpoints while leaving route inspection and control
selection exploratory within those bounds.

## Changes made

- Added fixed run metadata and mandatory coverage checkpoints.
- Added a visible HappyInvestor precondition and explicit wrong-fixture blocker.
- Clarified PASS/SUSPICIOUS/FAIL evidence thresholds; subjective and uncertain observations cannot
  become FAIL.
- Added `N/A` handling for absent optional control classes.
- Limited network evidence to relevant first-party failures and reduced screenshot noise.
- Required consistent desktop/narrow viewport settings across repeated runs.

## Recommendation

The mission contract is suitable for another live-browser trial, but the approach is **not yet
reliable enough to expand broadly** based on this run set. First repeat this mission at least three
times with live browser/Playwright MCP and a confirmed HappyInvestor deployment, then expand to other
pages if route, console/network, data-scope, and visual findings remain stable.
