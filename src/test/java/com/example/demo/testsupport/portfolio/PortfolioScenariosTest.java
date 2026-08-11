package com.example.demo.testsupport.portfolio;

import static com.example.demo.testsupport.portfolio.PortfolioAssertions.expectInternalTransferToBeNeutral;
import static com.example.demo.testsupport.portfolio.PortfolioAssertions.expectPortfolioValueToReconcile;
import static com.example.demo.testsupport.portfolio.PortfolioAssertions.expectPositionToReconcile;
import static com.example.demo.testsupport.portfolio.PortfolioTestData.AAPL_FIRST_BUY_DATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class PortfolioScenariosTest {

  @Test
  void longPositionScenarioCreatesDocumentedDeterministicExpectedValues() {
    PortfolioTestContext context = PortfolioScenarios.createLongPositionScenario();

    assertEquals("AAPL.US", context.assets().aapl().getSymbol());
    assertEquals(100.0, context.expected().position().quantity());
    assertEquals(19_000.0, context.expected().position().marketValue());
    assertEquals(18_000.0, context.expected().position().costBasis());
    assertEquals(1_000.0, context.expected().position().unrealizedProfit());
    assertEquals(81_995.0, context.expected().cash().endingCash());
    expectPortfolioValueToReconcile(
        context.expected().valuation().cash(),
        context.expected().valuation().marketValue(),
        context.expected().valuation().portfolioValue());
  }

  @Test
  void scenariosReturnFreshMutableEntitiesPerCall() {
    PortfolioTestContext first = PortfolioScenarios.createFundedPortfolio();
    PortfolioTestContext second = PortfolioScenarios.createFundedPortfolio();

    assertEquals(first.accounts().ibkrUsd().getId(), second.accounts().ibkrUsd().getId());
    assertNotSame(first.accounts().ibkrUsd(), second.accounts().ibkrUsd());
    assertNotSame(first.operations().initialUsdDeposit(), second.operations().initialUsdDeposit());
  }

  @Test
  void timestampsUseStableTestZoneAndDates() {
    PortfolioTestContext context = PortfolioScenarios.createLongPositionScenario();

    assertEquals(AAPL_FIRST_BUY_DATE, context.positions().aaplOpen().getOpenTime().toLocalDate());
    assertEquals(
        ZoneId.of("Europe/Warsaw"), context.positions().aaplOpen().getOpenTime().getZone());
  }

  @Test
  void multipleLotsScenarioPreservesExpectedQuantityAndCostBasis() {
    PortfolioTestContext context = PortfolioScenarios.createMultipleLotsScenario();

    assertEquals(150.0, context.expected().position().quantity());
    assertEquals(28_000.0, context.expected().position().costBasis());
    expectPositionToReconcile(0.0, context.positions().open(), 150.0);
  }

  @Test
  void dividendScenarioKeepsDividendAndTaxSeparateAndNonExternal() {
    PortfolioTestContext context = PortfolioScenarios.createDividendScenario();

    assertEquals(75.0, context.expected().dividend().grossIncome());
    assertEquals(11.25, context.expected().dividend().tax());
    assertEquals(63.75, context.expected().dividend().netCashIncrease());
    assertEquals(0.0, context.expected().dividend().externalCashFlow());
  }

  @Test
  void internalTransferScenarioLinksBothSidesAndIsPortfolioNeutral() {
    PortfolioTestContext context = PortfolioScenarios.createInternalCashTransferScenario();

    expectInternalTransferToBeNeutral(
        context.operations().transferOut(), context.operations().transferIn());
    assertEquals(0.0, context.expected().transfer().externalCashFlow());
  }

  @Test
  void duplicateImportScenarioUsesMatchingDeterministicChecksums() {
    PortfolioTestContext context = PortfolioScenarios.createDuplicateImportScenario();

    assertEquals(
        context.imports().firstImport().getFileSha256(),
        context.imports().duplicateImport().getFileSha256());
    assertEquals(
        context.expected().duplicateImport().checksum(),
        context.imports().firstImport().getFileSha256());
    assertTrue(context.expected().duplicateImport().duplicate());
  }

  @Test
  void multiCurrencyScenarioUsesExplicitDeterministicFx() {
    PortfolioTestContext context = PortfolioScenarios.createMultiCurrencyScenario();

    assertEquals(1.10, context.fxRates().eurUsd().getRate());
    assertEquals(27_500.0, context.expected().multiCurrency().convertedUsdAmount(), 0.000001);
  }
}
