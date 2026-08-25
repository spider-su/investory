package com.smartbox.investory.investment.reporting.dashboard.application;

import com.smartbox.investory.investment.accounting.model.Benchmark;
import com.smartbox.investory.investment.accounting.model.Performance;
import com.smartbox.investory.investment.accounting.model.Portfolio;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

/** Defensive copies for mutable calculation models returned by the cache. */
final class DashboardCalculationCopies {
  private DashboardCalculationCopies() {}

  static Portfolio portfolio(Portfolio source) {
    if (source == null) return null;
    Portfolio copy = new Portfolio();
    copy.setBaseCurrency(source.getBaseCurrency());
    copy.setRealizedProfit(source.getRealizedProfit());
    copy.setDividends(source.getDividends());
    copy.setDividendTax(source.getDividendTax());
    copy.setCapitalGainsTax(source.getCapitalGainsTax());
    copy.setLossCarryForward(source.getLossCarryForward());
    copy.setDeposits(source.getDeposits());
    copy.setWithdrawals(source.getWithdrawals());
    copy.setNetDeposits(source.getNetDeposits());
    copy.setInterest(source.getInterest());
    copy.setUnrealizedProfit(source.getUnrealizedProfit());
    copy.setTotalProfit(source.getTotalProfit());
    copy.setReconciliationStatus(source.getReconciliationStatus());
    copy.setReconciliationDifference(source.getReconciliationDifference());
    copy.setDataQuality(source.getDataQuality());
    copy.setRiskExposure(source.getRiskExposure());
    copy.setBalance(source.getBalance());
    copy.setCash(source.getCash());
    copy.setAccountBalances(source.getAccountBalances());
    copy.setAccountBalancesTotal(source.getAccountBalancesTotal());
    copy.setOpenPositionValues(source.getOpenPositionValues());
    copy.setOpenPositionValuesTotal(source.getOpenPositionValuesTotal());
    copy.setDividendGainers(source.getDividendGainers());
    copy.setRoi(source.getRoi());
    copy.setExchangeRates(copyMap(source.getExchangeRates()));
    copy.setPerformancePerSymbol(source.getPerformancePerSymbol());
    copy.setMonthlyPerformance(performance(source.getMonthlyPerformance()));
    return copy;
  }

  private static Performance performance(Performance source) {
    if (source == null) return null;
    Performance copy = new Performance();
    copy.setBaseCurrency(source.getBaseCurrency());
    copy.setTotalOpen(source.getTotalOpen());
    copy.setTotalProfit(source.getTotalProfit());
    copy.setCalculateMonthlyPerformance(copyMap(source.getCalculateMonthlyPerformance()));
    copy.setMonthlyOperationsCount(
        source.getMonthlyOperationsCount() == null
            ? null
            : new TreeMap<>(source.getMonthlyOperationsCount()));
    copy.setMonthlyCashflow(copyMap(source.getMonthlyCashflow()));
    copy.setMonthlyAttributions(
        source.getMonthlyAttributions() == null
            ? null
            : new TreeMap<>(source.getMonthlyAttributions()));
    copy.setBase(source.getBase());
    return copy;
  }

  static Benchmark benchmark(Benchmark source) {
    if (source == null) return null;
    Benchmark copy = new Benchmark();
    copy.setAvailable(source.isAvailable());
    copy.setPortfolioPerformanceAvailable(source.isPortfolioPerformanceAvailable());
    copy.setBenchmarkAvailable(source.isBenchmarkAvailable());
    copy.setSymbol(source.getSymbol());
    copy.setLabels(source.getLabels() == null ? null : new ArrayList<>(source.getLabels()));
    copy.setPortfolioCurve(copyList(source.getPortfolioCurve()));
    copy.setBenchmarkCurve(copyList(source.getBenchmarkCurve()));
    copy.setPortfolioReturnCurve(copyList(source.getPortfolioReturnCurve()));
    copy.setBenchmarkReturnCurve(copyList(source.getBenchmarkReturnCurve()));
    copy.setInvestedCapital(source.getInvestedCapital());
    copy.setPortfolioPl(source.getPortfolioPl());
    copy.setBenchmarkPl(source.getBenchmarkPl());
    copy.setPortfolioReturnPct(source.getPortfolioReturnPct());
    copy.setBenchmarkReturnPct(source.getBenchmarkReturnPct());
    copy.setAlpha(source.getAlpha());
    copy.setAccountOptions(
        source.getAccountOptions() == null ? null : new ArrayList<>(source.getAccountOptions()));
    copy.setAccountSeries(
        source.getAccountSeries() == null
            ? null
            : source.getAccountSeries().stream()
                .map(
                    series ->
                        new Benchmark.AccountSeries(
                            series.id(),
                            series.investedCapital(),
                            series.portfolioPl(),
                            series.benchmarkPl(),
                            copyList(series.portfolioCurve()),
                            copyList(series.benchmarkCurve()),
                            copyList(series.returnCapitalCurve()),
                            copyList(series.returnContributionCurve()),
                            copyList(series.returnPctCurve())))
                .toList());
    copy.setAccountValuesAvailable(source.isAccountValuesAvailable());
    copy.setSelectedAccountValueYear(source.getSelectedAccountValueYear());
    copy.setAccountValueYears(source.getAccountValueYears());
    return copy;
  }

  private static <K, V> HashMap<K, V> copyMap(java.util.Map<K, V> values) {
    return values == null ? null : new HashMap<>(values);
  }

  private static List<Double> copyList(List<Double> values) {
    return values == null ? null : new ArrayList<>(values);
  }
}
