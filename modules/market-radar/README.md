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
