package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.investment.api.InvestmentAnnualProjectionApi;
import com.smartbox.investory.longterm.api.LongTermAnnualProjectionApi;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Coordinates yearly income-gap funding. It owns no asset or investment mechanics. */
public final class SimplifiedRetirementSimulation {
  private final LongTermAnnualProjectionApi longTerm;
  private final InvestmentAnnualProjectionApi investments;

  public SimplifiedRetirementSimulation(
      LongTermAnnualProjectionApi longTerm, InvestmentAnnualProjectionApi investments) {
    this.longTerm = longTerm;
    this.investments = investments;
  }

  public Result run(RetirementSimulationInput input) {
    List<Year> years = new ArrayList<>();
    BigDecimal reserve = input.initialReserve();
    BigDecimal investmentValue = input.initialInvestmentValue();
    List<LongTermAnnualProjectionApi.Bond> bonds = List.of();
    BigDecimal spending = input.annualExpenses();
    for (int age = input.currentAge(); age <= input.endAge(); age++) {
      int year = input.startYear() + age - input.currentAge();
      boolean retired = age >= input.retirementAge();
      BigDecimal expenses = retired ? spending : BigDecimal.ZERO;
      BigDecimal employment = retired ? BigDecimal.ZERO : input.annualEmploymentIncome();
      BigDecimal pension = age >= input.pensionStartAge() ? input.annualPension() : BigDecimal.ZERO;
      BigDecimal eventIncome = events(input, year, SimulationEventType.ONE_OFF_INCOME);
      BigDecimal eventExpenses = events(input, year, SimulationEventType.ONE_OFF_EXPENSE);
      RetirementSimulationInput.LongTermYearInput assets = assets(input, year);
      if (!assets.bonds().isEmpty() || !assets.rentalIncome().isEmpty()) bonds = assets.bonds();
      var preview = longTerm.project(new LongTermAnnualProjectionApi.ProjectionRequest(
          year, reserve, BigDecimal.ZERO, bonds, assets.rentalIncome()));
      BigDecimal gap = expenses.add(eventExpenses)
          .subtract(preview.monthlyNetRentalIncome().multiply(BigDecimal.valueOf(12)))
          .subtract(preview.netBondIncome())
          .subtract(pension).subtract(employment).subtract(eventIncome);
      BigDecimal required = gap.max(BigDecimal.ZERO);
      BigDecimal surplus = gap.negate().max(BigDecimal.ZERO);
      var assetYear = longTerm.project(new LongTermAnnualProjectionApi.ProjectionRequest(
          year, reserve, required, bonds, assets.rentalIncome()));
      BigDecimal remaining = required.subtract(assetYear.reserveUsed()).subtract(assetYear.maturedFunding()).max(BigDecimal.ZERO);
      var investmentYear = investments.project(new InvestmentAnnualProjectionApi.ProjectionRequest(
          year, investmentValue, input.investmentReturnRate(), remaining, input.investmentSource()));
      BigDecimal unfunded = remaining.subtract(investmentYear.withdrawal()).max(BigDecimal.ZERO);
      years.add(new Year(age, year, retired, expenses, employment, pension, eventIncome, eventExpenses,
          preview.monthlyNetRentalIncome(), preview.netBondIncome(), gap, required, surplus,
          assetYear.reserveUsed(), assetYear.maturedFunding(), investmentYear.withdrawal(), unfunded,
          assetYear.reserveEnd(), investmentYear, assetYear.source()));
      reserve = assetYear.reserveEnd();
      investmentValue = investmentYear.endValue();
      bonds = assetYear.nextBonds();
      if (retired) spending = spending.multiply(BigDecimal.ONE.add(input.spendingGrowthRate()));
    }
    return new Result(years);
  }

  private static RetirementSimulationInput.LongTermYearInput assets(RetirementSimulationInput i, int year) {
    return i.longTermYears().stream().filter(a -> a.year() == year).findFirst()
        .orElse(new RetirementSimulationInput.LongTermYearInput(year, List.of(), List.of()));
  }

  private static BigDecimal events(RetirementSimulationInput i, int year, SimulationEventType type) {
    return i.events().stream().filter(e -> e.year() == year && e.type() == type)
        .map(SimulationEvent::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  public record Result(List<Year> years) { public Result { years = List.copyOf(years); } }

  public record Year(
      int age, int year, boolean retired, BigDecimal expenses, BigDecimal employmentIncome,
      BigDecimal pensionIncome, BigDecimal eventIncome, BigDecimal eventExpenses,
      BigDecimal monthlyNetRentalIncome, BigDecimal netBondIncome, BigDecimal incomeGap,
      BigDecimal requiredFunding, BigDecimal annualSurplus, BigDecimal reserveWithdrawal,
      BigDecimal maturedBondFunding, BigDecimal investmentWithdrawal, BigDecimal unfundedShortfall,
      BigDecimal reserveEnd, InvestmentAnnualProjectionApi.AnnualProjection investment,
      LongTermAnnualProjectionApi.Source longTermSource) {}
}
