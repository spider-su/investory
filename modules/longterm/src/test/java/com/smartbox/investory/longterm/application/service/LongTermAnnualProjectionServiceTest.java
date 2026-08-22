package com.smartbox.investory.longterm.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.longterm.api.LongTermAnnualProjectionApi;
import com.smartbox.investory.longterm.api.MaturityStrategy;
import com.smartbox.investory.longterm.api.model.CashFlowTypeModel;
import com.smartbox.investory.longterm.api.model.InterestTreatmentModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProjectionModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetTypeModel;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LongTermAnnualProjectionServiceTest {
  @Test
  void quoteIsNonConsumingAndMaturityIsConservedOnCommit() {
    var bond = new LongTermAssetProjectionModel(9L, "Maturity", LongTermAssetTypeModel.BOND,
        CurrencyType.USD, new BigDecimal("100000"), List.of(), LocalDate.of(2030, 12, 31),
        new BigDecimal("100000"), InterestTreatmentModel.PAY_OUT, BigDecimal.ZERO);
    var state = new LongTermAnnualProjectionApi.PlanningState(List.of(bond), BigDecimal.ZERO, 2030,
        LongTermAnnualProjectionApi.Source.PROJECTED);
    var service = new LongTermAnnualProjectionService();
    var request = new LongTermAnnualProjectionApi.PlanningRequest(2030, new BigDecimal("60000"), state);
    var first = service.quote(request);
    var second = service.quote(request);
    assertThat(first).isEqualTo(second);
    assertThat(first.capitalAvailable()).isEqualByComparingTo("100000");
    var committed = service.plan(request);
    assertThat(committed.actualCapitalProvided()).isEqualByComparingTo("60000");
    assertThat(committed.endCapital()).isEqualByComparingTo("40000");
    assertThat(committed.endState().assets()).singleElement()
        .extracting(LongTermAssetProjectionModel::currentValue)
        .isEqualTo(new BigDecimal("40000"));
    var retained = service.plan(new LongTermAnnualProjectionApi.PlanningRequest(2030, BigDecimal.ZERO, state));
    assertThat(retained.actualCapitalProvided()).isZero();
    assertThat(retained.endCapital()).isEqualByComparingTo("100000");
  }
  @Test
  void carriesRentalForwardAtEffectiveInflationPlusSpreadGrowth() {
    var asset =
        new LongTermAssetProjectionModel(
            1L,
            "Rental",
            LongTermAssetTypeModel.REAL_ESTATE,
            CurrencyType.USD,
            BigDecimal.ZERO,
            List.of(
                new LongTermAssetProjectionModel.Period(
                    LocalDate.of(2026, 1, 1),
                    null,
                    new BigDecimal("174804"),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    CashFlowTypeModel.RENT,
                    false)),
            List.of(),
            null,
            null,
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            false);
    var state =
        new LongTermAnnualProjectionApi.PlanningState(
            List.of(asset), new BigDecimal("0.01"), 2026, LongTermAnnualProjectionApi.Source.PROJECTED);
    var service = new LongTermAnnualProjectionService();

    var current = service.plan(new LongTermAnnualProjectionApi.PlanningRequest(2026, BigDecimal.ZERO, state));
    var firstFuture =
        service.plan(
            new LongTermAnnualProjectionApi.PlanningRequest(
                2027, BigDecimal.ZERO, current.endState()));
    var secondFuture =
        service.plan(
            new LongTermAnnualProjectionApi.PlanningRequest(
                2028, BigDecimal.ZERO, firstFuture.endState()));

    assertThat(rental(current)).isEqualByComparingTo("174804");
    assertThat(rental(firstFuture)).isEqualByComparingTo("176552.04");
    assertThat(rental(secondFuture)).isEqualByComparingTo("178317.5604");
    assertThat(rental(firstFuture)).isPositive();
    assertThat(rental(secondFuture)).isPositive();
  }

  @Test
  void preservesDeclaredNetBondIncomeInPlanningCashFlows() {
    var bond =
        new LongTermAssetProjectionModel(
            2L,
            "Bond",
            LongTermAssetTypeModel.BOND,
            CurrencyType.USD,
            new BigDecimal("1000"),
            List.of(
                new LongTermAssetProjectionModel.Period(
                    LocalDate.of(2026, 1, 1), null, new BigDecimal("38.88"), BigDecimal.ZERO,
                    new BigDecimal("0.10"))),
            LocalDate.of(2030, 12, 31),
            new BigDecimal("1000"),
            InterestTreatmentModel.PAY_OUT,
            new BigDecimal("0.20"));
    var state =
        new LongTermAnnualProjectionApi.PlanningState(
            List.of(bond), BigDecimal.ZERO, 2026, LongTermAnnualProjectionApi.Source.PROJECTED);

    var projection =
        new LongTermAnnualProjectionService()
            .plan(new LongTermAnnualProjectionApi.PlanningRequest(2027, BigDecimal.ZERO, state));

    assertThat(projection.plannedCashFlows()).anySatisfy(
        flow -> {
          assertThat(flow.kind()).isEqualTo(LongTermAnnualProjectionApi.CashFlowKind.FIXED_INCOME);
          assertThat(flow.annualAmount()).isEqualByComparingTo("38.88");
        });
  }

  @Test
  void projectsInterestForSixActivePayoutBondsFromFrozenState() {
    var bonds = java.util.stream.IntStream.range(0, 6)
        .mapToObj(i -> new LongTermAssetProjectionModel(
            (long) i, "Bond " + i, LongTermAssetTypeModel.BOND, CurrencyType.USD,
            new BigDecimal("150000"),
            List.of(new LongTermAssetProjectionModel.Period(
                LocalDate.of(2026, 1, 1), null, new BigDecimal("8000"), BigDecimal.ZERO,
                BigDecimal.ZERO)),
            LocalDate.of(2028, 12, 31), new BigDecimal("150000"),
            InterestTreatmentModel.PAY_OUT, BigDecimal.ZERO))
        .toList();
    var state = new LongTermAnnualProjectionApi.PlanningState(
        bonds, BigDecimal.ZERO, 2026, LongTermAnnualProjectionApi.Source.PROJECTED);

    var quote = new LongTermAnnualProjectionService().quote(
        new LongTermAnnualProjectionApi.PlanningRequest(2027, BigDecimal.ZERO, state));

    assertThat(quote.plannedCashFlows())
        .filteredOn(flow -> flow.kind() == LongTermAnnualProjectionApi.CashFlowKind.FIXED_INCOME)
        .extracting(LongTermAnnualProjectionApi.PlannedCashFlow::annualAmount)
        .containsExactlyInAnyOrder(
            new BigDecimal("8000"), new BigDecimal("8000"), new BigDecimal("8000"),
            new BigDecimal("8000"), new BigDecimal("8000"), new BigDecimal("8000"));
    assertThat(quote.plannedCashFlows().stream()
        .filter(flow -> flow.kind() == LongTermAnnualProjectionApi.CashFlowKind.FIXED_INCOME)
        .map(LongTermAnnualProjectionApi.PlannedCashFlow::annualAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("48000");
  }

  @Test
  void compoundsCapitalizedBondInterestIntoTheCarriedLongTermCapital() {
    var bond =
        new LongTermAssetProjectionModel(
            3L,
            "Capitalized bond",
            LongTermAssetTypeModel.BOND,
            CurrencyType.USD,
            new BigDecimal("1000"),
            List.of(
                new LongTermAssetProjectionModel.Period(
                    LocalDate.of(2026, 1, 1), null, BigDecimal.ZERO, BigDecimal.ZERO,
                    new BigDecimal("0.10"))),
            LocalDate.of(2030, 12, 31),
            new BigDecimal("1000"),
            InterestTreatmentModel.CAPITALIZE,
            BigDecimal.ZERO);
    var state =
        new LongTermAnnualProjectionApi.PlanningState(
            List.of(bond), BigDecimal.ZERO, 2026, LongTermAnnualProjectionApi.Source.PROJECTED);

    var first =
        new LongTermAnnualProjectionService()
            .plan(new LongTermAnnualProjectionApi.PlanningRequest(2027, BigDecimal.ZERO, state));
    var second =
        new LongTermAnnualProjectionService()
            .plan(new LongTermAnnualProjectionApi.PlanningRequest(2028, BigDecimal.ZERO, first.endState()));

    assertThat(first.plannedCashFlows()).noneMatch(
        flow -> flow.kind() == LongTermAnnualProjectionApi.CashFlowKind.FIXED_INCOME);
    assertThat(first.endCapital()).isEqualByComparingTo("1100");
    assertThat(second.endCapital()).isEqualByComparingTo("1210");
  }

  @Test
  void keepsMoveToReserveRedemptionSeparateFromDirectGapFunding() {
    var moveToReserve =
        new LongTermAnnualProjectionApi.Bond(
            "reserve-bond", new BigDecimal("100000"), LocalDate.of(2026, 12, 31),
            new BigDecimal("100000"), BigDecimal.ZERO, MaturityStrategy.MOVE_TO_RESERVE,
            3, BigDecimal.ZERO);
    var service = new LongTermAnnualProjectionService();
    var moved = service.project(new LongTermAnnualProjectionApi.ProjectionRequest(
        2026, new BigDecimal("746254.69"), new BigDecimal("63421.58"),
        List.of(moveToReserve), List.of()));
    assertThat(moved.reserveAfterMaturities()).isEqualByComparingTo("846254.69");
    assertThat(moved.reserveUsed()).isEqualByComparingTo("63421.58");
    assertThat(moved.reserveEnd()).isEqualByComparingTo("782833.11");
    assertThat(moved.maturedFunding()).isZero();

    var fundGap = new LongTermAnnualProjectionApi.Bond(
        "gap-bond", new BigDecimal("100000"), LocalDate.of(2026, 12, 31),
        new BigDecimal("100000"), BigDecimal.ZERO, MaturityStrategy.FUND_GAP, 3, BigDecimal.ZERO);
    var direct = service.project(new LongTermAnnualProjectionApi.ProjectionRequest(
        2026, BigDecimal.ZERO, new BigDecimal("63421.58"), List.of(fundGap), List.of()));
    assertThat(direct.reserveAfterMaturities()).isZero();
    assertThat(direct.reserveUsed()).isZero();
    assertThat(direct.maturedFunding()).isEqualByComparingTo("63421.58");
  }

  private static BigDecimal rental(LongTermAnnualProjectionApi.PlanningProjection projection) {
    return projection.plannedCashFlows().stream()
        .filter(flow -> flow.kind() == LongTermAnnualProjectionApi.CashFlowKind.RENTAL_INCOME)
        .map(LongTermAnnualProjectionApi.PlannedCashFlow::annualAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
