package com.smartbox.investory.retirement.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.investment.api.InvestmentAnnualProjectionApi;
import com.smartbox.investory.longterm.api.LongTermAnnualProjectionApi;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetirementSimulationOrchestratorTest {
  @Test
  void usesFixedFundingOrderAndCarriesProviderEndStates() {
    List<BigDecimal> longTermRequests = new ArrayList<>();
    List<BigDecimal> investmentRequests = new ArrayList<>();
    LongTermAnnualProjectionApi longTerm =
        longTerm(
            request -> {
              longTermRequests.add(request.requestedCapital());
              return new LongTermAnnualProjectionApi.PlanningProjection(
                  request.year(),
                  List.of(
                      new LongTermAnnualProjectionApi.PlannedCashFlow(
                          "rent",
                          "rent",
                          LongTermAnnualProjectionApi.CashFlowKind.RENTAL_INCOME,
                          bd("0"),
                          LongTermAnnualProjectionApi.Source.PROJECTED)),
                  bd("0"),
                  request.requestedCapital(),
                  request.requestedCapital().min(bd("30")),
                  bd("70"),
                  request.state(),
                  LongTermAnnualProjectionApi.Source.PROJECTED);
            });
    InvestmentAnnualProjectionApi investment =
        request -> {
          investmentRequests.add(request.withdrawal());
          return new InvestmentAnnualProjectionApi.AnnualProjection(
              request.year(),
              request.startValue(),
              request.externalContribution(),
              bd("7"),
              request.withdrawal().min(bd("40")),
              bd("67"),
              request.source());
        };
    var year =
        new RetirementSimulationOrchestrator(longTerm, investment)
            .run(input(65, 65, bd("100"), bd("20")))
            .years()
            .getFirst();
    assertThat(longTermRequests)
        .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .containsExactly(bd("0"), bd("80"));
    assertThat(investmentRequests)
        .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .containsExactly(bd("50"));
    assertThat(year.reserveWithdrawal()).isEqualByComparingTo("20");
    assertThat(year.maturedBondFunding()).isEqualByComparingTo("30");
    assertThat(year.investmentWithdrawal()).isEqualByComparingTo("40");
    assertThat(year.unfundedShortfall()).isEqualByComparingTo("10");
    assertThat(year.reserveEnd()).isZero();
    assertThat(year.investment().endValue()).isEqualByComparingTo("67");
    assertThat(year.requiredFunding())
        .isEqualByComparingTo(
            year.reserveWithdrawal()
                .add(year.maturedBondFunding())
                .add(year.investmentWithdrawal())
                .add(year.unfundedShortfall()));
    assertThat(year.reserveEnd())
        .isEqualByComparingTo(
            bd("20").add(year.reserveTransfer()).subtract(year.reserveWithdrawal()));
  }

  @Test
  void surplusDoesNotRefillReserveOrRequestCapital() {
    List<BigDecimal> requests = new ArrayList<>();
    LongTermAnnualProjectionApi longTerm =
        longTerm(
            request -> {
              requests.add(request.requestedCapital());
              return new LongTermAnnualProjectionApi.PlanningProjection(
                  request.year(),
                  List.of(),
                  bd("0"),
                  request.requestedCapital(),
                  BigDecimal.ZERO,
                  BigDecimal.ZERO,
                  request.state(),
                  LongTermAnnualProjectionApi.Source.PROJECTED);
            });
    InvestmentAnnualProjectionApi investment =
        request ->
            new InvestmentAnnualProjectionApi.AnnualProjection(
                request.year(),
                request.startValue(),
                request.externalContribution(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                request.startValue(),
                request.source());
    var result =
        new RetirementSimulationOrchestrator(longTerm, investment)
            .run(input(60, 65, bd("0"), bd("20"), bd("100")))
            .years()
            .getFirst();
    assertThat(result.annualSurplus()).isEqualByComparingTo("100");
    assertThat(result.reserveEnd()).isEqualByComparingTo("20");
    assertThat(requests.subList(0, 2)).containsExactly(BigDecimal.ZERO, BigDecimal.ZERO);
    assertThat(result.investmentWithdrawal()).isZero();
  }

  @Test
  void lifecycleOnlyChangesFlowsAndSpendingStartsAtRetirementBaseline() {
    LongTermAnnualProjectionApi longTerm =
        longTerm(
            request ->
                new LongTermAnnualProjectionApi.PlanningProjection(
                    request.year(),
                    List.of(),
                    BigDecimal.ZERO,
                    request.requestedCapital(),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    request.state(),
                    LongTermAnnualProjectionApi.Source.PROJECTED));
    InvestmentAnnualProjectionApi investment =
        request ->
            new InvestmentAnnualProjectionApi.AnnualProjection(
                request.year(),
                request.startValue(),
                request.externalContribution(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                request.startValue(),
                request.source());
    var years =
        new RetirementSimulationOrchestrator(longTerm, investment)
            .run(input(64, 66, bd("100"), BigDecimal.ZERO, bd("100")))
            .years();
    assertThat(years.get(0).expenses()).isZero();
    assertThat(years.get(0).employmentIncome()).isEqualByComparingTo("100");
    assertThat(years.get(1).expenses()).isEqualByComparingTo("100");
    assertThat(years.get(1).employmentIncome()).isZero();
    assertThat(years.get(2).expenses()).isEqualByComparingTo("110");
  }

  @Test
  void firstYearFundingAndInvestmentValuesFollowTheDisplayedIdentities() {
    LongTermAnnualProjectionApi longTerm =
        longTerm(
            request ->
                new LongTermAnnualProjectionApi.PlanningProjection(
                    request.year(),
                    List.of(
                        new LongTermAnnualProjectionApi.PlannedCashFlow(
                            "rent",
                            "rent",
                            LongTermAnnualProjectionApi.CashFlowKind.RENTAL_INCOME,
                            bd("174804"),
                            LongTermAnnualProjectionApi.Source.ACTUAL),
                        new LongTermAnnualProjectionApi.PlannedCashFlow(
                            "bond",
                            "bond",
                            LongTermAnnualProjectionApi.CashFlowKind.FIXED_INCOME,
                            bd("38880"),
                            LongTermAnnualProjectionApi.Source.ACTUAL)),
                    BigDecimal.ZERO,
                    request.requestedCapital(),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    request.state(),
                    LongTermAnnualProjectionApi.Source.ACTUAL));
    InvestmentAnnualProjectionApi investment =
        request -> {
          BigDecimal annualReturn = request.startValue().multiply(request.annualReturnRate());
          return new InvestmentAnnualProjectionApi.AnnualProjection(
              request.year(),
              request.startValue(),
              request.externalContribution(),
              annualReturn,
              BigDecimal.ZERO,
              request.startValue().add(request.externalContribution()).add(annualReturn),
              request.source());
        };

    var year =
        new RetirementSimulationOrchestrator(longTerm, investment)
            .run(
                new RetirementSimulationInput(
                    65,
                    65,
                    2027,
                    65,
                    bd("240000"),
                    bd(".04"),
                    BigDecimal.ZERO,
                    65,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    bd("710364"),
                    bd("8614"),
                    bd(".085"),
                    List.of(),
                    InvestmentAnnualProjectionApi.Source.PROJECTED,
                    ExpenseProfile.EMPTY,
                    LongTermAnnualProjectionApi.PlanningState.EMPTY))
            .years()
            .getFirst();

    assertThat(year.incomeGap()).isEqualByComparingTo("26316");
    assertThat(year.reserveWithdrawal()).isEqualByComparingTo("26316");
    assertThat(year.reserveEnd()).isEqualByComparingTo("684048");
    assertThat(year.investment().annualReturnAmount()).isEqualByComparingTo("732.190");
    assertThat(year.investment().endValue()).isEqualByComparingTo("9346.190");
    assertThat(year.requiredFunding()).isEqualByComparingTo("26316");
  }

  @Test
  void aggregatesAllIncomeBeforeCalculatingTheFundingGap() {
    SimulationYear year =
        SimulationYear.generic(
            64,
            2026,
            false,
            bd("240000"),
            BigDecimal.ZERO,
            bd("120000"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            bd("174804"),
            bd("38880"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO);

    assertThat(year.totalIncome()).isEqualByComparingTo("333684");
    assertThat(year.totalIncome().subtract(year.totalExpenses())).isEqualByComparingTo("93684");
    assertThat(year.funding().fundingGap()).isZero();
  }

  private static RetirementSimulationInput input(
      int age, int endAge, BigDecimal expenses, BigDecimal reserve) {
    return input(age, endAge, expenses, reserve, BigDecimal.ZERO);
  }

  private static RetirementSimulationInput input(
      int age, int endAge, BigDecimal expenses, BigDecimal reserve, BigDecimal employment) {
    return new RetirementSimulationInput(
        age,
        endAge,
        2026,
        65,
        expenses,
        bd(".10"),
        BigDecimal.ZERO,
        Integer.MAX_VALUE,
        employment,
        BigDecimal.ZERO,
        reserve,
        bd("100"),
        bd(".07"),
        List.of(),
        InvestmentAnnualProjectionApi.Source.PROJECTED,
        ExpenseProfile.EMPTY,
        LongTermAnnualProjectionApi.PlanningState.EMPTY);
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }

  private static LongTermAnnualProjectionApi longTerm(
      java.util.function.Function<
              LongTermAnnualProjectionApi.PlanningRequest,
              LongTermAnnualProjectionApi.PlanningProjection>
          projection) {
    return new LongTermAnnualProjectionApi() {
      @Override
      public AnnualProjection project(ProjectionRequest request) {
        throw new UnsupportedOperationException("legacy projection is not used");
      }

      @Override
      public PlanningProjection plan(PlanningRequest request) {
        return projection.apply(request);
      }
    };
  }
}
