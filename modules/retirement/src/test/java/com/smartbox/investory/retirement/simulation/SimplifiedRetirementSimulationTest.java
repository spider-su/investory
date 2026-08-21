package com.smartbox.investory.retirement.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.investment.api.InvestmentAnnualProjectionApi;
import com.smartbox.investory.investment.application.InvestmentAnnualProjectionService;
import com.smartbox.investory.longterm.api.LongTermAnnualProjectionApi;
import com.smartbox.investory.longterm.api.MaturityStrategy;
import com.smartbox.investory.longterm.application.service.LongTermAnnualProjectionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimplifiedRetirementSimulationTest {
  private final LongTermAnnualProjectionApi longTerm = new LongTermAnnualProjectionService();
  private final InvestmentAnnualProjectionApi investments = new InvestmentAnnualProjectionService();

  @Test
  void calculatesSignedGapWithAllIncomeSourcesAndEvents() {
    var year = run(50, 51, 51, bd("100"), bd("500"), bd("200"), bd("1000"),
        List.of(new LongTermAnnualProjectionApi.RentalIncome(bd("50"), LongTermAnnualProjectionApi.Source.ACTUAL)),
        List.of(new LongTermAnnualProjectionApi.Bond("b", bd("100"), null, bd("100"), bd("50"), null, 3, bd(".01"))),
        List.of(new SimulationEvent(1L, 2026, "event income", bd("20"), SimulationEventType.ONE_OFF_INCOME, ""),
            new SimulationEvent(2L, 2026, "event expense", bd("30"), SimulationEventType.ONE_OFF_EXPENSE, "")));

    assertThat(year.incomeGap()).isEqualByComparingTo("-1340");
    assertThat(year.annualSurplus()).isEqualByComparingTo("1340");
    assertThat(year.employmentIncome()).isEqualByComparingTo("500");
    assertThat(year.pensionIncome()).isEqualByComparingTo("200");
  }

  @Test
  void employmentStopsAtRetirementAndSpendingStartsThenGrows() {
    var result = new SimplifiedRetirementSimulation(longTerm, investments).run(input(64, 66, 65, bd("100"), bd("100"), bd("0"), bd("0"), List.of(), List.of(), List.of()));

    assertThat(result.years().get(0).expenses()).isZero();
    assertThat(result.years().get(0).employmentIncome()).isEqualByComparingTo("100");
    assertThat(result.years().get(1).expenses()).isEqualByComparingTo("100");
    assertThat(result.years().get(1).employmentIncome()).isZero();
    assertThat(result.years().get(2).expenses()).isEqualByComparingTo("110");
  }

  @Test
  void reserveIsUsedBeforeInvestmentAndShortfallIsReported() {
    var result = run(65, 65, 65, bd("100"), bd("0"), bd("0"), bd("60"), List.of(), List.of(), List.of());
    var year = result;
    assertThat(year.requiredFunding()).isEqualByComparingTo("100");
    assertThat(year.reserveWithdrawal()).isEqualByComparingTo("60");
    assertThat(year.investmentWithdrawal()).isEqualByComparingTo("40");
    assertThat(year.unfundedShortfall()).isZero();
  }

  @Test
  void appliesEveryMaturityStrategyAndDefaultsToReinvest() {
    var reinvest = project(MaturityStrategy.REINVEST, bd("100"));
    assertThat(reinvest.maturedFunding()).isZero();
    assertThat(reinvest.reserveEnd()).isZero();
    assertThat(reinvest.nextBonds()).singleElement().satisfies(b ->
        assertThat(b.maturityDate()).isEqualTo(LocalDate.of(2029, 12, 31)));

    var reserve = project(MaturityStrategy.MOVE_TO_RESERVE, bd("50"));
    assertThat(reserve.reserveUsed()).isEqualByComparingTo("50");
    assertThat(reserve.reserveEnd()).isEqualByComparingTo("50");

    var gap = project(MaturityStrategy.FUND_GAP, bd("70"));
    assertThat(gap.maturedFunding()).isEqualByComparingTo("70");
    assertThat(gap.reserveEnd()).isEqualByComparingTo("30");

    var defaulted = project(null, bd("70"));
    assertThat(defaulted.maturedFunding()).isZero();
    assertThat(defaulted.nextBonds()).hasSize(1);
  }

  @Test
  void investmentReturnIsExcludedFromGapButChangesEndValue() {
    var result = run(65, 65, 65, bd("100"), bd("0"), bd("0"), bd("0"), List.of(), List.of(), List.of());
    var investment = new InvestmentAnnualProjectionService().project(
        new InvestmentAnnualProjectionApi.ProjectionRequest(2026, bd("1000"), bd(".10"), bd("100"), InvestmentAnnualProjectionApi.Source.ACTUAL));
    assertThat(result.incomeGap()).isEqualByComparingTo("100");
    assertThat(investment.annualReturnAmount()).isEqualByComparingTo("100");
    assertThat(investment.endValue()).isEqualByComparingTo("1000");
    assertThat(investment.source()).isEqualTo(InvestmentAnnualProjectionApi.Source.ACTUAL);
  }

  @Test
  void longTermSourceReportsActualWhenAnyRentalValueIsActual() {
    var result = run(65, 65, 65, bd("0"), bd("0"), bd("0"), bd("0"),
        List.of(new LongTermAnnualProjectionApi.RentalIncome(bd("1"), LongTermAnnualProjectionApi.Source.ACTUAL)), List.of(), List.of());
    assertThat(result.longTermSource()).isEqualTo(LongTermAnnualProjectionApi.Source.ACTUAL);
  }

  private LongTermAnnualProjectionApi.AnnualProjection project(MaturityStrategy strategy, BigDecimal funding) {
    return longTerm.project(new LongTermAnnualProjectionApi.ProjectionRequest(2026, bd("0"), funding,
        List.of(new LongTermAnnualProjectionApi.Bond("bond", bd("100"), LocalDate.of(2026, 1, 1), bd("100"), bd("0"), strategy, 3, bd(".02"))), List.of()));
  }

  private SimplifiedRetirementSimulation.Year run(int age, int endAge, int retirementAge, BigDecimal expenses,
      BigDecimal employment, BigDecimal pension, BigDecimal reserve, List<LongTermAnnualProjectionApi.RentalIncome> rent,
      List<LongTermAnnualProjectionApi.Bond> bonds, List<SimulationEvent> events) {
    return new SimplifiedRetirementSimulation(longTerm, investments).run(
        input(age, endAge, retirementAge, expenses, employment, pension, reserve, rent, bonds, events)).years().getFirst();
  }

  private RetirementSimulationInput input(int age, int endAge, int retirementAge, BigDecimal expenses,
      BigDecimal employment, BigDecimal pension, BigDecimal reserve, List<LongTermAnnualProjectionApi.RentalIncome> rent,
      List<LongTermAnnualProjectionApi.Bond> bonds, List<SimulationEvent> events) {
    return new RetirementSimulationInput(age, endAge, 2026, retirementAge, expenses, bd(".10"), pension, age,
        employment, reserve, bd("0"), bd("0"), List.of(new RetirementSimulationInput.LongTermYearInput(2026, bonds, rent)), events,
        InvestmentAnnualProjectionApi.Source.PROJECTED);
  }

  private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
