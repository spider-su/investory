# Portfolio Test Data

Use this package for deterministic portfolio fixtures instead of redefining account IDs, symbols,
dates, prices, FX rates, and common operations inside individual tests.

## Structure

- `PortfolioTestData` contains immutable canonical dates, account definitions, asset definitions,
  deterministic prices, and deterministic FX rates.
- `PortfolioBuilders` creates fresh production entities with valid defaults and explicit overrides.
- `PortfolioBuilders.accountDaily()` creates deterministic historical account rows for history and
  performance tests.
- `PortfolioScenarios` composes builders into small domain scenarios such as funded portfolio,
  long position, dividend, internal transfer, multi-currency, and duplicate import.
- `PortfolioTestContext` returns named references to generated accounts, assets, operations,
  positions, FX rates, imports, and expected values.
- `PortfolioExpected` holds independently calculated expected values. Do not build expected values by
  calling the production service under test.
- `PortfolioAssertions` contains reusable accounting invariant checks.
- `PortfolioTestPersistence` persists a scenario into repositories for integration/repository tests.

## Adding a Scenario

1. Add canonical constants or definitions to `PortfolioTestData` only if they are reusable.
2. Prefer composing existing builders in `PortfolioScenarios`.
3. Return a `PortfolioTestContext` with named references, not unstructured arrays.
4. Include expected values that are explicit and independently calculated.
5. Add or extend `PortfolioScenariosTest` to prove determinism, isolation, and important invariants.

Keep scenarios minimal. Use the complete/reference scenario pattern only for reporting,
reconciliation, end-to-end, or regression tests.

## Example

```java
PortfolioTestContext context = PortfolioScenarios.createDividendScenario();

assertEquals(
    context.expected().dividend().netCashIncrease(),
    context.operations().aaplDividend().getAmount() + context.operations().aaplWithholdingTax().getAmount(),
    0.01);
```
