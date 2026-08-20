package com.smartbox.investory.application.dashboard;

import com.smartbox.investory.services.models.AccountBalance;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record OverviewView(
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
    AssetAllocationView assetAllocation,
    PortfolioStructureView portfolioStructure) {

  public OverviewView {
    accountBalances = accountBalances == null ? List.of() : List.copyOf(accountBalances);
    exchangeRates = exchangeRates == null ? Map.of() : Map.copyOf(exchangeRates);
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
        balance,
        totalProfit,
        netDeposits,
        gainPct,
        incomeTotal,
        incomeYieldPct,
        netGain,
        eurToPln,
        usdToPln,
        accountBalances,
        accountBalancesTotal,
        exchangeRates,
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

  /** Format a value already expressed in the selected portfolio currency. */
  public String formatBase(Double value) {
    return formatNumber(value);
  }

  /** Format a value with an ISO code only when it is not in the selected base currency. */
  public String formatMoney(Double value, CurrencyType currency) {
    String formatted = formatNumber(value);
    return value == null || currency == null || currency == baseCurrency
        ? formatted
        : formatted + " " + currency.name();
  }

  public String formatPercent(Double value) {
    return DashboardPercentageFormatter.percent(value);
  }

  public String formatSignedPercent(Double value) {
    return DashboardPercentageFormatter.signedPercent(value);
  }

  public String formatPercentagePoints(Double value) {
    return DashboardPercentageFormatter.percentagePoints(value);
  }

  private String formatNumber(Double value) {
    if (value == null) return "-";
    NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
    formatter.setRoundingMode(RoundingMode.HALF_UP);
    formatter.setMinimumFractionDigits(0);
    formatter.setMaximumFractionDigits(0);
    return formatter.format(value);
  }

  public double deposits() {
    return cashFlow.deposits();
  }

  public double withdrawals() {
    return cashFlow.withdrawals();
  }

  public double cash() {
    return cashFlow.cash();
  }

  public double realizedProfit() {
    return cashFlow.realizedProfit();
  }

  public Map<CurrencyType, Double> realizedByCurrency() {
    return cashFlow.realizedByCurrency();
  }

  public double dividends() {
    return cashFlow.dividends();
  }

  public double dividendTax() {
    return cashFlow.dividendTax();
  }

  public double interest() {
    return cashFlow.interest();
  }

  public double capitalGainsTax() {
    return cashFlow.capitalGainsTax();
  }

  public double lossCarryForward() {
    return cashFlow.lossCarryForward();
  }

  public List<com.smartbox.investory.services.models.DividendGainer> dividendGainers() {
    return cashFlow.dividendGainers();
  }

  public double unrealizedProfit() {
    return positions.unrealizedProfit();
  }

  public Map<CurrencyType, Double> unrealizedByCurrency() {
    return positions.unrealizedByCurrency();
  }

  public List<com.smartbox.investory.services.models.OpenPositionValue> openPositionValues() {
    return positions.openPositionValues();
  }

  public com.smartbox.investory.services.models.OpenPositionValue openPositionValuesTotal() {
    return positions.openPositionValuesTotal();
  }
}
