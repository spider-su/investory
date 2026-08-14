package com.smartbox.investory.application.planning;

/** Display-currency presentation of sustainable-spending analysis. */
public record SustainableSpendingAnalysisMoney(
    String currentRecurringSpending,
    String baseLimit,
    String conservativeLimit,
    String baseHeadroom,
    String conservativeHeadroom,
    String baseHeadroomPercentage,
    String conservativeHeadroomPercentage,
    boolean baseAboveLimit,
    boolean conservativeAboveLimit,
    String interpretation) {}
