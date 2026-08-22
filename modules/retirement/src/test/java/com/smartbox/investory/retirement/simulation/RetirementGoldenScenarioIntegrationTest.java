package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.investment.application.InvestmentAnnualProjectionService;
import com.smartbox.investory.longterm.api.model.InterestTreatmentModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetTypeModel;
import com.smartbox.investory.longterm.application.service.LongTermAnnualProjectionService;
import com.smartbox.investory.retirement.profile.EconomicBucket;
import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.profile.Liquidity;
import com.smartbox.investory.retirement.profile.ProjectedLongTermAsset;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Approved cross-module financial contract. Do not refresh constants from production output. */
class RetirementGoldenScenarioIntegrationTest {
  @Test
  void printBaseline() {
    var service = new RetirementSimulationService(new LongTermAnnualProjectionService(),
        new InvestmentAnnualProjectionService());
    for (int endAge : List.of(60, 80)) {
      var result = service.simulate(fixture(), assumptions(endAge), SimulationScenario.BASE);
      System.out.println("GOLDEN endAge=" + endAge + " failed=" + result.simulationFailed()
          + " failureAge=" + result.failureAge() + " totalUnfunded=" + result.totalUnfundedAmount());
      result.years().forEach(y -> {
        if (List.of(2025, 2026, 2027, 2035, 2045, 2055, 2065).contains(y.year())
            || y.unfundedAmount().signum() > 0)
          System.out.println(y.year() + " age=" + y.age() + " costs=" + y.totalExpenses()
              + " income=" + y.totalIncome() + " gap=" + y.requiredPortfolioFunding()
              + " rent=" + y.rentalIncome() + " bond=" + y.bondIncome()
              + " reserve=" + y.cashEnd() + " inv=" + y.equityEnd()
              + " unfunded=" + y.unfundedAmount());
      });
    }
  }

  private static SimulationAssumptions assumptions(int endAge) {
    return SimulationAssumptions.defaults(fixture(), 40, endAge, 2025)
        .withRecurringSpending(new BigDecimal("240000"))
        .withInflationRate(new BigDecimal("0.025"))
        .withSpendingGrowthSpread(new BigDecimal("-0.010"))
        .withRentalIncomeGrowthSpread(new BigDecimal("-0.015"))
        .withEquityReturnRate(new BigDecimal("0.085"))
        .withAnnualEmploymentIncome(new BigDecimal("120000"))
        .withAnnualPreRetirementContribution(new BigDecimal("24000"))
        .withAnnualPension(new BigDecimal("7000"))
        .withRetirementAge(42)
        .withPensionStartAge(67)
        .withExpenseProfile(new ExpenseProfile(List.of(
            new ExpenseProfileStep(0, new BigDecimal("1.00")),
            new ExpenseProfileStep(10, new BigDecimal("1.00")),
            new ExpenseProfileStep(20, new BigDecimal("0.85")),
            new ExpenseProfileStep(30, new BigDecimal("0.75")))));
  }

  private static InvestmentProfile fixture() {
    var bond = new ProjectedLongTermAsset(10L, "Golden bond", LongTermAssetTypeModel.BOND,
        EconomicBucket.FIXED_INCOME, CurrencyType.PLN, new BigDecimal("486000"), Liquidity.LIQUID,
        List.of(new ProjectedLongTermAsset.Period(LocalDate.of(2020, 1, 1), null,
            new BigDecimal("38880"), BigDecimal.ZERO, new BigDecimal("0.10"))),
        List.of(), LocalDate.of(2028, 12, 31), new BigDecimal("486000"),
        InterestTreatmentModel.PAY_OUT, new BigDecimal("0.20"), null, false);
    var rental = new ProjectedLongTermAsset(11L, "Golden rental", LongTermAssetTypeModel.REAL_ESTATE,
        EconomicBucket.REAL_ESTATE, CurrencyType.PLN, new BigDecimal("3000000"), Liquidity.ILLIQUID,
        List.of(new ProjectedLongTermAsset.Period(LocalDate.of(2020, 1, 1), null,
            new BigDecimal("174803.62"), BigDecimal.ZERO, BigDecimal.ZERO)),
        List.of(), null, null, InterestTreatmentModel.PAY_OUT, BigDecimal.ZERO, null, false);
    return new InvestmentProfile(1L, CurrencyType.PLN, new BigDecimal("1100000"),
        new BigDecimal("3486000"), new BigDecimal("4586000"), BigDecimal.ZERO,
        new BigDecimal("213683.62"), BigDecimal.ZERO, new BigDecimal("100000"),
        new BigDecimal("3000000"), List.of(), List.of(rental, bond),
        new BigDecimal("174803.62"), new BigDecimal("38880"));
  }
}
