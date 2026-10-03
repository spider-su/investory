# Market Radar Roadmap

This roadmap is evidence-driven. A later stage should not be implemented merely because the data is
available; it should address a limitation observed in the previous stage.

## Guiding principle

Market Radar is a research-attention system, not an automated adviser.

The progression should remain:

```text
market activity
    -> persisted signals
    -> measured outcomes
    -> fundamentals
    -> analyst/media intelligence
    -> cross-signal research briefs
    -> optional extraction from Investory
```

Each stage should preserve explainability and avoid replacing observable evidence with a magic score.

---

## Stage 1 — Deterministic market activity

**Status: implemented / collecting evidence**

Goal: identify instruments whose market behaviour deserves investigation.

Capabilities:

- daily OHLCV ingestion;
- 20d / 60d momentum;
- relative volume;
- SMA50 distance;
- RSI14;
- states: NORMAL, EMERGING, TRENDING, HOT, EXTENDED, COOLING;
- persisted daily snapshots;
- configurable curated universe;
- batch/rate-safe nightly scanning;
- per-symbol failure isolation;
- read-only Radar UI.

Success criterion:

- scheduled scans run reliably across the configured universe;
- the UI consistently reduces the universe to a manageable number of interesting observations.

Do not tune thresholds aggressively during this stage. First collect comparable history.

---

## Stage 1.5 — Validation and signal quality

**Status: implemented / evidence accumulating**

Goal: determine whether Radar states contain useful forward information.

Capabilities:

- immutable signal snapshots;
- 7/30/90-day forward outcomes;
- benchmark-relative returns;
- aggregate statistics by state and horizon;
- drill-down into observations.

Metrics to monitor:

- sample size;
- average and median return;
- average and median excess return;
- positive-return rate;
- benchmark-outperformance rate;
- dispersion/outliers.

Next improvements once enough data exists:

- median return and median excess return;
- max adverse/favourable excursion;
- confidence/sample-size indication;
- separation by market regime or sector if sample size permits.

Exit criterion:

- enough observations exist to judge whether at least some states outperform a simple baseline.
- if no state adds useful information, revise or simplify Stage 1 before adding more data sources.

---

## Stage 2 — Fundamental confirmation

**Status: planned**

Goal: distinguish market activity supported by business improvement from price/attention alone.

Candidate sources:

- SEC EDGAR/XBRL for US issuers;
- a replaceable fundamentals provider for non-US listings;
- analyst-estimate/revision source only if licensing and API stability are acceptable.

Candidate signals:

- revenue/EPS growth;
- margin trend;
- free cash flow;
- leverage;
- capex/R&D trend;
- valuation percentile versus own history;
- earnings/revenue estimate revisions.

Expected output:

```text
TRENDING
market confirmation: strong
fundamental confirmation: improving
valuation: normal/high
```

Success criterion:

- fundamental confirmation materially improves validation statistics or reduces false positives.

---

## Stage 3 — Themes and breadth

**Status: planned**

Goal: identify trends larger than a single ticker.

Capabilities:

- sector/theme definitions;
- ETF relative strength;
- breadth inside a theme;
- percentage above SMA50/SMA200;
- percentage outperforming the benchmark;
- theme acceleration/cooling.

Example output:

```text
SEMICONDUCTORS
state: accelerating
breadth: 72%
relative strength: improving
volume participation: elevated
```

Success criterion:

- theme information explains or prioritizes individual ticker signals better than ticker-only data.

---

## Stage 4 — Analyst / media radar

**Status: experimental future stage**

Goal: track changes in external investment theses without treating popularity as a buy signal.

Potential sources:

- selected YouTube transcripts/subtitles;
- podcasts/transcripts;
- newsletters/articles where legally and technically accessible;
- structured analyst consensus data.

Extracted facts:

- ticker;
- bullish / neutral / bearish stance;
- conviction;
- horizon;
- thesis;
- risks;
- first mention;
- opinion change.

Important rules:

- count independent theses, not repeated copies of the same narrative;
- opinion changes are more informative than repeated unchanged recommendations;
- media consensus is a research-candidate signal, never a direct BUY signal.

Success criterion:

- media/analyst changes add measurable value beyond deterministic/fundamental signals.

---

## Stage 5 — AI research synthesis

**Status: future**

Goal: explain correlations between deterministic evidence rather than inventing the evidence.

Possible output:

```text
Why this surfaced:
- relative volume increased to 2.1x;
- 20d relative strength improved;
- earnings estimates moved higher;
- three independent tracked sources turned bullish.

Risk:
- valuation is near the upper end of its historical range.
```

LLM responsibilities:

- summarize;
- compare theses;
- identify agreement/disagreement;
- produce research briefs;
- answer natural-language questions over stored Radar evidence.

LLM non-responsibilities:

- fabricate market facts;
- overwrite deterministic calculations;
- place trades;
- convert weak evidence into a definitive recommendation.

---

## Stage 6 — Investory portfolio relevance

**Status: planned after Radar evidence is useful**

Goal: connect market intelligence with personal exposure without coupling Radar to Investory's
accounting implementation.

Candidate context:

- owned / watchlist / new;
- direct position weight;
- indirect ETF/theme exposure;
- concentration overlap.

Intended boundary:

```text
Market Radar -> PortfolioContextPort -> Investory adapter/API
```

If Radar is extracted later, this becomes an HTTP/plugin boundary without changing Radar's domain.

---

## Stage 7 — Standalone extraction decision

**Status: decision point, not guaranteed**

Extract Market Radar from Investory only if the experiment proves useful and one or more of these
becomes true:

- scan/data workloads materially differ from Investory;
- independent deployment/scheduling is useful;
- additional users/portfolios should consume Radar;
- provider credentials or scaling deserve isolation;
- Radar development cadence starts affecting Investory stability.

Before extraction, ensure:

- Radar depends only on its own domain/API/ports;
- provider adapters can move without Investment internals;
- Investory integration is represented through explicit contracts;
- schema ownership is clear.

The target should be extraction, not rewrite.

---

## Explicitly out of scope

Unless the project direction changes, Radar should not become:

- an autonomous trading bot;
- high-frequency/intraday execution infrastructure;
- a substitute for licensed real-time market data;
- a social-media popularity leaderboard;
- a guaranteed stock-selection engine.

The value proposition is simpler: reduce market noise, surface explainable changes, and measure
whether those changes actually deserve attention.
