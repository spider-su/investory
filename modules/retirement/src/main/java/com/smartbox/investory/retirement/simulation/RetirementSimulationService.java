package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.investment.api.InvestmentAnnualProjectionApi;
import com.smartbox.investory.longterm.api.LongTermAnnualProjectionApi;
import com.smartbox.investory.longterm.api.model.InterestTreatmentModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetTypeModel;
import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.profile.ProjectedLongTermAsset;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Canonical retirement orchestrator. Asset mechanics remain behind public module APIs. */
@Service
public class RetirementSimulationService implements RetirementSimulation {
  private static final BigDecimal ZERO = BigDecimal.ZERO;
  private final LongTermAnnualProjectionApi longTerm;
  private final InvestmentAnnualProjectionApi investments;

  @Autowired
  public RetirementSimulationService(LongTermAnnualProjectionApi longTerm,
      InvestmentAnnualProjectionApi investments) {
    this.longTerm = longTerm;
    this.investments = investments;
  }

  @Override
  public SimulationResult simulate(InvestmentProfile profile, SimulationAssumptions assumptions,
      SimulationScenario scenario) { return simulate(profile, assumptions, scenario, false); }

  @Override
  public SimulationResult simulate(InvestmentProfile profile, SimulationAssumptions assumptions,
      SimulationScenario scenario, boolean actualRentalYear) {
    SimulationScenarioSettings settings = SimulationScenarioSettings.forScenario(scenario, assumptions);
    BigDecimal reserve = nz(profile.liquidAssets());
    BigDecimal investmentStart = nz(profile.marketPortfolioValue()).subtract(reserve).max(ZERO);
    List<LongTermAnnualProjectionApi.Bond> projectedBonds =
        bonds(profile, settings, assumptions.startYear());
    // Rental comes from the canonical Long-Term annual snapshot. Bond cash flow stays in the
    // instrument-level Long-Term projection input below; never reconstruct rental from aggregate income.
    BigDecimal annualRentalIncome = nz(profile.currentRentalIncome());
    var input = new RetirementSimulationInput(
        assumptions.currentAge(), assumptions.endAge(), assumptions.startYear(),
        assumptions.retirementAge(),
        assumptions.annualLivingExpenses().add(assumptions.annualDiscretionaryExpenses()),
        settings.spendingGrowthRate(), assumptions.annualPension(), assumptions.pensionStartAge(),
        assumptions.annualEmploymentIncome(), assumptions.annualPreRetirementContribution(),
        reserve, investmentStart, settings.equityReturnRate(),
        longTermInputs(
            annualRentalIncome,
            projectedBonds,
            settings.rentalIncomeGrowthRate(),
            assumptions.startYear(),
            assumptions.ageAtPlanStart(),
            assumptions.endAge(),
            actualRentalYear),
        assumptions.futureEvents(), InvestmentAnnualProjectionApi.Source.PROJECTED);
    var result = new RetirementSimulationOrchestrator(longTerm, investments).run(input);
    return map(result, scenario, assumptions, reserve, investmentStart);
  }

  @Override
  public Map<SimulationScenario, SimulationResult> compareScenarios(InvestmentProfile profile,
      SimulationAssumptions assumptions) {
    EnumMap<SimulationScenario, SimulationResult> results = new EnumMap<>(SimulationScenario.class);
    for (SimulationScenario scenario : SimulationScenario.values()) results.put(scenario, simulate(profile, assumptions, scenario));
    return results;
  }

  private static List<LongTermAnnualProjectionApi.Bond> bonds(
      InvestmentProfile profile, SimulationScenarioSettings settings, int year) {
    return profile.longTermAssets().stream()
        .filter(
            asset ->
                asset.type() == LongTermAssetTypeModel.BOND
                    || asset.type() == LongTermAssetTypeModel.DEPOSIT)
        .map(
            asset -> {
              BigDecimal netRate = netAnnualRate(asset, settings, year);
              BigDecimal payoutIncome =
                  asset.interestTreatment() == InterestTreatmentModel.PAY_OUT
                      ? nz(asset.currentValue()).multiply(netRate)
                      : ZERO;
              return new LongTermAnnualProjectionApi.Bond(
                  String.valueOf(asset.id()),
                  nz(asset.currentValue()),
                  asset.maturityDate(),
                  asset.redemptionValue(),
                  payoutIncome,
                  null,
                  3,
                  netRate);
            })
        .toList();
  }

