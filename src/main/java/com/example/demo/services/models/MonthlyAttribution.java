package com.example.demo.services.models;

import java.util.List;

public record MonthlyAttribution(
    String period,
    double openingEquity,
    double closingEquity,
    double deposits,
    double withdrawals,
    double netExternalFlow,
    double totalProfit,
    double marketAndFxMovement,
    double realizedTradingResult,
    double dividends,
    double cashInterest,
    double fees,
    double taxes,
    double valuationAdjustments,
    double unresolvedResidual,
    List<AccountContribution> accounts) {
    public record AccountContribution(String accountId, double openingValue, double closingValue,
                                      double netFlow, double monthlyProfit, double contributionPct) {}
}
