package com.smartbox.investory.investment.performance.model;

import com.smartbox.investory.investment.api.reporting.model.AccountBalance;
import com.smartbox.investory.investment.api.reporting.model.DividendGainer;
import com.smartbox.investory.investment.api.reporting.model.InstrumentPerformance;
import com.smartbox.investory.investment.api.reporting.model.OpenPositionValue;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.policy.FinancialPolicyDefaults;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Portfolio {
  CurrencyType baseCurrency = FinancialPolicyDefaults.CANONICAL_CURRENCY;

  /** Current household planning currency; reserved for Long-Term and simulation use. */
  CurrencyType localCurrency = CurrencyType.PLN;

  double realizedProfit = 0.0;

  double dividends = 0.0;

  /** Dividend withholding tax already deducted (base currency, negative). */
  double dividendTax = 0.0;

  /**
   * Estimated capital-gains tax: 19% of the current tax year's net realized gains (base currency,
   * positive).
   */
  double capitalGainsTax = 0.0;

  /**
   * Prior-year losses applied (carried forward) to reduce the current year's taxable gain (base
   * currency).
   */
  double lossCarryForward = 0.0;

  /** External cash funding (base currency), excluding internal transfers / FX conversions. */
  double deposits = 0.0;

  double withdrawals = 0.0;
  double netDeposits = 0.0;

  /** Free-funds interest net of interest tax (base currency). */
  double interest = 0.0;

  double unrealizedProfit = 0.0;

  double totalProfit = 0.0;

  String reconciliationStatus = "UNKNOWN";
  double reconciliationDifference = 0.0;
  PortfolioDataQuality dataQuality = PortfolioDataQuality.unknown();
  RiskExposureSummary riskExposure = RiskExposureSummary.unavailable(0.0);

  /** Total assets value (cash + open positions) across all accounts, converted to base currency. */
  double balance = 0.0;

  /** Free cash across all accounts in base currency. */
  double cash = 0.0;

  List<AccountBalance> accountBalances;
  AccountBalance accountBalancesTotal;
  List<OpenPositionValue> openPositionValues;
  OpenPositionValue openPositionValuesTotal;
  List<DividendGainer> dividendGainers;

  /** Return on investment: (balance - netDeposits) / netDeposits * 100. */
  double roi = 0.0;

  Map<CurrencyType, Double> exchangeRates = new HashMap<>();

  List<InstrumentPerformance> performancePerSymbol;

  Performance monthlyPerformance;
}
