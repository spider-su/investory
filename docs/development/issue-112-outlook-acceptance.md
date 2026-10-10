# Issue 112 — isolated Outlook acceptance

## Source facts verified 2026-10-10

No article text, forecast inference or live import seed is included.

| Institution | Named strategist | Published | Target date | Price-index target | Original primary source |
| --- | --- | --- | --- | --- | --- |
| Goldman Sachs Research | Ben Snider | 2026-05-28 | 2026-12-31 | 8,000 | [Institutional article](https://www.goldmansachs.com/insights/articles/s-and-p-500-forecast-to-climb-as-earnings-growth-powers-stocks-higher) |
| J.P. Morgan Global Research | Dubravko Lakos-Bujas | 2026-07-01 | 2026-12-31 | 7,800 | [Mid-year outlook](https://cws-main-ndc.jpmorgan.com/insights/global-research/outlook/mid-year-outlook) |

| Morgan Stanley | Mike Wilson | 2026-09-21 | 2026-12-31 | 8,000 | [Institutional podcast transcript](https://www.morganstanley.com/insights/podcasts/thoughts-on-the-market/bull-market-resilience-isnt-complacency-mike-wilson) |

These pages explicitly state year-end 2026 numeric S&P 500 targets and the named strategists.
Publication time is not established: encode their date at 00:00 UTC conservatively. Earlier targets
mentioned in the articles are not separate observations because their publication dates are unknown.
Public readability does not establish automated retrieval or republication rights.

## Disposable current-date QA, not historical evidence

`JdbcStrategistForecastStoreIT` imports those facts through the controlled importer into a scoped
Testcontainers PostgreSQL database, renders the actual Thymeleaf page, and drops that database.
The fixture capture is fixed on the actual verification date, **2026-10-10**, not backdated.
Goldman is stale; Morgan Stanley and J.P. Morgan are current. This isolated QA dataset is labeled
in the HTML artifact and must never be copied into a real historical dataset. No normal development or production database is touched.

Independent arithmetic for the October 10 QA snapshot:

- distinct institutions = 2; each contributes once;
- median target = (8,000 + 7,800) / 2 = **7,900 index points**;
- min/max = **7,800 / 8,000**; target spread = **200 index points**;
- publication ages: Morgan Stanley 19 days, J.P. Morgan 101 days, Goldman 135 days;
  Goldman is excluded by the 120-day rule; stale exclusions = 1;
- no baseline/current/publication/VIX closes are supplied: YTD, full-year return, remaining return,
  publication implied move, pp dispersion and VIX metrics must be unavailable, never calculated
  from SPY, total return, or invented quotes;
- no earlier capture history exists in the replay: 30-day revision is unavailable;
- no earlier capture is eligible; a September snapshot contains no observations and no consensus.

Synthetic populated-plot verification is separate: four explicitly synthetic targets 5,000, 5,200,
5,400, 5,600; baseline 5,000; current quote 5,500 dated July 1; publication before July 1 close uses
June 30 quote 5,000. Independent expected values: YTD 10%; median full-year 6%; median remaining
(5,300 / 5,500 − 1) × 100 = −3.63636%; dispersion 12 pp; target IQR 5,150–5,450.
These numbers are test inputs, not real forecasts or provider history.

## Evidence boundaries

The browser test `MarketRadarOutlookUiIT` uses isolated rendered application HTML and checked-in
assets, never an external page or authenticated production session. It records desktop/mobile PNGs
and page errors under `app/target/ui-test-results`. This proves rendering, attribution and plot
presentation, not deployment, provider completeness/licensing or a comprehensive institutional consensus.
Controller/API tests separately verify target-date/as-of routing. Normal Radar signal calculations
and jobs are unchanged. The repository's headless browser-test screenshots were inspected for separate plot scales,
labels and missing-data presentation. Supplementary native Chrome accessibility inspection and
a rolled-back DEV-schema check are recorded in [the operator acceptance report](market-radar-outlook-manual-acceptance.md).

Local Docker recovery: the prescribed loopback TCP endpoint was unavailable; an ephemeral loopback-only relay initially enabled migration/store checks, but Docker snapshot
streaming stalled. Final checks use the verified Colima socket and disposable Flyway databases.
The relay is stopped; no deployment configuration changes. Native Chromium startup is denied by
the macOS sandbox (Mach-port registration). A disposable Linux browser can be connected with
`-Doutlook.browser.ws=ws://127.0.0.1:3112/`; this is test-only, not a deployed service.
