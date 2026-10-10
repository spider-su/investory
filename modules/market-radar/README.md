# Market Radar

Market Radar is an experimental market-intelligence module hosted inside Investory while the concept
is validated. Its job is not to manage portfolio accounting and it does not execute trades.

Its primary question is:

> What is changing in the market, why did it surface, and is the signal useful enough to investigate?

## Current POC

Stage 1 is deterministic. It consumes daily OHLCV observations through
`HistoricalMarketDataPort`, derives transparent market-activity metrics, persists daily snapshots,
evaluates later outcomes, and exposes read-only Radar views.

Current metrics:

- 20-day and 60-day momentum.
- 20-day relative volume.
- distance from SMA50.
- RSI14.

Current states:

- `NORMAL`
- `EMERGING`
- `TRENDING`
- `HOT`
- `EXTENDED`
- `COOLING`

Each signal keeps human-readable reasons. Radar deliberately avoids a synthetic BUY/SELL score.

## Why it exists

Investory answers questions such as what the user owns, how the portfolio performs, and how assets
contribute to wealth. Market Radar answers a different class of question: which market instruments
or themes are behaving unusually and deserve attention.

The POC is intended to validate three assumptions before more complex intelligence is added:

1. deterministic price/volume signals can surface useful research candidates;
2. persisted signal history can be evaluated objectively against later returns;
3. broader information such as fundamentals and media should only be added if they improve measured
   signal quality.

## Architecture

The module owns its domain, application services, public API, and ports. Provider-specific adapters
live outside the module.

```text
Yahoo / future providers
        |
        v
HistoricalMarketDataPort
        |
        v
MarketSignalCalculator
        |
        v
RadarSnapshot
        |
        +----> persistence/history
        |
        +----> MarketRadarApi ----> Web UI
        |
        v
MarketRadarEvaluator
        |
        v
RadarOutcome
        |
        v
Validation statistics
```

Dependency rules:

- Market Radar must not depend on Investment persistence.
- Market Radar must not know about Yahoo, JDBC, Thymeleaf, or provider SDKs.
- Web UI consumes the public `MarketRadarApi`.
- integrations implement market-data, persistence, and scheduling adapters.
- existing portfolio/accounting calculations must not depend on Radar.
- the boundary should remain extractable into a separate service/project.

## Persistence

A daily `RadarSnapshot` is treated as a frozen observation of what Radar knew at detection time.
Future results do not rewrite the snapshot.

Forward outcomes are stored separately for 7, 30, and 90-day horizons. They record:

- symbol return;
- benchmark return;
- benchmark-relative excess return;
- actual evaluation trading date.

This separation prevents hindsight from changing the original signal.

### Strategist forecast observations (curated MVP)

S&P 500 strategist targets are a separate, informational dataset; they do not enter Radar signals,
portfolio performance, or recommendations. Forecasts are manually source-curated and imported as a
JSON array with `POST /api/v1/admin/market-radar/strategist-forecasts` (the admin API path is
restricted by application security). Each row requires an institution key/name, publication
timestamp, explicit target date, positive numeric target, HTTP(S) source URL/title, and source type
(`PRIMARY` or `SECONDARY`); strategist is optional. The service validates the entire request before
writing, rejects future publication/impossible or over-ten-year horizons, and records capture time.
An existing URL/institution/target-date/publication-timestamp identity is an idempotent no-op. A different publication
for the same institution and target date is retained as a new observation, with its predecessor
linked where available. A previously registered URL retains its source type. Conflicting titles are rejected for the same publication; a later publication may carry a revised title.

Only source-backed numeric price-index targets belong here. Secondary reporting must use
`SECONDARY`; it is stored with secondary quality, not presented as a primary institutional
statement. No scraping, source-text copying, invented test/live values, or scheduled retrieval is
used. The admin-controlled maintenance cadence is monthly or on a material published revision.
The additive schema is `V01.053__market_radar_strategist_forecasts.sql`; rollback is to disable use
of the endpoint and, only if no observations need preservation, remove these three new tables in
reverse foreign-key order. Existing Radar tables and jobs are untouched.

