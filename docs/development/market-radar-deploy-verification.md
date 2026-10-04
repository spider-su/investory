# Market Radar deployment verification

Use this checklist when enabling Market Radar in a deployed Investory environment.

## 1. Deploy with Radar disabled

Deploy the image first with:

- `SPRING_PROFILES_ACTIVE=prod`
- `DEVELOP_MODE=false`
- valid `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- non-default application security secrets
- `SCHEDULING_ENABLED=true`
- `MARKET_RADAR_ENABLED=false`
- `MARKET_RADAR_MEDIA_ENABLED=false`

Verify:

1. container starts successfully;
2. Flyway applies all pending migrations, including `market_radar_run`;
3. `/actuator/health` returns healthy;
4. authenticated `/market-radar` and `/market-radar/operations` load.

## 2. Enable a small verification universe

For the first live scan, use a small explicit universe:

```text
MARKET_RADAR_ENABLED=true
MARKET_RADAR_SYMBOLS=SPY,QQQ,AAPL,MSFT,NVDA
MARKET_RADAR_BATCH_SIZE=5
MARKET_RADAR_BATCH_PAUSE_MS=1000
```

Keep media disabled during this first verification.

Use `MARKET_RADAR_CRON` to schedule a near-term verification run in the target environment, then
restore the normal weekday schedule after verification.

## 3. Verify the run

After the scheduled run:

1. open `/market-radar/operations`;
2. confirm the latest run is `SUCCESS` or an understood `PARTIAL`;
3. verify attempted/stored/no-data/failed counts;
4. confirm duration is reasonable;
5. open `/market-radar` and verify stored observations;
6. confirm no unexpected provider/auth/database errors in application logs.

A `FAILED` run must be investigated before expanding the universe.

## 4. Expand to the normal universe

Remove `MARKET_RADAR_SYMBOLS` to use the bundled universe and restore the normal batch/pause
configuration. Keep the classifier thresholds unchanged while evidence is collected.

## 5. Enable optional media separately

Only after deterministic Radar runs are stable:

- enable `MARKET_RADAR_MEDIA_ENABLED=true`;
- verify its provider access independently;
- confirm media failures do not affect deterministic Radar operation.

## Cloud Run notes

- Cloud Run may terminate idle instances. Scheduled work therefore requires an instance to be
  available at the scheduled time; do not assume an in-process Spring scheduler is a durable cloud
  scheduler.
- For reliable Cloud Run execution, keep at least one instance available or invoke a dedicated
  authenticated refresh path from Cloud Scheduler in a future deployment iteration.
- Treat `/actuator/health` as process health only. The Operations page is the evidence that Radar
  actually executed.
