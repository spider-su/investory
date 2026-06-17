package com.example.demo.services.models;

import com.example.demo.infrastructure.CurrencyType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Portfolio {
    CurrencyType baseCurrency = CurrencyType.USD;
    double totalProfitInBase = 0.0;
    Map<CurrencyType, Double> profitByCurrency = new HashMap<>();

    double dividends = 0.0;
    Map<CurrencyType, Double> dividendsByCurrency = new HashMap<>();

    /** Dividend withholding tax already deducted (base currency, negative). */
    double dividendTax = 0.0;
    /** Estimated capital-gains tax: 19% of the current tax year's net realized gains (base currency, positive). */
    double capitalGainsTax = 0.0;
    /** Prior-year losses applied (carried forward) to reduce the current year's taxable gain (base currency). */
    double lossCarryForward = 0.0;

    /** External cash funding (base currency), excluding internal transfers / FX conversions. */
    double deposits = 0.0;
    double withdrawals = 0.0;
    double netDeposits = 0.0;
    /** Free-funds interest net of interest tax (base currency). */
    double interest = 0.0;

    double totalUnrealizedInBase = 0.0;
    Map<CurrencyType, Double> unrealizedByCurrency = new HashMap<>();

    double total = 0.0;

    /** Total assets value (cash + open positions) across all accounts, converted to base currency. */
    double balance = 0.0;

    Map<CurrencyType, Double> exchangeRates = new HashMap<>();

    List<InstrumentPerformance> performancePerSymbol;

    Performance monthlyPerformance;

    Map<String, OpenPositionsPerformance> openPositionsFlow;

}
