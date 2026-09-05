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
    double total =
        assetAllocationQuery == null
            ? portfolio.getBalance()
            : allocation.totalValue().doubleValue();
    List<PortfolioStructureView.Holding> holdings =
        assetAllocationQuery == null
            ? List.of()
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
    return build(portfolio, allocation, total, holdings);
  }

  private PortfolioStructureView build(
      Portfolio portfolio,
      AssetAllocationView allocation,
      double total,
      List<PortfolioStructureView.Holding> holdings) {
    double cash = portfolio.getCash();

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
