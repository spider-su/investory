# Test ownership map

This document records where tests and test helpers belong in the current modular monolith.

## Ownership matrix

| Scope | Owner module/path | What belongs here | What does not |
| --- | --- | --- | --- |
| Feature logic | `modules/<feature>/src/test/java` | Unit tests, service tests, feature REST controller tests, feature integration tests that do not require app-only wiring | UI template/browser coverage, cross-module composition checks |
| Application composition | `app/src/test/java/com/smartbox/investory/app`, `app/src/test/java/com/smartbox/investory/config`, `app/src/test/java/com/smartbox/investory/architecture`, `app/src/test/java/com/smartbox/investory/ui`, `app/src/test/java/com/smartbox/investory/contract` | Spring wiring, security config, template rendering, browser smoke/golden tests, database reporting contracts, architecture boundaries | Feature-local unit tests that can live in module tests |
| External adapters | `integrations/src/test/java/com/smartbox/investory/integrations` | Provider/plugin tests and adapter contracts (market/fx/export/notifications/telegram/ai) | Feature accounting logic tests |
| Shared test infrastructure | `test-support/src/main/java/com/smartbox/investory/testsupport` | `FastDatabase*`, deterministic fixture builders/scenarios, reusable DB worker infrastructure | App wiring tests, feature-specific one-off helper logic |

## Current exceptions that stay in `app`

These tests remain app-hosted because they rely on app composition concerns or current fixture/dependency constraints:

- `app/src/test/java/com/smartbox/investory/investment/imports/ImportHistoryOrchestratorServiceTest.java`
- `app/src/test/java/com/smartbox/investory/investment/imports/xtb/XtbImportIT.java`
- `app/src/test/java/com/smartbox/investory/investment/imports/ibkr/IbkrTreasuryImportIT.java`
- `app/src/test/java/com/smartbox/investory/investment/valuation/fx/CurrencyRateUpdaterPostgresIT.java`
- `app/src/test/java/com/smartbox/investory/investment/imports/BrokerImportAccountingBoundaryIT.java`
- `app/src/test/java/com/smartbox/investory/investment/projection/AccountDailyProjectionBoundaryIT.java`
- `app/src/test/java/com/smartbox/investory/investment/valuation/price/AssetPriceFallbackServiceTest.java`
- `app/src/test/java/com/smartbox/investory/investment/valuation/price/ManualAssetPriceServiceTest.java`
- `app/src/test/java/com/smartbox/investory/longterm/application/service/LongTermAssetLifecyclePersistenceIT.java`
- `app/src/test/java/com/smartbox/investory/longterm/application/service/RentalContractPersistenceIT.java`
- `app/src/test/java/com/smartbox/investory/investment/asset/AssetDetailReadModelIT.java` (cross-module app context, owned by investment REST)
- `app/src/test/java/com/smartbox/investory/profile/InvestmentProfileCompositionIT.java` (cross-module profile composition)
- `app/src/test/java/com/smartbox/investory/retirement/planning/PlanningTimelineLifecycleIT.java` (persisted planning lifecycle)
- `app/src/test/java/com/smartbox/investory/integrations/IntegrationJobExecutionIT.java` (persisted integration scheduler)

## Completed consistency moves

- Contract IT package normalized from `com.it` to `com.smartbox.investory.contract`.
- Investment REST-controller test packages aligned to `com.smartbox.investory.investment.web`.
- Long-term Thymeleaf render smoke test moved to `app/src/test/java/com/smartbox/investory/ui/longterm` to match UI ownership.
- `YahooExportServiceTest` moved to `integrations/src/test/java/com/smartbox/investory/integrations/export/yahoo`.

## Next move batches

1. Move additional adapter-owned tests from `app` to `integrations` when dependencies are already available.
2. Keep app-hosted feature tests only where module-test dependency cycles still exist.
3. Revisit `test-support` dependencies before moving app-hosted investment import tests into `modules/investment`.

