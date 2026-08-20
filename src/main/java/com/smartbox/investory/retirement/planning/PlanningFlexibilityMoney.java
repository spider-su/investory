package com.smartbox.investory.retirement.planning;

/** Combined presentation model for spending and retirement timing flexibility. */
public record PlanningFlexibilityMoney(
    SustainableSpendingAnalysisMoney spending, RetirementAgeAnalysisMoney retirement) {}
