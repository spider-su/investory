package com.smartbox.investory.investment.api.reporting.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.presentation.FinancialPresentation;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record OverviewView(
    BigDecimal balance,
    BigDecimal totalProfit,
    BigDecimal netDeposits,
    BigDecimal gainPct,
    BigDecimal incomeTotal,
    BigDecimal incomeYieldPct,
    BigDecimal netGain,
    BigDecimal eurToPln,
    BigDecimal usdToPln,
    List<AccountBalance> accountBalances,
    AccountBalance accountBalancesTotal,
    Map<CurrencyType, BigDecimal> exchangeRates,
    CurrencyType baseCurrency,
    CashFlowView cashFlow,
    PositionsView positions,
    RiskView riskExposure,
    DataQualityView dataQuality,
    MonthlyPerformanceView monthlyPerformance,
    DashboardOperationalView operational,
    AssetAllocationView assetAllocation,
    PortfolioStructureView portfolioStructure) {

  public OverviewView {
    accountBalances =
        com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(accountBalances);
    exchangeRates =
        com.smartbox.investory.shared.util.CollectionUtils.immutableMapOrEmpty(exchangeRates);
  }

  public OverviewView(
      double balance,
      double totalProfit,
      double netDeposits,
      Double gainPct,
      double incomeTotal,
      Double incomeYieldPct,
      double netGain,
      Double eurToPln,
      Double usdToPln,
      List<AccountBalance> accountBalances,
      AccountBalance accountBalancesTotal,
      Map<CurrencyType, Double> exchangeRates,
      CurrencyType baseCurrency,
      CashFlowView cashFlow,
      PositionsView positions,
      RiskView riskExposure,
      DataQualityView dataQuality,
      MonthlyPerformanceView monthlyPerformance,
      DashboardOperationalView operational,
      AssetAllocationView assetAllocation) {
    this(
        decimal(balance),
        decimal(totalProfit),
        decimal(netDeposits),
        decimal(gainPct),
        decimal(incomeTotal),
        decimal(incomeYieldPct),
        decimal(netGain),
        decimal(eurToPln),
        decimal(usdToPln),
        accountBalances,
        accountBalancesTotal,
        decimals(exchangeRates),
        baseCurrency,
        cashFlow,
        positions,
        riskExposure,
        dataQuality,
        monthlyPerformance,
        operational,
        assetAllocation,
        null);
  }

  public BigDecimal deposits() {
    return decimal(cashFlow.deposits());
  }

  public BigDecimal withdrawals() {
    return decimal(cashFlow.withdrawals());
  }

  public BigDecimal cash() {
    return decimal(cashFlow.cash());
  }

  public BigDecimal realizedProfit() {
    return decimal(cashFlow.realizedProfit());
  }

  public BigDecimal dividends() {
    return decimal(cashFlow.dividends());
  }

  public BigDecimal dividendTax() {
    return decimal(cashFlow.dividendTax());
  }

  public BigDecimal interest() {
    return decimal(cashFlow.interest());
  }

  public BigDecimal capitalGainsTax() {
    return decimal(cashFlow.capitalGainsTax());
  }

  public BigDecimal lossCarryForward() {
    return decimal(cashFlow.lossCarryForward());
  }

  public List<com.smartbox.investory.investment.api.reporting.model.DividendGainer>
      dividendGainers() {
    return cashFlow.dividendGainers();
  }

  public BigDecimal unrealizedProfit() {
    return decimal(positions.unrealizedProfit());
  }

  public List<com.smartbox.investory.investment.api.reporting.model.OpenPositionValue>
      openPositionValues() {
    return positions.openPositionValues();
  }

  public com.smartbox.investory.investment.api.reporting.model.OpenPositionValue
      openPositionValuesTotal() {
    return positions.openPositionValuesTotal();
  }

  /**
   * @deprecated display formatting belongs to the web adapter; retained as a compatibility bridge.
   */
  @Deprecated(forRemoval = false)
  public String formatBase(double value) {
    return FinancialPresentation.compactMoney(BigDecimal.valueOf(value));
  }

  public String formatBase(BigDecimal value) {
    return FinancialPresentation.compactMoney(value);
  }

  /**
   * @deprecated display formatting belongs to the web adapter; retained as a compatibility bridge.
   */
  @Deprecated(forRemoval = false)
  public String formatMoney(double value, CurrencyType currency) {
    String formatted = FinancialPresentation.compactMoney(BigDecimal.valueOf(value));
    return currency == null || currency == baseCurrency
        ? formatted
        : formatted + " " + currency.name();
  }

  /**
   * @deprecated display formatting belongs to the web adapter; retained for old templates.
   */
  @Deprecated(forRemoval = false)
  public String formatPercent(BigDecimal value) {
    return DashboardPercentageFormatter.percent(value);
  }

  public String formatPercent(double value) {
    return DashboardPercentageFormatter.percent(value);
  }

  public String formatSignedPercent(BigDecimal value) {
    return value == null ? "-" : DashboardPercentageFormatter.signedPercent(value.doubleValue());
  }

  private static BigDecimal decimal(Double value) {
    return value == null ? null : BigDecimal.valueOf(value);
  }

  private static BigDecimal decimal(double value) {
    return BigDecimal.valueOf(value);
  }

  private static Map<CurrencyType, BigDecimal> decimals(Map<CurrencyType, Double> values) {
    Map<CurrencyType, BigDecimal> result = new HashMap<>();
    if (values != null) values.forEach((key, value) -> result.put(key, decimal(value)));
    return result;
  }
}
