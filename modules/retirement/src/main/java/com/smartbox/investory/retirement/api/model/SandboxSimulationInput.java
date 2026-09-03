package com.smartbox.investory.retirement.api.model;

import java.math.BigDecimal;

/** Minimal, portfolio-independent inputs for the retirement sandbox. */
public record SandboxSimulationInput(
    int currentAge,
    int retirementAge,
    int endAge,
    BigDecimal annualSpending,
    BigDecimal inflationRate,
    BigDecimal cash,
    BigDecimal bonds,
    BigDecimal bondReturnRate,
    BigDecimal equities,
    BigDecimal equityReturnRate,
    BigDecimal monthlyRentalIncome,
    BigDecimal monthlyPensionIncome,
    int pensionAge,
    int startYear) {
  public SandboxSimulationInput(
      int currentAge,
      int retirementAge,
      int endAge,
      BigDecimal annualSpending,
      BigDecimal inflationRate,
      BigDecimal cash,
      BigDecimal bonds,
      BigDecimal bondReturnRate,
      BigDecimal equities,
      BigDecimal equityReturnRate,
      int startYear) {
    this(
        currentAge,
        retirementAge,
        endAge,
        annualSpending,
        inflationRate,
        cash,
        bonds,
        bondReturnRate,
        equities,
        equityReturnRate,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        120,
        startYear);
  }

  public SandboxSimulationInput(
      int currentAge,
      int retirementAge,
      int endAge,
      BigDecimal annualSpending,
      BigDecimal inflationRate,
      BigDecimal cash,
      BigDecimal bonds,
      BigDecimal bondReturnRate,
      BigDecimal equities,
      BigDecimal equityReturnRate,
      BigDecimal monthlyRentalIncome,
      int startYear) {
    this(
        currentAge,
        retirementAge,
        endAge,
        annualSpending,
        inflationRate,
        cash,
        bonds,
        bondReturnRate,
        equities,
        equityReturnRate,
        monthlyRentalIncome,
        BigDecimal.ZERO,
        120,
        startYear);
  }

  public SandboxSimulationInput {
    if (currentAge < 0 || currentAge > retirementAge || retirementAge > endAge || endAge > 120)
      throw new IllegalArgumentException("Invalid sandbox ages");
    requireNonNegative(annualSpending, "Annual spending");
    requireNonNegative(cash, "Cash");
    requireNonNegative(bonds, "Bonds");
    requireNonNegative(equities, "Equities");
    requireNonNegative(monthlyRentalIncome, "Monthly rental income");
    requireNonNegative(monthlyPensionIncome, "Monthly pension income");
    if (pensionAge < 0 || pensionAge > 120)
      throw new IllegalArgumentException("Pension age is invalid");
    requireRate(inflationRate, "Inflation");
    requireRate(bondReturnRate, "Bond return");
    requireRate(equityReturnRate, "Equity return");
  }

  private static void requireNonNegative(BigDecimal value, String name) {
    if (value == null || value.signum() < 0)
      throw new IllegalArgumentException(name + " is invalid");
  }

  private static void requireRate(BigDecimal value, String name) {
    if (value == null || value.compareTo(BigDecimal.ONE.negate()) < 0)
      throw new IllegalArgumentException(name + " rate is invalid");
  }
}
