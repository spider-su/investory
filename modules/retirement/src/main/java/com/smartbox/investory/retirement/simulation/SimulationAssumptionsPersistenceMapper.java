package com.smartbox.investory.retirement.simulation;

import static com.smartbox.investory.shared.util.StringUtils.isBlank;

import com.smartbox.investory.retirement.infrastructure.simulation.PersistedSimulationAssumptions;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/** Sole translation boundary between compatibility-shaped persistence and active assumptions. */
final class SimulationAssumptionsPersistenceMapper {
  private SimulationAssumptionsPersistenceMapper() {}

  static SimulationAssumptions read(
      PersistedSimulationAssumptions source, List<SimulationEvent> events) {
    return new SimulationAssumptions(
        source.getCurrentAge(),
        source.getEndAge(),
        source.getAnnualLivingExpenses(),
        source.getInflationRate(),
        source.getCashReturnRate(),
        source.getFixedIncomeReturnRate(),
        source.getEquityReturnRate(),
        source.getRealEstateReturnRate(),
        source.getOtherReturnRate(),
        source.getPensionStartAge(),
        source.getAnnualPension(),
        source.getCapitalGainTaxRate(),
        source.getStartYear(),
        source.getAnnualDiscretionaryExpenses(),
        events,
        defaultValue(
            source.getRentalIncomeGrowthSpread(),
            SimulationAssumptions.DEFAULT_RENTAL_INCOME_GROWTH_SPREAD),
        defaultValue(
            source.getSpendingGrowthSpread(), SimulationAssumptions.DEFAULT_SPENDING_GROWTH_SPREAD),
        source.getFundingStrategy() == null
            ? SimulationFundingStrategy.SIMPLE_WATERFALL
            : source.getFundingStrategy(),
        defaultValue(source.getSafeReserveYears(), BigDecimal.ZERO),
        defaultValue(source.getEquityHarvestMinimumReturnRate(), BigDecimal.ZERO),
        defaultValue(source.getEquityGainHarvestRate(), BigDecimal.ZERO),
        source.getAllowEmergencyEquityWithdrawal() == null
            || source.getAllowEmergencyEquityWithdrawal(),
        source.getRetirementAge() == null ? source.getCurrentAge() : source.getRetirementAge(),
        defaultValue(source.getAnnualEmploymentIncome(), BigDecimal.ZERO),
        defaultValue(source.getAnnualPreRetirementContribution(), BigDecimal.ZERO),
        parseFundingOrder(source.getFundingOrder()),
        parseExpenseProfile(source.getExpenseProfile()),
        new ProjectedIncomePolicy(
            source.getRentalIncomeMode(),
            source.getManualRentalIncome(),
            source.getBondCashIncomeMode(),
            source.getManualBondCashIncome()));
  }

  @SuppressWarnings("deprecation")
  static void write(PersistedSimulationAssumptions target, SimulationAssumptions assumptions) {
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
    target.setRentalIncomeMode(assumptions.projectedIncomePolicy().rentalIncomeMode());
    target.setManualRentalIncome(assumptions.projectedIncomePolicy().manualRentalIncome());
    target.setBondCashIncomeMode(assumptions.projectedIncomePolicy().bondCashIncomeMode());
    target.setManualBondCashIncome(assumptions.projectedIncomePolicy().manualBondCashIncome());
    target.setFundingStrategy(assumptions.fundingStrategy());
    target.setFundingOrder(serializeFundingOrder(assumptions.fundingOrder()));
    target.setExpenseProfile(serializeExpenseProfile(assumptions.expenseProfile()));
    target.setSafeReserveYears(assumptions.safeReserveYears());
    target.setEquityHarvestMinimumReturnRate(assumptions.equityHarvestMinimumReturnRate());
    target.setEquityGainHarvestRate(assumptions.equityGainHarvestRate());
    target.setAllowEmergencyEquityWithdrawal(assumptions.allowEmergencyEquityWithdrawal());
    target.setCashReturnRate(assumptions.cashReturnRate());
    target.setFixedIncomeReturnRate(assumptions.fixedIncomeReturnRate());
    target.setEquityReturnRate(assumptions.equityReturnRate());
    target.setRealEstateReturnRate(assumptions.realEstateReturnRate());
    target.setOtherReturnRate(assumptions.otherReturnRate());
    target.setPensionStartAge(assumptions.pensionStartAge());
    target.setAnnualPension(assumptions.annualPension());
    target.setCapitalGainTaxRate(assumptions.capitalGainTaxRate());
  }

  static String serializeFundingOrder(List<FundingSource> order) {
    return String.join(",", order.stream().map(Enum::name).toList());
  }

  static List<FundingSource> parseFundingOrder(String value) {
    if (value == null || isBlank(value)) return SimulationAssumptions.DEFAULT_FUNDING_ORDER;
    try {
      return Arrays.stream(value.split(",")).map(String::trim).map(FundingSource::valueOf).toList();
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Unknown funding source in simulation plan", exception);
    }
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

  private static BigDecimal defaultValue(BigDecimal value, BigDecimal fallback) {
    return value == null ? fallback : value;
  }
}
