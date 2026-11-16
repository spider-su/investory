package com.smartbox.investory.mcp;

import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Application query port. Adapters never depend on controllers or persistence. */
public interface PortfolioResearchClient {
  ProfileSummary profile(long portfolioId);

  HoldingsSummary holdings(long portfolioId, int limit);

  PeriodResult performance(long portfolioId, DashboardPeriod period);

  BenchmarkResult benchmark(long portfolioId, DashboardPeriod period);

  RetirementSummary retirement(long portfolioId);

  Documentation search(long portfolioId, String query, int limit);

  MarketQuote quote(long portfolioId, String symbol);

  Capabilities capabilities(long portfolioId);

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
      String data,
      String cacheStatus,
      Instant fetchedAt,
      LocalDate asOf,
      Instant expiresAt,
      Integer remainingBudget,
      List<String> warnings) {}

  record Capabilities(
      List<String> reportingPeriods,
      boolean externalResearchAvailable,
      Integer externalBudget,
      Integer remainingBudget,
      String quotaWindow,
      String freshnessPolicy) {}
}
