package com.smartbox.investory.retirement.api.model;

/** Combined presentation model for spending and retirement timing flexibility. */
public record PlanningFlexibilityMoney(
    SustainableSpendingAnalysisMoney spending, RetirementAgeAnalysisMoney retirement) {}
