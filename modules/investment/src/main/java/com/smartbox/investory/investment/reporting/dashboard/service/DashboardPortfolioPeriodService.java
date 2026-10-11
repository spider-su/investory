package com.smartbox.investory.investment.reporting.dashboard.service;

import com.smartbox.investory.investment.performance.model.Performance;
import com.smartbox.investory.investment.performance.model.Portfolio;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Copies and transforms portfolio monthly performance for a dashboard period. */
@Service
public class DashboardPortfolioPeriodService {

  public Portfolio filter(Portfolio source, YearMonth start) {
    Portfolio portfolio = copyPortfolio(source);
    apply(portfolio, start);
    return portfolio;
  }

  private void apply(Portfolio portfolio, YearMonth start) {
    if (portfolio == null || portfolio.getMonthlyPerformance() == null) {
      return;
    }
    Performance performance = portfolio.getMonthlyPerformance();
    if (start == null) {
      return;
    }
    performance.setCalculateMonthlyPerformance(
        filter(performance.getCalculateMonthlyPerformance(), start));
    performance.setMonthlyOperationsCount(filter(performance.getMonthlyOperationsCount(), start));
    performance.setMonthlyCashflow(filter(performance.getMonthlyCashflow(), start));
    performance.setMonthlyAttributions(filter(performance.getMonthlyAttributions(), start));
    performance.setTotalProfit(
        performance.getCalculateMonthlyPerformance().values().stream()
            .mapToDouble(Double::doubleValue)
            .sum());
  }

  private <T> Map<String, T> filter(Map<String, T> values, YearMonth start) {
    if (values == null || values.isEmpty()) {
      return new LinkedHashMap<>();
    }
    Map<String, T> filtered = new LinkedHashMap<>();
    values.forEach(
        (label, value) -> {
          if (!labelMonth(label).isBefore(start)) {
            filtered.put(label, value);
          }
        });
    return filtered;
  }

  private YearMonth labelMonth(String label) {
    try {
      return YearMonth.parse(label);
    } catch (DateTimeParseException ignored) {
      return YearMonth.from(LocalDate.parse(label));
    }
  }

  private static Portfolio copyPortfolio(Portfolio source) {
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
    copy.setMonthlyPerformance(copyPerformance(source.getMonthlyPerformance()));
    return copy;
  }

  private static Performance copyPerformance(Performance source) {
    if (source == null) return null;
    Performance copy = new Performance();
    copy.setBaseCurrency(source.getBaseCurrency());
    copy.setTotalOpen(source.getTotalOpen());
    copy.setTotalProfit(source.getTotalProfit());
    copy.setCalculateMonthlyPerformance(copyMap(source.getCalculateMonthlyPerformance()));
    copy.setMonthlyOperationsCount(copyMap(source.getMonthlyOperationsCount()));
    copy.setMonthlyCashflow(copyMap(source.getMonthlyCashflow()));
    copy.setMonthlyAttributions(copyMap(source.getMonthlyAttributions()));
    copy.setBase(source.getBase());
    return copy;
  }

  private static <K, V> HashMap<K, V> copyMap(Map<K, V> values) {
    return values == null ? null : new LinkedHashMap<>(values);
  }
}
