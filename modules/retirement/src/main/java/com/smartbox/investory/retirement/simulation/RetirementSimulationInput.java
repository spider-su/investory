package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.investment.api.InvestmentAnnualProjectionApi;
import com.smartbox.investory.longterm.api.LongTermAnnualProjectionApi;
import java.math.BigDecimal;
import java.util.List;

/** Inputs for the simplified yearly retirement orchestrator. */
public record RetirementSimulationInput(
    int currentAge,
    int endAge,
    int startYear,
    int retirementAge,
    BigDecimal annualExpenses,
    BigDecimal spendingGrowthRate,
    BigDecimal annualPension,
    int pensionStartAge,
    BigDecimal annualEmploymentIncome,
    BigDecimal annualPreRetirementContribution,
    BigDecimal initialReserve,
    BigDecimal initialInvestmentValue,
    BigDecimal investmentReturnRate,
    List<LongTermYearInput> longTermYears,
    List<SimulationEvent> events,
    InvestmentAnnualProjectionApi.Source investmentSource) {
  public RetirementSimulationInput(
      int currentAge, int endAge, int startYear, int retirementAge, BigDecimal annualExpenses,
      BigDecimal spendingGrowthRate, BigDecimal annualPension, int pensionStartAge,
      BigDecimal annualEmploymentIncome, BigDecimal initialReserve, BigDecimal initialInvestmentValue,
      BigDecimal investmentReturnRate, List<LongTermYearInput> longTermYears,
      List<SimulationEvent> events, InvestmentAnnualProjectionApi.Source investmentSource) {
    this(currentAge, endAge, startYear, retirementAge, annualExpenses, spendingGrowthRate,
        annualPension, pensionStartAge, annualEmploymentIncome, BigDecimal.ZERO, initialReserve,
        initialInvestmentValue, investmentReturnRate, longTermYears, events, investmentSource);
  }

  public RetirementSimulationInput {
    if (currentAge < 0 || endAge < currentAge || retirementAge < 0 || retirementAge > endAge)
      throw new IllegalArgumentException("Invalid retirement horizon");
    annualExpenses = nz(annualExpenses);
    spendingGrowthRate = nz(spendingGrowthRate);
    annualPension = nz(annualPension);
    annualEmploymentIncome = nz(annualEmploymentIncome);
    annualPreRetirementContribution = nz(annualPreRetirementContribution);
    initialReserve = nz(initialReserve);
    initialInvestmentValue = nz(initialInvestmentValue);
    investmentReturnRate = nz(investmentReturnRate);
    longTermYears = longTermYears == null ? List.of() : List.copyOf(longTermYears);
    events = events == null ? List.of() : List.copyOf(events);
    investmentSource = investmentSource == null ? InvestmentAnnualProjectionApi.Source.PROJECTED : investmentSource;
  }

  public record LongTermYearInput(
      int year,
      List<LongTermAnnualProjectionApi.Bond> bonds,
      List<LongTermAnnualProjectionApi.RentalIncome> rentalIncome) {
    public LongTermYearInput {
      bonds = bonds == null ? List.of() : List.copyOf(bonds);
      rentalIncome = rentalIncome == null ? List.of() : List.copyOf(rentalIncome);
    }
  }

  private static BigDecimal nz(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
