# Market Radar

Experimental deterministic market-activity radar hosted inside Investory while the concept is validated.

Stage 1 consumes daily OHLCV observations through `HistoricalMarketDataPort`, derives transparent
momentum/volume/trend metrics, and classifies each symbol as NORMAL, EMERGING, TRENDING, HOT,
EXTENDED, or COOLING. It does not make trade decisions.

The module owns its domain and ports. Provider adapters live outside it. It must not depend on
Investment persistence, REST/MVC controllers, JPA, provider SDKs, or presentation helpers. This is
intentional so the module can later be extracted into a separate application.
