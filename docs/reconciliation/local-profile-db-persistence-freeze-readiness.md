# Database, persistence, and reconciliation freeze readiness

Reviewed on current main `1e6ddde8d79047b4715890d1bacefb133dbdf6da`.

## Decision

**NOT_READY_TO_FREEZE**

The clean Flyway/Hibernate/golden path is healthy, but production accounting and projection code still performs financial arithmetic through legacy `Double` entity accessors. This conflicts with the required BigDecimal-only financial-calculation boundary.

## Double API audit

Persisted monetary fields in `Position`, `CashOperation`, `AccountDaily`, `Asset`, `AccountStatistics`, and `CurrencyRate` are `BigDecimal` with `numeric(20,8)` mappings. Legacy `Double` accessors remain for compatibility.

`TaxCalculator` now uses `Position`'s `BigDecimal` value accessors. Production financial calculation/comparison uses still remain in `PortfolioProjectionService`, `PortfolioService`, `CashOperationNormalizer`, `IbkrPositionReconstructionService`, `AssetPriceFallbackService`, and `MarketService`. These include position value/profit/commission/swap, cash amount, and current-price arithmetic. Dashboard/export/notification compatibility uses also remain, but the calculation/import uses are the freeze blocker.

Future accounting/domain code must use the matching `*Value()` `BigDecimal` accessor (or add one where an immutable reporting entity has none). This review does not remove compatibility APIs.

## As-of contract

`reporting_account_statistics_vs_daily_reconciliation` is in `V01.005__portfolio_views.sql`, not V01.018 on current main. `CURRENT_DATE <> latest_daily.snapshot_date` is retained intentionally: this is a **current valuation reconciliation**, not a latest-completed-market-session reconciliation. Friday viewed Saturday, a holiday, pre-close, or a refresh lag are explicit `VALUATION_ASOF_DIFFERENCE` review states, not monetary mismatches. `DatabaseReportingContractIT.statisticsReconciliationLabelsDifferentAsOfDatesExplicitly` covers this behavior.

## Baseline migration strictness

Current baseline ends at **V01.008**: nine versioned migrations. Historical migrations were not edited.

| File | Statement | Classification | Reason |
| --- | --- | --- | --- |
| `V01.000__schema.sql` | `CREATE SCHEMA IF NOT EXISTS investory` | B | Flyway/test setup may create the schema first; it does not hide table or column mismatch. |
| `V01.003__initial_data.sql` | explicit-target `ON CONFLICT ... DO UPDATE` seed upserts | A | Deterministic anonymized reference/sample seed behavior. |
| `V01.004__asset_price_history_import.sql` | `ON CONFLICT (asset_id, price_date, source) DO NOTHING` | B | Idempotent static history seed keyed by natural source identity; applied Flyway migrations do not rerun. |

No reviewed baseline statement silently accepts incompatible table shape.

## Persistence and provenance

`GoldenRebuildIT` starts empty PostgreSQL, runs Flyway, and boots Spring with `spring.jpa.hibernate.ddl-auto=validate`; it passed. This validates the current entity/schema mapping, including enums and `numeric(20,8)` fields. Baseline checks confirm importer-owned raw IDs for `positions` and `cash_operations` and reject cascade deletion for `accounts`, `cash_operations`, and `positions`. `DatabaseReportingContractIT` now supplies explicit raw-fact IDs in its direct SQL fixtures and deletes fixture rows in foreign-key order. Legacy/manual provenance remains nullable by contract.

## Fresh database and golden rebuild

- Empty PostgreSQL Flyway migration: **PASS**, nine migrations through `V01.008`; `spring.flyway.out-of-order` is `false`.
- Hibernate validation on the fresh golden database: **PASS**.
- Golden rebuild: **READY**; independent reconciliation error scan passed.
- `BaselineReadinessContractIT`: **PASS** on a fresh container; its seed expectations match `V01.003`.
- `DatabaseReportingContractIT`: **PASS** on a fresh container (10 tests).

The grouped remaining Testcontainers contract command exceeded the local 15-minute timeout during migration-checkpoint work and remains incomplete. Its child processes were terminated before individual reruns to avoid stale build-output locks. A clean individual `MaterializedViewRefreshContractIT` run then exposed a separate contract failure: it expects `v_portfolio_performance_daily`, but the clean V01.008 schema does not contain that relation. Resolve the test/schema contract before freeze; do not weaken it without identifying the intended reporting view.

## Known debt blocking freeze

1. Migrate remaining production financial calculations/comparisons from legacy entity `Double` accessors to `BigDecimal` values.
2. Resolve the missing `v_portfolio_performance_daily` contract exposed by `MaterializedViewRefreshContractIT`, then complete the remaining clean-container contracts: `MaterializedViewRefreshContractIT`, `ValuationInputContractIT`, `SystemAuditContractIT`, `AccountMonthlyBenchmarkContractIT`, and `SchemaMigrationCheckpoint2IT`.
3. Only then declare the baseline through the then-current Flyway version frozen and require append-only migrations.
