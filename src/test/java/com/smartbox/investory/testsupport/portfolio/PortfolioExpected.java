package com.smartbox.investory.testsupport.portfolio;

public final class PortfolioExpected {

  private PortfolioExpected() {}

  public record CashBalance(double startingCash, double endingCash, double externalCashFlow) {}

  public record Position(
      double quantity,
      double marketPrice,
      double marketValue,
      double costBasis,
      double unrealizedProfit) {}

  public record Dividend(
      double grossIncome, double tax, double netCashIncrease, double externalCashFlow) {}

  public record Valuation(double cash, double marketValue, double portfolioValue) {}

  public record Transfer(double amountOut, double amountIn, double fee, double externalCashFlow) {}

  public record MultiCurrencyValue(
      double localAmount,
      double fxRateToUsd,
      double convertedUsdAmount,
      double unrealizedFxImpact) {}

  public record DuplicateImport(String checksum, long existingBatchId, boolean duplicate) {}
}
