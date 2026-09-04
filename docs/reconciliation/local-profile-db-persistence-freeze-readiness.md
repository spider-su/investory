# Database, persistence, and reconciliation freeze readiness

Reviewed on local checkout `5e8a1f3` plus the current working-tree benchmark failure fix.

## Decision

**NOT_READY_TO_FREEZE**

The clean Flyway/Hibernate/golden path is healthy. The named reporting, portfolio aggregation, and XTB import paths now keep financial arithmetic in `BigDecimal`; remaining legacy primitive calculations are lower-P2 migration debt and are listed below.

## Double API audit

Persisted monetary fields in `Position`, `CashOperation`, `AccountDaily`, `Asset`, `AccountStatistics`, and `CurrencyRate` use `BigDecimal` with `numeric(20,8)` mappings. Canonical persistence getters/setters are `BigDecimal`; remaining primitive conversions must be limited to explicit external, chart, or presentation boundaries.

`TaxCalculator`, `PortfolioMetricsService`, `XtbImportService`, and the public `InvestmentPerformanceApi` now use `BigDecimal` for canonical financial calculations and reporting values. Remaining primitive calculations are concentrated in legacy cash normalization/funding, IBKR import parsing, market-price provider adapters, return-solving code, and compatibility dashboard models. Dashboard/export/notification compatibility conversions may remain at their presentation or integration boundaries.

Benchmark failure behavior is explicit: a database failure while loading persisted portfolio data or SPY closes is logged and propagated as a data-access failure. It must not be converted into an empty or unavailable benchmark, because that would hide an operational failure as valid missing history.

Future accounting/domain code must keep arithmetic in `BigDecimal` and convert only at an explicit presentation or external-library boundary.

## As-of contract

`recon_v_account_statistics_vs_daily` is in `V01.006__reconciliation_views.sql`, not the current migration tail. `CURRENT_DATE <> latest_daily.snapshot_date` is retained intentionally: this is a **current valuation reconciliation**, not a latest-completed-market-session reconciliation. Friday viewed Saturday, a holiday, pre-close, or a refresh lag are explicit `VALUATION_ASOF_DIFFERENCE` review states, not monetary mismatches. `DatabaseReportingContractIT.statisticsReconciliationLabelsDifferentAsOfDatesExplicitly` covers this behavior.

## Migration strictness

The clean baseline chain ends at **V01.008**: nine versioned migrations. Earlier post-baseline patch work was squashed into the original schema/function/view migrations; existing database compatibility is intentionally not supported.

| File | Statement | Classification | Reason |
| --- | --- | --- | --- |
| `V01.000__schema.sql` | `CREATE SCHEMA IF NOT EXISTS investory` | B | Flyway/test setup may create the schema first; it does not hide table or column mismatch. |
| `V01.003__initial_data.sql` | explicit-target `ON CONFLICT ... DO UPDATE` seed upserts | A | Deterministic anonymized reference/sample seed behavior. |
| `V01.004__asset_price_history_import.sql` | `ON CONFLICT (asset_id, price_date, source) DO NOTHING` | B | Idempotent static history seed keyed by natural source identity; applied Flyway migrations do not rerun. |

No reviewed baseline statement silently accepts incompatible table shape.

## Persistence and provenance

`GoldenRebuildIT` starts empty PostgreSQL, runs Flyway, and boots Spring with `spring.jpa.hibernate.ddl-auto=validate`; it passed. This validates the current entity/schema mapping, including enums and `numeric(20,8)` fields. Baseline checks confirm importer-owned raw IDs for `positions` and `cash_operations` and reject cascade deletion for `accounts`, `cash_operations`, and `positions`. `DatabaseReportingContractIT` now supplies explicit raw-fact IDs in its direct SQL fixtures and deletes fixture rows in foreign-key order. Legacy/manual provenance remains nullable by contract.

## Fresh database and golden rebuild

- Empty PostgreSQL Flyway migration: **PASS**, migrations through `V01.008`; `spring.flyway.out-of-order` is `false`.
- Hibernate validation on the fresh golden database: **PASS**.
- Golden rebuild: **READY**; independent reconciliation error scan passed.
- `BaselineReadinessContractIT`: **PASS** on a fresh container; its seed expectations match `V01.003`.
- `DatabaseReportingContractIT`: **PASS** on a fresh container (10 tests).

The grouped Testcontainers contract command was previously interrupted during migration-checkpoint work. The migration checkpoint, materialized-view, valuation-input, system-audit, and benchmark contracts have since passed in focused runs; repeat the complete clean-container batch before freeze.

## Known debt blocking freeze

1. Migrate the remaining legacy primitive financial calculations/comparisons in cash normalization/funding, IBKR import parsing, market-price adapters, return solving, and compatibility dashboard models to `BigDecimal` where they are canonical calculations.
2. Repeat the complete clean-container contract batch, including `MaterializedViewRefreshContractIT`, `ValuationInputContractIT`, `SystemAuditContractIT`, `AccountMonthlyBenchmarkContractIT`, and the migration-chain, schema-structure, and migration-data-repair contracts.
3. Only then declare the current Flyway chain frozen and require append-only migrations.
