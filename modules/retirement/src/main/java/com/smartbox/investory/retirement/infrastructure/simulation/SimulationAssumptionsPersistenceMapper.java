package com.smartbox.investory.retirement.infrastructure.simulation;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.ExpenseProfile;
import com.smartbox.investory.retirement.api.model.ExpenseProfileStep;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.retirement.api.model.SimulationEvent;
import com.smartbox.investory.retirement.api.model.SimulationFundingStrategy;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Sole translation boundary between persistence and active simulation assumptions. */
public final class SimulationAssumptionsPersistenceMapper {
  private SimulationAssumptionsPersistenceMapper() {}

  public static SimulationAssumptions read(
      PersistedSimulationAssumptions source, List<SimulationEvent> events) {
    return new SimulationAssumptions(
        source.getCurrentAge(),
        source.getEndAge(),
        source.getAnnualLivingExpenses(),
        source.getInflationRate(),
        source.getFixedIncomeReturnRate(),
        source.getEquityReturnRate(),
        readPensionStartAge(source.getPensionStartAge()),
        source.getAnnualPension(),
        defaultValue(source.getCapitalGainTaxRate(), BigDecimal.ZERO),
        source.getStartYear(),
        source.getAnnualDiscretionaryExpenses(),
        events,
        defaultValue(
            source.getRentalIncomeGrowthSpread(),
            SimulationAssumptions.DEFAULT_RENTAL_INCOME_GROWTH_SPREAD),
        defaultValue(
            source.getSpendingGrowthSpread(), SimulationAssumptions.DEFAULT_SPENDING_GROWTH_SPREAD),
        SimulationFundingStrategy.SIMPLE_WATERFALL,
        defaultValue(
            source.getSafeReserveYears(), SimulationAssumptions.DEFAULT_SAFE_RESERVE_YEARS),
        defaultValue(source.getEquityHarvestMinimumReturnRate(), BigDecimal.ZERO),
        defaultValue(source.getEquityGainHarvestRate(), BigDecimal.ZERO),
        source.getAllowEmergencyEquityWithdrawal() == null
            || source.getAllowEmergencyEquityWithdrawal(),
        source.getRetirementAge() == null ? source.getCurrentAge() : source.getRetirementAge(),
        defaultValue(source.getAnnualEmploymentIncome(), BigDecimal.ZERO),
        defaultValue(source.getAnnualPreRetirementContribution(), BigDecimal.ZERO),
        parseFundingOrder(source.getFundingOrder()),
        parseExpenseProfile(source.getExpenseProfile()));
  }

  public static void write(
      PersistedSimulationAssumptions target, SimulationAssumptions assumptions) {
    target.setCurrentAge(assumptions.currentAge());
    target.setStartYear(assumptions.startYear());
    target.setEndAge(assumptions.endAge());
    target.setRetirementAge(assumptions.retirementAge());
    target.setAnnualEmploymentIncome(assumptions.annualEmploymentIncome());
    target.setAnnualPreRetirementContribution(assumptions.annualPreRetirementContribution());
    target.setAnnualLivingExpenses(assumptions.annualLivingExpenses());
    target.setAnnualDiscretionaryExpenses(assumptions.annualDiscretionaryExpenses());
    target.setInflationRate(assumptions.inflationRate());
    target.setRentalIncomeGrowthSpread(assumptions.rentalIncomeGrowthSpread());
    target.setSpendingGrowthSpread(assumptions.spendingGrowthSpread());
    target.setFundingStrategy(assumptions.fundingStrategy());
    target.setExpenseProfile(serializeExpenseProfile(assumptions.expenseProfile()));
    target.setFundingOrder(
        assumptions.fundingOrder().stream()
            .map(SimulationAssumptionsPersistenceMapper::serializeFundingSource)
            .collect(Collectors.joining(",")));
    target.setSafeReserveYears(assumptions.safeReserveYears());
    target.setEquityHarvestMinimumReturnRate(assumptions.equityHarvestMinimumReturnRate());
    target.setEquityGainHarvestRate(assumptions.equityGainHarvestRate());
    target.setAllowEmergencyEquityWithdrawal(assumptions.allowEmergencyEquityWithdrawal());
    target.setFixedIncomeReturnRate(assumptions.fixedIncomeReturnRate());
    target.setEquityReturnRate(assumptions.equityReturnRate());
    target.setPensionStartAge(writePensionStartAge(assumptions.pensionStartAge()));
    target.setAnnualPension(assumptions.annualPension());
    target.setCapitalGainTaxRate(assumptions.capitalGainTaxRate());
  }

  static String serializeExpenseProfile(ExpenseProfile profile) {
    return profile.steps().stream()
        .map(step -> step.fromYear() + ":" + step.factor().toPlainString())
        .reduce((left, right) -> left + ";" + right)
        .orElse("");
  }

  static ExpenseProfile parseExpenseProfile(String value) {
    if (value == null || isBlank(value)) return ExpenseProfile.EMPTY;
    try {
      return new ExpenseProfile(
          Arrays.stream(value.split(";"))
              .map(String::trim)
              .map(
                  entry -> {
                    String[] parts = entry.split(":", -1);
                    if (parts.length != 2) throw new IllegalArgumentException();
                    return new ExpenseProfileStep(
                        Integer.parseInt(parts[0].trim()), new BigDecimal(parts[1].trim()));
                  })
              .toList());
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Invalid expense profile in simulation plan", exception);
    }
  }

  private static String serializeFundingSource(RetirementFundingSource source) {
    return switch (source) {
      case RESERVE -> "CASH";
      case LONG_TERM -> "BONDS";
      case INVESTMENT -> "STOCKS";
    };
  }

  static List<RetirementFundingSource> parseFundingOrder(String value) {
    if (value == null || isBlank(value)) return SimulationAssumptions.DEFAULT_FUNDING_ORDER;
    try {
      return Arrays.stream(value.split(","))
          .map(String::trim)
          .map(
              token ->
                  switch (token) {
                    case "CASH", "RESERVE" -> RetirementFundingSource.RESERVE;
                    case "BONDS", "LONG_TERM" -> RetirementFundingSource.LONG_TERM;
                    case "STOCKS", "INVESTMENT" -> RetirementFundingSource.INVESTMENT;
                    default ->
                        throw new IllegalArgumentException("Unknown funding source: " + token);
                  })
          .toList();
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Invalid funding order in simulation plan", exception);
    }
  }

  private static BigDecimal defaultValue(BigDecimal value, BigDecimal fallback) {
    return value == null ? fallback : value;
  }

  private static Integer readPensionStartAge(Integer value) {
    return value == null || value == Integer.MAX_VALUE ? null : value;
  }

  private static int writePensionStartAge(Integer value) {
    return value == null ? Integer.MAX_VALUE : value;
  }
}