  private static BigDecimal netAnnualRate(
      ProjectedLongTermAsset asset, SimulationScenarioSettings settings, int year) {
    BigDecimal grossRate =
        asset.periods().stream()
            .filter(period -> applies(period, year))
            .map(ProjectedLongTermAsset.Period::annualReturnRate)
            .findFirst()
            .orElse(settings.fixedIncomeReturnRate());
    return grossRate.multiply(BigDecimal.ONE.subtract(nz(asset.taxRate())));
  }

  private static boolean applies(ProjectedLongTermAsset.Period period, int year) {
    return period.validFrom().getYear() <= year
        && (period.validTo() == null || period.validTo().getYear() >= year);
  }

  private static List<RetirementSimulationInput.LongTermYearInput> longTermInputs(
      BigDecimal annualRentalIncome,
      List<LongTermAnnualProjectionApi.Bond> bonds,
      BigDecimal rentalIncomeGrowthRate,
      int startYear,
      int ageAtPlanStart,
      int endAge,
      boolean actualRentalYear) {
    int endYear = startYear + endAge - ageAtPlanStart;
    return java.util.stream.IntStream.rangeClosed(startYear, endYear)
        .mapToObj(
            year -> {
              var source =
                  actualRentalYear && year == startYear
                      ? LongTermAnnualProjectionApi.Source.ACTUAL
                      : LongTermAnnualProjectionApi.Source.PROJECTED;
              var rental =
                  List.of(
                      new LongTermAnnualProjectionApi.RentalIncome(
                          annualRentalIncome.divide(BigDecimal.valueOf(12), 12, RoundingMode.HALF_UP),
                          source,
                          startYear,
                          rentalIncomeGrowthRate));
              return new RetirementSimulationInput.LongTermYearInput(year, bonds, rental);
            })
        .toList();
  }

  private static SimulationResult map(RetirementSimulationOrchestrator.Result result, SimulationScenario scenario,
      SimulationAssumptions assumptions, BigDecimal initialReserve, BigDecimal initialInvestment) {
    BigDecimal reserveStart = initialReserve, investmentStart = initialInvestment;
    Integer failureAge = null; BigDecimal firstShortfall = ZERO, totalUnfunded = ZERO;
    var years = new java.util.ArrayList<SimulationYear>();
    for (var year : result.years()) {
      var investment = year.investment();
      BigDecimal expenses = year.expenses().add(year.eventExpenses());
      BigDecimal income = year.monthlyNetRentalIncome().multiply(BigDecimal.valueOf(12))
          .add(year.netBondIncome()).add(year.pensionIncome()).add(year.employmentIncome()).add(year.eventIncome());
      BigDecimal spendableEnd = year.reserveEnd().add(investment.endValue());
      if (year.unfundedShortfall().signum() > 0 && failureAge == null) { failureAge = year.age(); firstShortfall = year.unfundedShortfall(); }
      totalUnfunded = totalUnfunded.add(year.unfundedShortfall());
      years.add(SimulationYear.generic(
          year.age(), year.year(), year.retired(), year.expenses(), year.eventExpenses(),
          year.employmentIncome(), year.pensionIncome(), year.eventIncome(),
          annualRentalIncome(year.monthlyNetRentalIncome()), year.netBondIncome(),
          reserveStart, year.reserveWithdrawal().add(year.maturedBondFunding()), year.reserveEnd(),
          investmentStart, investment.annualReturnAmount(), year.investmentWithdrawal(), investment.endValue(),
          year.unfundedShortfall(),
          year.retired() ? ZERO : assumptions.annualPreRetirementContribution()));
      reserveStart = year.reserveEnd(); investmentStart = investment.endValue();
    }
    return new SimulationResult(scenario, failureAge != null, failureAge, firstShortfall, totalUnfunded, years);
  }

  private static BigDecimal nz(BigDecimal value) { return value == null ? ZERO : value; }

  private static BigDecimal annualRentalIncome(BigDecimal monthly) {
    return monthly.multiply(BigDecimal.valueOf(12)).setScale(8, RoundingMode.HALF_UP);
  }
}
