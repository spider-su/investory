# Market Radar Outlook manual acceptance — 2026-10-10

## What was exercised

An operator used the actual controlled import service, JDBC forecast store,
Outlook calculation service, and Thymeleaf page renderer in the issue #112
workspace. Four source-backed observations were imported inside an uncommitted
transaction in the task-owned `issue112_forecast_repair` DEV schema on
`192.168.1.60`. The rendered HTML was opened in Chrome locally and its displayed
values, dates, source links, quality labels, and missing-data states were read.
This was a local static render of the real service output, not a deployed-app
or full application-server acceptance run. Screenshot capture was unavailable;
browser accessibility content was inspected instead.

## Source facts checked

| Institution | Publication | Horizon | Target | Source quality and original/reference link |
| --- | --- | --- | --- | --- |
| Morgan Stanley / Mike Wilson | 2026-09-21 | year-end 2026 | 8,000 | PRIMARY: [institution's podcast transcript](https://www.morganstanley.com/insights/podcasts/thoughts-on-the-market/bull-market-resilience-isnt-complacency-mike-wilson) |
| UBS / Chief Investment Office | 2026-08-21 | year-end 2026 | 8,100 | SECONDARY: [Newsquawk report, published 08:57 UTC](https://www.newsquawk.com/headlines/ubs-lifts-its-year-end-2026-sp-500-target-to-8100-prev-7900-and-2027-target-to-8400-prev-8200). A UBS primary release was not located; the observation was explicitly imported and displayed as SECONDARY. |
| Goldman Sachs / Ben Snider | 2026-05-28 | year-end 2026 | 8,000 | PRIMARY: [institution's article](https://www.goldmansachs.com/insights/articles/s-and-p-500-forecast-to-climb-as-earnings-growth-powers-stocks-higher) |
| UBS / Chief Investment Office | 2026-07-15 | June 2027 | 8,200 | PRIMARY: [institution's daily update](https://www.ubs.com/global/en/wealthmanagement/insights/chief-investment-office/house-view/daily/2026/latest-15072026.html) |

The two year-end primary articles state year-end horizons. Month-end target
dates use December 31 / June 30 normalization. Unknown publication times were
normalized conservatively to UTC midnight rather than represented as verified
intraday timestamps. Capture time was the actual operator verification time,
2026-10-10T18:44:56Z; captures were not backdated. The manually curated sample
is not a claim about comprehensive or current market consensus.

## Independent expected values and observed page

For selected target `2026-12-31`, as-of `2026-10-10`:

- Morgan Stanley and the secondary UBS observation are the two eligible
  institutions. Goldman Sachs is excluded as stale; the June-2027 UBS forecast
  is excluded from this horizon.
- Independent calculation: `(8000 + 8100) / 2 = 8050` index points; min/max
  `8000 / 8100`; target-level range `100` index points.
- The browser page showed eligible institutions **2**, stale exclusions **1**,
  median target **8050.0**, min/max **8000.0 / 8100.0**, the actual publication
  dates and source links, and an explicit **SECONDARY** label on the UBS report.
- The same-horizon consensus plot was present. Only the known capture/as-of
  snapshot was supplied; historical snapshots were not fabricated.
- This isolated manual harness deliberately supplied no market bars. Actual
  YTD, remaining return, publication reference closes, percentage-point
  dispersion, and VIX values were visibly **unavailable / insufficient**.
  No price, return, or volatility value was invented to fill the page.
- Percentage versus percentage-point calculations and publication-session
  close selection require the separate deterministic service/render tests;
  this manual exercise does not prove a live provider connection or quote
  completeness. Live VIX/provider acceptance and remote CI remain separate.

## Cleanup and local evidence

The transaction was rolled back. Forecast observation count was **0 before and
0 after** the exercise. The schema was retained for the worker's remaining
checks; there was no write to another schema or to production. Institution and
source inserts were part of the same rolled-back transaction.

Local artifacts are retained outside Git under
`~/.investory-orchestrator/recovery/issue112-manual-acceptance/`:
`index.html`, `facts.txt`, and `rollback.txt`. No credentials are included in
this document or the artifacts. The temporary HTTP preview listens only on
localhost. These observations are supplementary operator evidence; independent
review and CI must still approve the final candidate.

A supplementary read-only request to the configured Yahoo chart endpoint for
`^GSPC` returned HTTP **429** during this check. It did not yield verified live
index closes. The harness therefore retained its explicitly missing-market-data
case; it did not invent live prices, replace the provider, or turn that external
availability failure into a claim of successful live-provider acceptance.
