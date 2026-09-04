package com.smartbox.investory.investment.reporting.dashboard.service;

import com.smartbox.investory.investment.api.reporting.model.AccountBalance;
import com.smartbox.investory.investment.api.reporting.model.AssetAllocationView;
import com.smartbox.investory.investment.api.reporting.model.PortfolioStructureView;
import com.smartbox.investory.investment.performance.model.Portfolio;
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
    return load(portfolioId, portfolio, allocation);
  }

  public PortfolioStructureView load(Portfolio portfolio, AssetAllocationView allocation) {
    return load(null, portfolio, allocation);
  }

  private PortfolioStructureView load(
      Long portfolioId, Portfolio portfolio, AssetAllocationView allocation) {
    double total =
        portfolioId == null || assetAllocationQuery == null
            ? portfolio.getBalance()
            : allocation.totalValue().doubleValue();
    double cash = portfolio.getCash();

    // Symbols are Investory's portfolio-level instrument identifiers. Aggregate across accounts
    // before calculating concentration so duplicate account rows do not understate a holding.
    List<PortfolioStructureView.Holding> holdings =
        portfolioId == null || assetAllocationQuery == null
            ? legacyHoldings(portfolio, total)
            : assetAllocationQuery.canonicalHoldings(portfolioId).stream()
                .map(
                    row ->
                        new PortfolioStructureView.Holding(
                            row.symbol(),
                            row.value().doubleValue(),
                            weight(row.value().doubleValue(), total),
                            row.unrealized().doubleValue()))
                .sorted(
                    Comparator.comparingDouble(PortfolioStructureView.Holding::value).reversed())
                .toList();

    // Account-balance exposure; distinct from SQL portfolio_currency_breakdown P/L-by-currency.
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

  private static List<PortfolioStructureView.Holding> legacyHoldings(
      Portfolio portfolio, double total) {
    if (portfolio.getOpenPositionValues() == null) return List.of();
    return portfolio.getOpenPositionValues().stream()
        .filter(position -> position.getSymbol() != null && !position.getSymbol().isBlank())
        .collect(
            Collectors.groupingBy(
                position -> position.getSymbol(),
                java.util.LinkedHashMap::new,
                Collectors.toList()))
        .entrySet()
        .stream()
        .map(
            entry -> {
              double value =
                  entry.getValue().stream()
                      .mapToDouble(
                          position ->
                              position.getValue() == null ? 0.0 : position.getValue().doubleValue())
                      .sum();
              double unrealized =
                  entry.getValue().stream()
                      .mapToDouble(
                          position ->
                              position.getUnrealized() == null
                                  ? 0.0
                                  : position.getUnrealized().doubleValue())
                      .sum();
              return new PortfolioStructureView.Holding(
                  entry.getKey(), value, weight(value, total), unrealized);
            })
        .sorted(Comparator.comparingDouble(PortfolioStructureView.Holding::value).reversed())
        .toList();
  }

  private static PortfolioStructureView.CurrencyBucket currencyBucket(
      CurrencyType currency, List<AccountBalance> accounts, double total) {
    double value =
        accounts.stream()
            .map(AccountBalance::getBalance)
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
            .doubleValue();
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
