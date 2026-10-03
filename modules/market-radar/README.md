# Market Radar

Experimental deterministic market-activity radar hosted inside Investory while the concept is validated.

Stage 1 consumes daily OHLCV observations through `HistoricalMarketDataPort`, derives transparent
momentum/volume/trend metrics, and classifies each symbol as NORMAL, EMERGING, TRENDING, HOT,
EXTENDED, or COOLING. It does not make trade decisions.

The module owns its domain and ports. Provider adapters live outside it. It must not depend on
Investment persistence, REST/MVC controllers, JPA, provider SDKs, or presentation helpers. This is
intentional so the module can later be extracted into a separate application.

## Default scan universe

When `MARKET_RADAR_SYMBOLS` is empty, the integration layer scans a curated liquid universe of
broad-market and thematic ETFs plus large-cap US/global listings. The bundled universe is intended
for POC signal collection rather than index replication.

The nightly scan is processed in configurable batches so a large universe does not create one
unpaced burst of Yahoo requests:

- `MARKET_RADAR_BATCH_SIZE` defaults to 25.
- `MARKET_RADAR_BATCH_PAUSE_MS` defaults to 5000.
- `MARKET_RADAR_SYMBOLS` can replace the bundled universe for experiments.

A provider failure for one symbol is isolated and logged; the remaining symbols continue.
