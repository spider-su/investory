package com.smartbox.investory.investment.performance;

import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioAssetAllocationRepository;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioCurrencyBreakdownRepository;
import com.smartbox.investory.investment.performance.model.Portfolio;
import com.smartbox.investory.investment.performance.model.RiskExposureSummary;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
final class PortfolioRiskExposureCalculator {
  private static final String ACCOUNT_LATEST = "ACCOUNT_LATEST";

  private final PortfolioAssetAllocationRepository assetAllocations;
  private final PortfolioCurrencyBreakdownRepository currencyBreakdowns;

  void applyTo(Portfolio portfolio) {
    var allocationRows = assetAllocations.findAll();
    double total =
        allocationRows.stream()
            .mapToDouble(row -> nonZero(row.getTotalValueInBaseCurrency()))
            .sum();
    if (total <= 0.0) {
      portfolio.setRiskExposure(RiskExposureSummary.unavailable(portfolio.getCash()));
      return;
    }

    List<Double> weights =
        allocationRows.stream()
            .map(row -> nonZero(row.getTotalValueInBaseCurrency()) / total * 100.0)
            .sorted(Comparator.reverseOrder())
            .toList();
    double largest = weights.isEmpty() ? 0.0 : weights.getFirst();
    double topFive = weights.stream().limit(5).mapToDouble(Double::doubleValue).sum();
    double baseExposure = exposureFor(portfolio, true) / portfolio.getBalance() * 100.0;
    double foreignExposure = exposureFor(portfolio, false) / portfolio.getBalance() * 100.0;
    List<String> warnings = new ArrayList<>();
    if (largest >= 20.0) warnings.add("Largest holding exceeds 20% of portfolio.");
    if (topFive >= 50.0) warnings.add("Top five holdings exceed 50% of portfolio.");
    portfolio.setRiskExposure(
        new RiskExposureSummary(
            largest,
            topFive,
            baseExposure,
            foreignExposure,
            portfolio.getCash(),
            portfolio.getDividends() + portfolio.getInterest(),
            "Current snapshot · base "
                + portfolio.getBaseCurrency()
                + " · cash excluded from asset concentration",
            warnings));
  }

  private double exposureFor(Portfolio portfolio, boolean baseCurrency) {
    CurrencyType portfolioCurrency = portfolio.getBaseCurrency();
    return currencyBreakdowns.findAllByMetricType(ACCOUNT_LATEST).stream()
        .filter(row -> baseCurrency == (row.getCurrency() == portfolioCurrency))
        .mapToDouble(row -> nonZero(row.getAmountInBaseCurrency()))
        .sum();
  }

  private static double nonZero(BigDecimal value) {
    return value == null ? 0.0 : value.doubleValue();
  }
}
