package com.smartbox.investory.application.planning;

/** Combined presentation model for spending and retirement timing flexibility. */
public record PlanningFlexibilityMoney(
    SustainableSpendingAnalysisMoney spending, RetirementAgeAnalysisMoney retirement) {}
