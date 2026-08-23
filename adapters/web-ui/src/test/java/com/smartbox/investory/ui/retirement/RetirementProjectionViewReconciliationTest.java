package com.smartbox.investory.ui.retirement;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.retirement.planning.PlanningTimeline;
import com.smartbox.investory.retirement.planning.PlanningTimelineMoney;
import com.smartbox.investory.retirement.planning.PlanningTimelineState;
import com.smartbox.investory.retirement.planning.PlanningTimelineYear;
import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.simulation.SimulationAssumptions;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Presentation contract: all current-year views must reconcile to the same timeline money row. */
class RetirementProjectionViewReconciliationTest {

  @Test
  void cashFlowTimelineAndYearSummaryUseTheSameAuthoritativeYearValues() {
    var timeline = new PlanningTimeline(List.of(
        new PlanningTimelineYear(2026, 41, PlanningTimelineState.LIVE, null, null, null)));
    var money = new PlanningTimelineMoney(
        bd("262"), bd("214"), bd("175"), bd("39"), bd("48"),
        bd("48"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
        bd("52"), bd("900"), bd("575"),
        bd("100"), bd("52"), bd("900"), bd("900"), bd("575"), bd("575"),
        bd("3650"), bd("3650"), bd("48"), BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, bd("39"), bd("46"), BigDecimal.ZERO);
    var moneyByYear = Map.of(2026, money);
    var assumptions = SimulationAssumptions.defaults(profile(), 41, 80, 2026)
        .withRetirementAge(41)
        .withAnnualEmploymentIncome(BigDecimal.ZERO);

    var summaries = RetirementYearSummaryView.from(timeline, moneyByYear);
    var timelineView = PlanTimelineView.from(timeline, summaries, 2026, 2052, 2026);
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
  }

  private static InvestmentProfile profile() {
    return new InvestmentProfile(
        1L, CurrencyType.PLN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
        List.of(), List.of());
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }
}
