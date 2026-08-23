package com.smartbox.investory.retirement.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Lifecycle boundary contract for contributions, retirement, pension and one-off events. */
class RetirementLifecycleCashFlowIntegrationTest {

  @Test
  void contributionsStopAtRetirementAndEventsApplyAtLifecycleBoundaries() {
    var service = new RetirementSimulationService();
    var profile = profile();
    var base =
        SimulationAssumptions.defaults(profile, 60, 67, 2026)
            .withRecurringSpending(bd("500"))
            .withInflationRate(BigDecimal.ZERO)
            .withSpendingGrowthSpread(BigDecimal.ZERO)
            .withRentalIncomeGrowthSpread(BigDecimal.ZERO)
            .withFixedIncomeReturnRate(BigDecimal.ZERO)
            .withEquityReturnRate(BigDecimal.ZERO)
            .withRetirementAge(62)
            .withAnnualEmploymentIncome(bd("1000"))
            .withAnnualPreRetirementContribution(bd("100"))
            .withAnnualPension(bd("300"))
            .withPensionStartAge(65);
    var assumptions =
        base.rebasedTo(
            60,
            2026,
            List.of(
                event(2028, "Retirement expense", "200", SimulationEventType.ONE_OFF_EXPENSE),
                event(2028, "Retirement gift", "50", SimulationEventType.ONE_OFF_INCOME),
                event(2031, "Pension-year income", "70", SimulationEventType.ONE_OFF_INCOME),
                event(2033, "Final-year expense", "100", SimulationEventType.ONE_OFF_EXPENSE)));

    var result = service.simulate(profile, assumptions, SimulationScenario.BASE);

    var lastWorking = year(result, 2027);
    assertThat(lastWorking.age()).isEqualTo(61);
    assertThat(lastWorking.employmentIncome()).isEqualByComparingTo("1000");
    assertThat(lastWorking.preRetirementContribution()).isEqualByComparingTo("100");
    assertThat(lastWorking.equityEnd()).isEqualByComparingTo("1200");

    var retirement = year(result, 2028);
    assertThat(retirement.age()).isEqualTo(62);
    assertThat(retirement.employmentIncome()).isZero();
    assertThat(retirement.preRetirementContribution()).isZero();
    assertThat(retirement.equityStart()).isEqualByComparingTo(lastWorking.equityEnd());
    assertThat(retirement.eventExpenses()).isEqualByComparingTo("200");
    assertThat(retirement.eventIncome()).isEqualByComparingTo("50");
    assertThat(retirement.totalExpenses()).isEqualByComparingTo("700");
    assertThat(retirement.totalIncome()).isEqualByComparingTo("50");
    assertThat(retirement.requiredPortfolioFunding()).isEqualByComparingTo("650");

    var pension = year(result, 2031);
    assertThat(pension.age()).isEqualTo(65);
    assertThat(pension.pensionIncome()).isEqualByComparingTo("300");
    assertThat(pension.eventIncome()).isEqualByComparingTo("70");
    assertThat(pension.totalIncome()).isEqualByComparingTo("370");
    assertThat(pension.totalExpenses()).isEqualByComparingTo("500");

    var finalYear = year(result, 2033);
    assertThat(finalYear.age()).isEqualTo(67);
    assertThat(finalYear.eventExpenses()).isEqualByComparingTo("100");
    assertThat(finalYear.totalExpenses()).isEqualByComparingTo("600");
    assertThat(finalYear.pensionIncome()).isEqualByComparingTo("300");
  }

  private static SimulationYear year(SimulationResult result, int year) {
    return result.years().stream().filter(row -> row.year() == year).findFirst().orElseThrow();
  }

  private static SimulationEvent event(
      int year, String name, String amount, SimulationEventType type) {
    return new SimulationEvent(null, year, name, bd(amount), type, null);
  }

  private static InvestmentProfile profile() {
    return new InvestmentProfile(
        1L,
        CurrencyType.PLN,
        bd("11000"),
        BigDecimal.ZERO,
        bd("11000"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        bd("10000"),
        BigDecimal.ZERO,
        List.of(),
        List.of(),
        BigDecimal.ZERO,
        BigDecimal.ZERO);
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }
}
