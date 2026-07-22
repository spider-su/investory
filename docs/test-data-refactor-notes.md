# Portfolio Test-Data Refactor Notes

Generated: 2026-07-27

## Existing Fixture Approaches

- A centralized package already exists at
  `src/test/java/com/example/demo/testsupport/portfolio`.
- Many service tests still used local helper methods such as `closed(...)`,
  `opened(...)`, `cash(...)`, and inline `builder()` calls.
- API adapter tests used inline account-daily rows, cash operations, assets, and
  open-position rows.
- Yahoo export tests repeated open-position setup for each symbol/lot case.
- Manual CSV fixtures under `src/test/manual` are still separate smoke/manual
  data and were not replaced.

## Duplication Found

- Repeated account ids: `51499241`, `50290466`, `17959259`.
- Repeated symbols: `AAPL.US`, `MSFT.US`, `NVDA.US`, `VWRA.UK`, `VWRA`.
- Repeated dates created with `ZonedDateTime.now()` or inline `ZonedDateTime.of(...)`.
- Repeated open-position setup: id, account, symbol, currency, volume, price,
  market price.
- Repeated account-statistics setup for cash export tests.
- Repeated Ghostfolio account-daily and cash-operation construction.

## Centralized Structure

- `PortfolioTestData`: canonical dates, account definitions, asset definitions,
  deterministic prices, and deterministic FX rates.
- `PortfolioBuilders`: low-level builders for accounts, assets, cash operations,
  open positions, closed positions, FX rates, account statistics, account daily
  rows, and import history.
- `PortfolioScenarios`: focused domain scenarios for empty, funded, long
  position, multiple lots, partial sale, dividend, internal transfer,
  multi-currency, and duplicate import cases.
- `PortfolioTestContext`: named references for accounts, assets, operations,
  positions, FX rates, imports, and expected values.
- `PortfolioExpected`: independently calculated expected-value records.
- `PortfolioAssertions`: reusable accounting invariant assertions.
- `PortfolioTestPersistence`: repository persistence facade for scenario-backed
  integration tests.

## Tests Migrated First

- `CashFlowAggregatorTest`: already uses dividend and transfer scenarios.
- `GhostfolioCompatibilityServiceTest`: migrated account-daily, asset,
  open-position, and cash-operation fixtures to shared builders and canonical
  dates.
- `PortfolioServiceTest`: migrated local open/closed/cash helper construction to
  shared builders and deterministic dates where production behavior allows it.
- `YahooExportServiceTest`: migrated repeated open-position and account-stat
  fixtures to shared builders.
- `PortfolioScenariosTest`: covers deterministic scenario behavior and
  invariants.

## Domain Notes

- Production entities use `double` values, so tests preserve the existing
  epsilon-based assertions instead of introducing a new money type.
- Current production Yahoo export uses the first day of the current month as the
  generated trade date. Tests still derive that expected value from the same
  clock convention because changing it would alter production behavior.
- Capital-gains tax logic depends on the current calendar year. The migrated
  test keeps the current year but uses a fixed month/day and canonical timezone.
- Existing `Account` and `Asset` schemas do not expose account type, contract
  multiplier, strike, expiration, option type, face value, coupon, maturity, or
  quantity precision. Canonical definitions only include fields present in the
  production entities.

## Remaining Work

- Repository/integration tests can adopt `PortfolioTestPersistence` as they are
  added or touched.
- Import parser tests still have broker-specific fixtures and should only move
  shared portfolio concepts into `PortfolioTestData`; raw broker files should
  remain realistic samples.
- Benchmark and projection tests should get dedicated scenario methods when they
  next need fixture changes.