The read-only `/api/v1/market-radar/strategist-outlook?targetDate=YYYY-MM-DD&asOf=YYYY-MM-DD`
contract calculates one exact target horizon using observations whose publication and capture
timestamps are both no later than `asOf`. Each institution contributes its latest eligible
observation, ordered by publication timestamp, capture timestamp, then observation ID. Observations
older than 120 calendar days are excluded from current consensus and counted as stale. Calendar
YTD is the latest available as-of price-index close divided by the prior calendar year final
trading close minus one; forecasted full-year return uses target divided by the previous year-end
close; remaining return uses target divided by the latest available close. Dispersion is max minus
min of full-year returns in percentage points; quartiles are withheld below four institutions.
Historical revision compares available consensus at `asOf - 30 days`; absent history remains null.
Matured forecast errors use the target-date close, or the immediately following available trading
close when the target is a non-trading day; records are not scored until that close is available.
Accuracy is aggregate only and warns below five scored observations.

The `/market-radar` Outlook panel accepts an exact target date and an as-of date. It labels actual
calendar YTD, forecasted full-year return, remaining implied return, percentage-point spread and
eligible institution count separately. The page includes source-linked institution rows, publication
history, matured outcome errors and explicit missing-data states; a missing value is never replaced
with SPY or a fabricated estimate. Median/range use one latest eligible observation per institution
for that exact horizon. IQR is shown only with at least four institutions. The 30-day median revision
is shown only when historical as-of coverage exists. Historical selection requires publication AND
capture timestamps to be no later than the snapshot date.

The current provider path does not establish licensed/complete `^GSPC` or `^VIX` series. The API
reports missing benchmark history explicitly and does not substitute the existing SPY benchmark.
VIX is shown in its own risk-context panel, separate from strategist dispersion; it is defined there
as an option-implied estimate of annualized S&P 500 volatility over approximately 30 days, not a
directional forecast. The provider is queried for `^VIX` dated closes independently from `^GSPC`. Latest level,
5/20-session percentage changes and a three-month dot plot are shown only with available history.
No values are seeded or scraped. A one-year empirical percentile (fraction of valid closes less
than or equal to the latest close, including ties) requires at least 200 valid observations. Runtime history depends on the existing
configured provider's availability and terms. No legal permission or automated access is asserted;
do not scrape Cboe delayed quotes. Missing/stale quote history is an honest unavailable state, not a
feed-failure claim for weekends or holidays. VIX cannot affect consensus, Radar signals, or portfolio
performance. Percentage outputs are percentages unless named `dispersionPercentagePoints` or
`revisionPercentagePoints`; dispersion and revision are percentage points, not percent changes.

Forecast outcomes use a price-index close on the target date, or the immediately following
regular US equity trading-date close when target date is a non-trading day. Missing scheduled
closes are not replaced with a later quote. Unscheduled exchange closures remain unavailable. Year-end reference baselines use the last
available close on/before Dec 31. Forecast errors are only scored after the selected outcome close
exists. The provider currently does not establish all required benchmark history, so the UI may show
partial forecast/source data while benchmark-derived returns, outcomes and VIX are unavailable.

The validation view aggregates outcomes by Radar state and horizon and currently reports:

- observation count and a simple sample-size indication;
- average and median return;
- average and median benchmark-relative excess return;
- positive-return rate;
- benchmark-outperformance rate.

The scheduler emits one refresh summary after each run with the universe size, attempted/stored/no-data/
failed counts, interesting-state count, state distribution, number of forward outcomes evaluated, and
total duration. Individual symbol failures remain isolated and logged separately.

## UI

The current read-only UI consists of:

- `/market-radar` — current signals, with `NORMAL` hidden by default;
- `/market-radar/{symbol}` — ticker history and forward outcomes;
- `/market-radar/validation` — aggregate effectiveness and drill-down observations;
- `/market-radar/operations` — persisted run history plus an admin-only verification refresh action;
- `/market-radar/themes` — theme proxy strength and member breadth;
- `/market-radar/media` — media attention acceleration, source diversity, and explicit analyst stance.

