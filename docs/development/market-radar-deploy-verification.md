# Market Radar deployment verification

Use this checklist when enabling Market Radar in a deployed Investory environment.

The objective is to prove four separate things:

1. the application and Radar schema deploy safely;
2. Radar can reach its market-data provider;
3. a complete refresh persists operational evidence and market snapshots;
4. the normal scheduled configuration can be enabled without changing classifier thresholds.

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
2. Flyway applies all pending migrations;
3. `/actuator/health`, `/actuator/health/readiness`, and `/actuator/health/liveness`
   are healthy;
4. authenticated `/market-radar`, `/market-radar/operations`, and
   `/market-radar/validation` load;
5. the Operations page explains that refresh is disabled.

Database evidence should include at least:

- `market_radar_snapshot`;
- `market_radar_outcome`;
- `market_radar_run`;
- theme/media tables when their migrations are part of the deployed image.

Do not enable the full universe until this step is clean.

## 2. Enable a small verification universe

Redeploy with:

```text
MARKET_RADAR_ENABLED=true
MARKET_RADAR_SYMBOLS=SPY,QQQ,AAPL,MSFT,NVDA
MARKET_RADAR_BATCH_SIZE=5
MARKET_RADAR_BATCH_PAUSE_MS=1000
MARKET_RADAR_BENCHMARK=SPY
MARKET_RADAR_MEDIA_ENABLED=false
```

Keep the normal Radar classifier thresholds unchanged.

The normal cron may remain configured; deployment verification no longer needs to wait for it.

## 3. Trigger a verification refresh

Log in as an administrator and open:

```text
/market-radar/operations
```

Use **Run verification refresh**.

The action is a synchronous admin-only POST. When it returns, the page redirects back to Operations
and the newest persisted run should be visible.

For automation clients, the same operation is exposed as:

```text
POST /api/v1/admin/market-radar/refresh
```

It uses the normal application admin authentication and CSRF/security policy. Do not weaken production
security merely to make this endpoint easier to call.

## 4. Verify persisted run evidence

The newest Operations row should normally be `SUCCESS`.

`PARTIAL` is acceptable only when every failed/no-data symbol is understood. `FAILED` blocks
rollout.

For the five-symbol verification universe expect:

- `universeSize = 5`;
- `attempted = 5`;
- `stored + noData + failed = 5`;
- `stored > 0`;
- duration greater than zero;
- no unexpected error text.

Useful SQL checks:

```sql
select started_at, completed_at, status, universe_size, attempted, stored, no_data, failed,
       interesting, outcomes_evaluated, duration_ms, error
from market_radar_run
order by started_at desc
limit 5;
```

```sql
select symbol, observed_on, state, close_price, relative_volume_20d, rsi14
from market_radar_snapshot
order by observed_on desc, symbol
limit 25;
```

The snapshot dates should correspond to the latest available market session rather than necessarily
the deployment calendar date.

## 5. Verify UI and logs

Open:

- `/market-radar` — current observations;
- `/market-radar/operations` — execution evidence;
- `/market-radar/validation` — may legitimately have little/no mature data on a fresh deployment;
- `/market-radar/themes` — only if theme collection is enabled/available;
- `/market-radar/media` — keep disabled during the deterministic first pass.

Application logs should contain the completed Radar summary and no unexpected database/provider
exceptions.

A healthy first refresh proves execution and persistence. It does **not** prove that the investment
signals are predictive; that is what forward validation is for.

## 6. Expand to the normal universe

After the small-universe refresh succeeds:

1. remove `MARKET_RADAR_SYMBOLS` to use the bundled universe;
2. restore the normal batch size/pause if they were reduced;
3. run one more manual verification refresh or observe the next scheduled refresh;
4. confirm the larger run remains `SUCCESS` or an understood `PARTIAL`;
5. keep signal thresholds unchanged while evidence accumulates.

The deployed defaults are designed for paced Yahoo access rather than a single request burst.

## 7. Verify scheduled execution

After the manual path is proven, verify the scheduler separately.

Confirm:

- `MARKET_RADAR_CRON` is correct for the intended Warsaw-time schedule;
- `SCHEDULING_ENABLED=true`;
- a new `market_radar_run` row appears without manual interaction;
- the Operations page shows its completion status.

This distinguishes "Radar works" from "Radar scheduling works."

## 8. Enable optional media separately

Only after deterministic Radar is stable:

- set `MARKET_RADAR_MEDIA_ENABLED=true`;
- verify provider access independently;
- confirm media failures do not affect deterministic Radar operation;
- inspect `/market-radar/media` before treating attention metrics as useful evidence.

## Failure / rollback criteria

Do not expand the rollout when any of these occur:

- Flyway fails;
- application readiness is unhealthy;
- the manual refresh is `FAILED`;
- most symbols fail provider access;
- no snapshots persist despite successful Yahoo responses;
- repeated scheduled runs do not appear in `market_radar_run`;
- Radar causes material instability in unrelated Investory workflows.

Rollback is configuration-first:

1. set `MARKET_RADAR_ENABLED=false`;
2. keep the additive Radar tables/migrations in place;
3. redeploy and verify core Investory health.

Do not delete Radar history simply because the scheduler is disabled; retained observations are useful
for later diagnosis and validation.

## Cloud Run notes

- Cloud Run may terminate idle instances. An in-process Spring cron alone is not a durable guarantee
  that a scheduled job will execute when the service has scaled to zero.
- The admin run-now path makes deployment verification deterministic even when cron timing is
  inconvenient.
- For long-term scheduled reliability, either keep an instance available or invoke an authenticated
  run-now path from an external scheduler after the authentication model is explicitly designed for
  machine-to-machine calls.
- Treat `/actuator/health` as process health only. The persisted Operations run is the evidence that
  Radar actually executed.
