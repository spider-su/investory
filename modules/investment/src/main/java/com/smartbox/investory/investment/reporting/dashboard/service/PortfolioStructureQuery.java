package com.smartbox.investory.investment.reporting.dashboard.service;

import com.smartbox.investory.investment.accounting.model.AccountBalance;
import com.smartbox.investory.investment.accounting.model.OpenPositionValue;
import com.smartbox.investory.investment.accounting.model.Portfolio;
import com.smartbox.investory.investment.reporting.dashboard.application.AssetAllocationView;
import com.smartbox.investory.investment.reporting.dashboard.application.PortfolioStructureView;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Builds the current, portfolio-level structure snapshot used by the dashboard. */
@Service
public class PortfolioStructureQuery {

  private final AssetAllocationQuery assetAllocationQuery;

  public PortfolioStructureQuery(AssetAllocationQuery assetAllocationQuery) {
    this.assetAllocationQuery = assetAllocationQuery;
  }

  public PortfolioStructureView load(Long portfolioId, Portfolio portfolio) {
    AssetAllocationView allocation =
        assetAllocationQuery == null
            ? new AssetAllocationView(0.0, List.of())
            : assetAllocationQuery.load(portfolioId, portfolio);
    return load(portfolio, allocation);
  }

  public PortfolioStructureView load(Portfolio portfolio, AssetAllocationView allocation) {
    double total = portfolio.getBalance();
    double cash = portfolio.getCash();
    List<OpenPositionValue> positions =
        portfolio.getOpenPositionValues() == null ? List.of() : portfolio.getOpenPositionValues();

    // Symbols are Investory's portfolio-level instrument identifiers. Aggregate across accounts
    // before calculating concentration so duplicate account rows do not understate a holding.
    List<PortfolioStructureView.Holding> holdings =
        positions.stream()
            .filter(position -> position.getSymbol() != null && position.getValue() > 0.005)
            .collect(
                Collectors.groupingBy(
                    OpenPositionValue::getSymbol,
                    java.util.LinkedHashMap::new,
                    Collectors.toList()))
            .entrySet()
            .stream()
            .map(
                entry -> {
                  double value =
                      entry.getValue().stream().mapToDouble(OpenPositionValue::getValue).sum();
                  double unrealized =
                      entry.getValue().stream().mapToDouble(OpenPositionValue::getUnrealized).sum();
                  return new PortfolioStructureView.Holding(
                      entry.getKey(), value, weight(value, total), unrealized);
                })
            .sorted(Comparator.comparingDouble(PortfolioStructureView.Holding::value).reversed())
            .toList();

    List<PortfolioStructureView.CurrencyBucket> currencies =
        portfolio.getAccountBalances() == null
            ? List.of()
            : portfolio.getAccountBalances().stream()
                .filter(account -> account.getLocalCurrency() != null)
                .collect(
                    Collectors.groupingBy(
                        AccountBalance::getLocalCurrency,
                        java.util.LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet()
                .stream()
                .map(entry -> currencyBucket(entry.getKey(), entry.getValue(), total))
                .sorted(
                    Comparator.comparingDouble(PortfolioStructureView.CurrencyBucket::value)
                        .reversed())
                .toList();

    return new PortfolioStructureView(
        cash,
        weight(cash, total),
        holdings.isEmpty() ? null : holdings.getFirst(),
        holdings.stream().limit(5).mapToDouble(PortfolioStructureView.Holding::weightPct).sum(),
        holdings.stream().limit(10).mapToDouble(PortfolioStructureView.Holding::weightPct).sum(),
        holdings.stream().limit(10).toList(),
        currencies,
        allocation);
  }

  private static PortfolioStructureView.CurrencyBucket currencyBucket(
      CurrencyType currency, List<AccountBalance> accounts, double total) {
    double value = accounts.stream().mapToDouble(AccountBalance::getBalance).sum();
    return new PortfolioStructureView.CurrencyBucket(
        currency,
        value,
        weight(value, total),
        accounts.stream().map(AccountBalance::getAccountName).toList());
  }

  private static double weight(double value, double total) {
    return total > 0.005 ? value / total * 100.0 : 0.0;
  }
}