The main `/market-radar` page also includes a separate source-curated S&P 500 Outlook and VIX context
panel. Add or revise targets through the documented admin JSON import; never edit an earlier
observation to make a revision. Monthly or material-revision curation remains manual. Rollback is to
stop import/use and preserve the additive forecast tables unless their history is deliberately
retired; existing Radar scans and provider refresh jobs require no change.

The main screen is intentionally a "what changed?" surface rather than another full market
dashboard.

## Scheduling and configuration

Radar is disabled by default.

Relevant environment variables:

- `MARKET_RADAR_ENABLED` — enable the scheduled scan.
- `MARKET_RADAR_SYMBOLS` — optional comma-separated universe override.
- `MARKET_RADAR_CRON` — refresh schedule.
- `MARKET_RADAR_BENCHMARK` — forward-evaluation benchmark, default `SPY`.
- `MARKET_RADAR_BATCH_SIZE` — symbols per provider batch, default 25.
- `MARKET_RADAR_BATCH_PAUSE_MS` — pause between batches, default 5000 ms.
- `MARKET_RADAR_THEME_CRON` — theme breadth refresh, default 22:50 Warsaw time.
- `MARKET_RADAR_MEDIA_ENABLED` — enable media collection separately from price scanning.
- `MARKET_RADAR_MEDIA_CRON` — media refresh, default 23:10 Warsaw time.
- `MARKET_RADAR_MEDIA_SYMBOLS` — comma-separated media watch universe.

## Default scan universe

When `MARKET_RADAR_SYMBOLS` is empty, the integration layer scans a curated liquid universe of
broad-market and thematic ETFs plus large-cap US/global listings. The bundled universe is intended
for POC signal collection rather than index replication.

The nightly scan is processed in configurable batches so a large universe does not create one
unpaced burst of Yahoo requests. A provider failure for one symbol is isolated and logged; the
remaining symbols continue.

## Non-goals for the current POC

The current module does not:

- execute or place trades;
- generate personalized BUY/SELL recommendations;
- optimize thresholds based on future data;
- infer sentiment from unstructured headlines or commentary;
- use an LLM to create market evidence;
- maintain a full security master;
- replicate a professional market-data platform;
- make existing Investory portfolio logic depend on Radar.

## Themes and media

Stage 3 calculates breadth from curated theme definitions. A theme combines a liquid proxy ETF with
constituent/member symbols and records percentage above SMA50, percentage outperforming SPY, proxy
20-day return, and benchmark-relative strength. Theme states are `ACCELERATING`, `STRONG`,
`NEUTRAL`, and `WEAKENING`.

Stage 4 stores external observations independently from market signals. Media attention is measured as
7-day mention count versus the previous 7 days plus independent-source count. The first source adapter
uses Yahoo Finance RSS and is disabled by default. RSS items are stored with stance `UNKNOWN`; Radar
does not infer bullish/bearish intent from headlines. Structured analyst sources can submit explicit
stance through the admin-only `/api/v1/admin/market-radar/analyst-observations` endpoint.

See [ROADMAP.md](ROADMAP.md) for planned stages and exit criteria.


## Deployment verification

Use [../../docs/development/market-radar-deploy-verification.md](../../docs/development/market-radar-deploy-verification.md)
for the production enablement sequence. The recommended rollout starts disabled, enables a five-symbol
verification universe, performs an admin-triggered refresh, validates persisted run/snapshot evidence,
and only then expands to the bundled universe.

## Outlook integration conventions and validation

`V01.054` adds publication timestamp to source-observation uniqueness, preserving revisions on
reused URLs. Import batches are transactional in the JDBC adapter; contradictory targets for the
same institution/publication/horizon are rejected before writes. Provenance mismatches and altered
idempotency payloads are rejected rather than overwriting history.

Publication reference uses `publishedAt` in America/New_York: before 16:00 local use the preceding
completed regular session; at/after 16:00 use that session if available. This deliberately conservative
cutoff does not assume early-close availability. Date-only sources must be encoded at 00:00 UTC;
this excludes that UTC day's close. The reference level, quote date and descriptive implied move
are displayed together. Missing reference close yields no implied move. Never infer intraday prices.

