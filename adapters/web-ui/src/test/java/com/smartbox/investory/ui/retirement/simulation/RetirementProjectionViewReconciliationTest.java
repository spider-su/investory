package com.smartbox.investory.ui.retirement.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.PlanningTimeline;
import com.smartbox.investory.retirement.api.model.PlanningTimelineMoney;
import com.smartbox.investory.retirement.api.model.PlanningTimelineState;
import com.smartbox.investory.retirement.api.model.PlanningTimelineYear;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Presentation contract: all current-year views must reconcile to the same timeline money row. */
@DisplayName("Retirement Projection View Reconciliation")
class RetirementProjectionViewReconciliationTest {

  @DisplayName("cash Flow Timeline And Year Summary Use The Same Authoritative Year Values")
  @Test
  void cashFlowTimelineAndYearSummaryUseTheSameAuthoritativeYearValues() {
    var timeline =
        new PlanningTimeline(
            List.of(
                new PlanningTimelineYear(2026, 41, PlanningTimelineState.LIVE, null, null, null)));
    var money =
        new PlanningTimelineMoney(
            bd("262"),
            bd("214"),
            bd("175"),
            bd("39"),
            bd("48"),
            bd("48"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            bd("52"),
            bd("900"),
            bd("575"),
            bd("100"),
            bd("52"),
            bd("900"),
            bd("900"),
            bd("575"),
            bd("575"),
            bd("3650"),
            bd("3650"),
            bd("48"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            bd("39"),
            bd("46"),
            BigDecimal.ZERO);
    var moneyByYear = Map.of(2026, money);
    var assumptions =
        SimulationAssumptions.defaults(profile(), 41, 80, 2026)
            .withRetirementAge(41)
            .withAnnualEmploymentIncome(BigDecimal.ZERO);

    var summaries = RetirementYearSummaryView.from(timeline, moneyByYear);
    var timelineView =
        PlanTimelineView.from(timeline, summaries, moneyByYear, assumptions, 2026, 2052, 2026);
    var cashFlow = CashFlowSectionView.from(timeline, moneyByYear, assumptions);
    var summary = summaries.get(2026);
    var snapshot = timelineView.years().getFirst().summary();

    assertThat(cashFlow).isNotNull();
    assertThat(cashFlow.spending()).isEqualByComparingTo(summary.spending());
    assertThat(cashFlow.cashIncome()).isEqualByComparingTo(summary.income());
    assertThat(cashFlow.fundingRequired()).isEqualByComparingTo("48");
    assertThat(cashFlow.capitalFunding()).isEqualByComparingTo("48");
    assertThat(cashFlow.totalFunded()).isEqualByComparingTo("262");

    assertThat(snapshot.spending()).isEqualByComparingTo(summary.spending());
    assertThat(snapshot.income()).isEqualByComparingTo(summary.income());
    assertThat(snapshot.cash().startValue()).isEqualByComparingTo("100");
    assertThat(snapshot.cash().endValue()).isEqualByComparingTo("52");
    assertThat(snapshot.bonds().endValue()).isEqualByComparingTo("900");
    assertThat(snapshot.equities().endValue()).isEqualByComparingTo("575");
    assertThat(snapshot.realEstate().endValue()).isEqualByComparingTo("3650");
    assertThat(timelineView.years().getFirst().incomeSources())
        .extracting(CashFlowFlowView::source)
        .containsExactly("Rents", "Bond cash income", "Bonds", "Equities");
    assertThat(timelineView.years().getFirst().fundingSources())
        .extracting(CashFlowFlowView::source)
        .containsExactly("Cash");
    assertThat(timelineView.years().getFirst().capitalFunding()).isEqualByComparingTo("48");
    assertThat(timelineView.years().getFirst().totalFunded()).isEqualByComparingTo("262");
  }

  private static InvestmentProfile profile() {
    return new InvestmentProfile(
        1L,
        CurrencyType.PLN,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        List.of(),
        null,
        null,
        new com.smartbox.investory.profile.api.model.ProfileAssetProjection(
            List.of(),
            java.math.BigDecimal.ZERO,
            0,
            com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
        (BigDecimal.ZERO == null ? java.math.BigDecimal.ZERO : BigDecimal.ZERO),
        BigDecimal.ZERO
            .subtract((BigDecimal.ZERO == null ? java.math.BigDecimal.ZERO : BigDecimal.ZERO))
            .max(java.math.BigDecimal.ZERO),
        com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO),
        com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }
}
