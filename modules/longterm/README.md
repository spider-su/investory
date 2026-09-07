# Long-Term module

Long-Term owns real estate, rental contracts, bonds, cash reserves, and personal assets. Canonical
financial semantics live in [`docs/domain/long-term-assets.md`](../../docs/domain/long-term-assets.md).

## Boundaries

- `api`: persistence-free management, Profile, Retirement, and payment-audit contracts.
- `application/service`: command validation, read aggregation, lifecycle, audit, and calculations.
- `infrastructure`: JPA entities and repositories grouped by owned asset subtype.
- `web`: the Long-Term REST adapter; it may use `api` only.

Other business modules may depend only on `api`. Web UI also uses the API boundary and owns its
controllers/templates in `adapters/web-ui`. Long-Term may depend on shared portfolio, currency,
policy, and asset semantics, but not on Investment, Profile, or Retirement implementations.

## Main flows and tests

- Asset create/update: subtype command services and `LongTermCommandValidationTest`.
- Rental lifecycle: `RentalContractService` and its focused service/validation tests.
- Current totals, tax, yield, Profile and projection facts: `LongTermAssetReadService`,
  `LongTermAssetEconomicsTest`, and `LongTermAssetsApplicationServiceTest`.
- Historical calendar reconstruction: `LongTermAssetHistoricalSnapshotService`; it returns
  unavailable values when complete acquisition/archive provenance is absent.
- PostgreSQL real-estate/rental lifecycle: app-owned `LongTermAssetLifecyclePostgresIT`.
- Database conversion/constraints: app-owned `LongTermHardeningMigrationIT`.
- Rendered CRUD and canonical values: app-owned `LongTermAssetCrudUiIT` and
  `HappyInvestorReadOnlyUiIT`.

Use reactor-aware verification from the repository root: `./mvnw -pl modules/longterm -am verify`.
CI also combines the app-owned lifecycle execution data with module tests and enforces the critical
`longterm.application.service` coverage baseline.