Year-end baselines require the final regular US equity session on/before Dec 31, not an arbitrary
older quote. That session must precede the selected as-of date to exclude unfinished daily bars.
Return-based historical snapshots and revisions likewise wait until the baseline session has completed. Outcomes require the exact first regular session on/after target date, strictly before the selected
as-of date so an unfinished same-day bar cannot be scored. The calendar
covers weekends, New Year (Saturday exception), MLK Day, Presidents Day, Good Friday, Memorial Day,
Juneteenth from 2022, Independence Day, Labor Day, Thanksgiving and Christmas with observed days.
Unscheduled closures are not inferred. This regular-calendar limitation fails closed when a required
quote is missing. YTD/remaining metrics require a close from at least the regular session preceding
the selected as-of date (which may be intraday), or a returned close on that date. Older closes retain
their provider date and stale notice but cannot supply current metrics. The requested history includes
the earliest eligible publication-reference session, including long-horizon forecasts. Actual YTD uses the displayed quote's calendar year, including a prior-year close at New Year.
Other horizons show their target date and comparison quote date, with quote-year YTD labeled as context.
All series are price-index closes, never adjusted/total-return series or SPY substitutes.

Consensus needs at least two distinct eligible institutions; IQR needs four. Individual rows remain
visible below the consensus threshold. Same-horizon dated dots and min/max whiskers retain observed
snapshot dates (publication AND capture). Dispersion has its own pp scale adjacent to the independent
VIX index-point plot. Dots never interpolate absent history. VIX changes require the exact 5th/20th
preceding regular session; sparse history produces unavailable changes. Quotes older than the regular session preceding the selected as-of date are marked stale;
weekends and scheduled holidays do not themselves imply a feed failure. Feed failures produce empty states
without breaking technical Radar rendering. Provider symbols and redistribution terms still need
operator verification before production use; support for arbitrary symbols is not a licensing claim.

File-to-test ownership for this extension:

| Surface | Verification |
| --- | --- |
| Import validation/identity | `StrategistForecastImportServiceTest` |
| As-of statistics, publication reference, VIX and scoring | `StrategistOutlookServiceTest`, `OutlookTradingDatesTest` |
| JDBC history and disposable source acceptance | `JdbcStrategistForecastStoreIT` (app CI reconciliation leg) |
| Additive V01.053/V01.054 chain | `FlywayMigrationChainIT` (migration CI job) |
| Read-only API | `StrategistOutlookControllerTest` |
| Controller and rendered populated/empty plots | `MarketRadarOutlookPageTest`, `MarketRadarOutlookTemplateTest` |

Existing schema inventory remains V01.040 outcomes (`JdbcRadarOutcomeStore`/evaluator), V01.043
themes (`JdbcThemeSnapshotStore`/theme scheduler), V01.044 media (`JdbcMediaObservationStore`/media
scheduler), V01.045 runs (`JdbcRadarRunStore`/Radar scheduler), V01.047 snapshots
(`JdbcRadarSnapshotStore`/scanner), and V01.052 snapshot repair. No existing jobs or tables change.
Local `.devcontainer/compose.yml` is not deployment evidence: packaged behavior is governed by
`Dockerfile`, `application.yml` and the [deployment verification guide](../../docs/development/market-radar-deploy-verification.md).

Source-backed manual QA evidence and replay caveats live in
[issue-112-outlook-acceptance.md](../../docs/development/issue-112-outlook-acceptance.md).
Rollback remains stop import/rendering while retaining additive history; do not undo applied
Flyway migrations or replace them with the test snapshot. Monthly source checks preserve URLs,
titles and named strategists, marking secondary reporting explicitly. Public access does not grant
scraping or content reuse rights. No news collector or paid provider is introduced.

Institution keys retain immutable display attribution: conflicting names are rejected transactionally, including on idempotent import retries.

Observation titles are immutable per publication. Later publications may revise the title at the same URL; duplicate publications with conflicting titles are rejected. V01.055 backfills legacy titles additively from their existing source metadata.
