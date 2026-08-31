# Import performance analysis

Analyze import/projection performance from supplied logs and current code.

This mode is read-only.

Goal: identify the small number of database/caching/batching issues responsible for
most observed runtime.

## Analyze

Where evidence is available, measure or count:

- total observed runtime
- repeated SQL statements
- repeated resolver/function calls
- full-table reloads
- N+1 entity loads
- per-row inserts/upserts
- duplicate reads of stable reference data
- expensive work inside position/date loops

Pay particular attention to:

- FX resolution
- market-price lookup/history
- asset-price gap fill
- account/asset loading
- broker-import persistence
- projection recalculation

Distinguish:

- correctness blockers
- high-impact performance problems
- secondary optimizations

Do not recommend caching merely because data is read often.

For each cache candidate state:

- cache key
- cached value
- authority/source of truth
- invalidation trigger
- whether failures should be cached
- expected reduction in DB calls

Prefer keeping canonical financial semantics in their current authority. Do not
duplicate complex PostgreSQL FX/valuation rules in Java merely for speed.

Prefer:

- resolved-result caching
- projection/request scoped memoization
- bulk loads/maps
- JDBC batching
- set-based SQL

over hidden semantic changes.

## Output

# Import Performance

## Baseline

Report concrete observed counts/timings when available.

## Hotspots

| Priority | Hotspot | Evidence | Proposed change |
|---|---|---|---|

Use `P0`, `P1`, `P2`, `P3`.

`P0` is reserved for correctness or a performance problem that blocks normal use.

## Cache Candidates

For each useful cache:

- key
- value
- invalidation
- authority
- expected benefit

## DB-call Reduction

Estimate expected before/after call shape where evidence permits, for example:

`number of conversions -> number of unique (date, currency pair) keys`

Do not invent precise speedups without measurements.

## Recommendation

Give the smallest next optimization that provides the largest justified benefit.

Do not implement it.
