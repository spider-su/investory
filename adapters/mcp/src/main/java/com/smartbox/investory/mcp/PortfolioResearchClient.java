package com.smartbox.investory.mcp;

import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Application query port. Adapters never depend on controllers or persistence. */
public interface PortfolioResearchClient {
  ProfileSummary profile(long portfolioId);

  HoldingsSummary holdings(long portfolioId, int limit);

  GeneralProfile generalProfile(long portfolioId);

  LongTermSummary longTermAssets(long portfolioId, int limit);

  RetirementSimulation retirementSimulation(long portfolioId);

  PeriodResult performance(long portfolioId, DashboardPeriod period);

  BenchmarkResult benchmark(long portfolioId, DashboardPeriod period);

  AssetSummary asset(long portfolioId, String symbol, DashboardPeriod period);

  CashFlows cashFlows(long portfolioId);

  Freshness freshness(long portfolioId);

  RetirementSummary retirement(long portfolioId);

  Documentation search(long portfolioId, String query, int limit);

  MarketQuote quote(long portfolioId, String symbol);

  MarketQuoteStatus quoteStatus(long portfolioId, String symbol);

  Capabilities capabilities(long portfolioId);

  record GeneralProfile(
      String currency,
      String netWorth,
      String investmentValue,
      String longTermValue,
      String liquidAssets,
      String illiquidAssets,
      String currentRentalIncome,
      String currentBondIncome,
      String combinedNetAnnualIncome,
      String longTermAnnualIncome,
      String marketNetAnnualIncome,
      String retirementReserve,
      String investmentCapital,
      Instant computedAt,
      String source) {}

  record LongTermAsset(
      String name,
      String type,
      String currency,
      String value,
      String maturityDate,
      String annualRate,
      String monthlyRent,
      boolean integrityWarning) {}

  record LongTermSummary(
      String currency,
      String totalValue,
      String investmentValue,
      String personalAssetValue,
      String netAnnualIncomeAfterTax,
      String monthlyNetIncomeAfterTax,
      List<LongTermAsset> assets,
      Instant computedAt,
      String source) {}

  record SimulationScenarioSummary(
      String scenario,
      boolean failed,
      Integer failureAge,
      Integer firstFailureYear,
      String finalNetWorth,
      String finalLiquidAssets,
      String finalIlliquidAssets,
      String minimumLiquidAssets,
      String lifetimeRequiredPortfolioFunding,
      String lifetimeActualWithdrawals,
      String totalUnfundedAmount,
      String firstYearPassiveIncomeCoverage) {}

  record RetirementSimulation(
      boolean available,
      String plan,
      String currency,
      Integer currentAge,
      Integer retirementAge,
      Integer endAge,
      String annualLivingExpenses,
      String annualDiscretionaryExpenses,
      String inflationRate,
      String equityReturnRate,
      String fixedIncomeReturnRate,
      List<SimulationScenarioSummary> scenarios,
      Instant computedAt,
      String source,
      String note) {}

  record ProfileSummary(
      String currency,
      String netWorth,
      String investmentValue,
      String longTermValue,
      String liquidAssets,
      String illiquidAssets,
      Instant computedAt,
      String source) {}

  record Holding(String symbol, String value, String weightPercent) {}

  record HoldingsSummary(
      String currency,
      List<Holding> holdings,
      Instant computedAt,
      String source,
      String valuationScope) {}

  record PeriodResult(
      String period,
      String currency,
      String profit,
      boolean available,
      Instant computedAt,
      String source) {}

  record BenchmarkResult(
      String period,
      String currency,
      String symbol,
      boolean available,
      String portfolioProfit,
      String benchmarkProfit,
      String portfolioReturnPercent,
      String benchmarkReturnPercent,
      Instant computedAt,
      String source,
      String methodology) {}

  record AssetSummary(
      String symbol,
      String name,
      String currency,
      String quantity,
      String currentMarketValue,
      String price,
      String priceSource,
      java.time.ZonedDateTime priceUpdatedAt,
      String period,
      String realizedProfit,
      String netDividends,
      String source) {}

  record CashFlows(
      String currency,
      String deposits,
      String withdrawals,
      String cashBalance,
      String source,
      String contributionPeriod) {}

  record Freshness(
      String state,
      LocalDate latestPriceDate,
      LocalDate latestFxMonth,
      long missingPrices,
      long stalePrices,
      long missingFx,
      String source) {}

  record RetirementSummary(
      boolean available,
      String selectedPlan,
      Integer currentAge,
      Integer retirementAge,
      Integer endAge,
      List<String> savedPlans,
      Instant computedAt,
      String source) {}

  record Snippet(String source, String text, double relevance) {}

  record Documentation(List<Snippet> matches, Instant computedAt) {}

  record MarketQuote(
      String provider,
      String tool,
      String symbol,
      String source,
      String price,
      String currency,
      String cacheStatus,
      Instant fetchedAt,
      LocalDate asOf,
      Instant expiresAt,
      Integer remainingBudget,
      List<String> warnings) {}

  record MarketQuoteStatus(
      String providerSymbol,
      String coverage,
      String cacheStatus,
      Instant symbolCooldownUntil,
      Instant providerCooldownUntil,
      Instant budgetAvailableAt,
      int remainingBudget,
      int budgetLimit) {}

  record Capabilities(
      List<String> reportingPeriods,
      boolean externalResearchAvailable,
      Integer externalBudget,
      Integer remainingBudget,
      String quotaWindow,
      String freshnessPolicy) {}
}
