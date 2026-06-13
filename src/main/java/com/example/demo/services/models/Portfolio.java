package com.example.demo.services.models;

import com.example.demo.data.CurrencyType;
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

    double totalUnrealizedInBase = 0.0;
    Map<CurrencyType, Double> unrealizedByCurrency = new HashMap<>();

    double total = 0.0;

    List<InstrumentPerformance> performancePerSymbol;

    Performance monthlyPerformance;

    Map<String, OpenPositionsPerformance> openPositionsFlow;

}
