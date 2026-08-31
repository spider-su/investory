package com.smartbox.investory.retirement.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.ExpenseProfile;
import com.smartbox.investory.retirement.api.model.ProjectedIncomePolicy;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.retirement.api.model.SimulationChartData;
import com.smartbox.investory.retirement.api.model.SimulationFunding;
import com.smartbox.investory.retirement.api.model.SimulationFundingStrategy;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import com.smartbox.investory.retirement.api.model.SimulationYear;
import com.smartbox.investory.retirement.api.model.SustainableSpendingAnalysis;
import com.smartbox.investory.retirement.api.model.SustainableSpendingResultState;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("Planning Currency Presentation Service")
class PlanningCurrencyPresentationServiceTest {
  @DisplayName("presents Spending Difference As Extra Capacity Or Over Limit")
  @Test
  void presentsSpendingDifferenceAsExtraCapacityOrOverLimit() {
    PlanningCurrencyPresentationService service =
        new PlanningCurrencyPresentationService(
            Mockito.mock(CurrencyConversion.class),
            Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC));
    SustainableSpendingAnalysis positive =
        new SustainableSpendingAnalysis(
            new BigDecimal("15000"),
            new SustainableSpendingAnalysis.ScenarioResult(
                new BigDecimal("20000"),
                new BigDecimal("5000"),
                new BigDecimal("0.3333"),
                false,
                SustainableSpendingResultState.BOUNDARY_FOUND),
            new SustainableSpendingAnalysis.ScenarioResult(
                new BigDecimal("20000"),
                new BigDecimal("5000"),
                new BigDecimal("0.3333"),
                false,
                SustainableSpendingResultState.BOUNDARY_FOUND));
    SustainableSpendingAnalysisMoney extra =
        service.displaySustainableSpending(positive, CurrencyType.USD);
    assertEquals("+5,000", extra.conservativeHeadroom());
    assertFalse(extra.conservativeHeadroom().contains("-"));

    SustainableSpendingAnalysis negative =
        new SustainableSpendingAnalysis(
            new BigDecimal("20000"),
            new SustainableSpendingAnalysis.ScenarioResult(
                new BigDecimal("15000"),
                new BigDecimal("-5000"),
                new BigDecimal("-0.25"),
                true,
                SustainableSpendingResultState.BOUNDARY_FOUND),
            new SustainableSpendingAnalysis.ScenarioResult(
                new BigDecimal("15000"),
                new BigDecimal("-5000"),
                new BigDecimal("-0.25"),
                true,
                SustainableSpendingResultState.BOUNDARY_FOUND));
    SustainableSpendingAnalysisMoney overLimit =
        service.displaySustainableSpending(negative, CurrencyType.USD);
    assertEquals("5,000", overLimit.conservativeHeadroom());
    assertFalse(overLimit.conservativeHeadroom().contains("-"));
  }

  @DisplayName("converts Both Historical Actual And Expected Values With The Same Display Rate")
  @Test
  void convertsBothHistoricalActualAndExpectedValuesWithTheSameDisplayRate() {
    CurrencyConversion rates = Mockito.mock(CurrencyConversion.class);
    when(rates.convertToBaseCurrency(
            any(BigDecimal.class),
            eq(CurrencyType.PLN),
            eq(CurrencyType.USD),
            any(LocalDate.class)))
        .thenAnswer(i -> ((BigDecimal) i.getArgument(0)).multiply(new BigDecimal("4")));
    PlanningCurrencyPresentationService service =
        new PlanningCurrencyPresentationService(
            rates, Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC));
    PastPlanningYear past =
        new PastPlanningYear(
            2025,
            PlanningYearStatus.CLOSED,
            null,
            Map.of(
                PlanningMetric.NET_WORTH,
                new PlanningMetricValue(
                    PlanningMetric.NET_WORTH,
                    new BigDecimal("100"),
                    null,
                    PlanningValueSource.PORTFOLIO_DERIVED,
                    null)),
            Map.of(
                PlanningMetric.NET_WORTH,
                new PlanningMetricValue(
                    PlanningMetric.NET_WORTH,
                    new BigDecimal("120"),
                    null,
                    PlanningValueSource.SIMULATION_BASELINE,
                    null),
                PlanningMetric.EQUITY_RETURN,
                new PlanningMetricValue(
                    PlanningMetric.EQUITY_RETURN,
                    new BigDecimal("0.07"),
                    null,
                    PlanningValueSource.SIMULATION_BASELINE,
                    null)));
    PastPlanningYear displayed = service.display(past, CurrencyType.PLN);
    assertEquals(
        new BigDecimal("400"), displayed.values().get(PlanningMetric.NET_WORTH).derivedValue());
    assertEquals(
        new BigDecimal("480"),
        displayed.expectedValues().get(PlanningMetric.NET_WORTH).derivedValue());
    assertEquals(
        new BigDecimal("0.07"),
        displayed.expectedValues().get(PlanningMetric.EQUITY_RETURN).derivedValue());
  }

  @DisplayName("converts Profile And Every Monetary Chart Dataset At The Single Display Boundary")
  @Test
  void convertsProfileAndEveryMonetaryChartDatasetAtTheSingleDisplayBoundary() {
    CurrencyConversion rates = Mockito.mock(CurrencyConversion.class);
    when(rates.convertToBaseCurrency(
            any(BigDecimal.class),
            eq(CurrencyType.PLN),
            eq(CurrencyType.USD),
            any(LocalDate.class)))
        .thenAnswer(i -> ((BigDecimal) i.getArgument(0)).multiply(new BigDecimal("4")));
    PlanningCurrencyPresentationService service =
        new PlanningCurrencyPresentationService(
            rates, Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC));
    InvestmentProfile profile =
        new InvestmentProfile(
            1L,
            CurrencyType.USD,
            new BigDecimal("100"),
            new BigDecimal("50"),
            new BigDecimal("150"),
            new BigDecimal("25"),
            new BigDecimal("125"),
            List.of(),
            null,
            null,
            new com.smartbox.investory.profile.api.model.ProfileAssetProjection(
                List.of(),
                java.math.BigDecimal.ZERO,
                0,
                com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
            (new BigDecimal("25") == null ? java.math.BigDecimal.ZERO : new BigDecimal("25")),
            new BigDecimal("100")
                .subtract(
                    (new BigDecimal("25") == null
                        ? java.math.BigDecimal.ZERO
                        : new BigDecimal("25")))
                .max(java.math.BigDecimal.ZERO),
            com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
                BigDecimal.ZERO,
                new BigDecimal("100"),
                new BigDecimal("10"),
                new BigDecimal("50"),
                BigDecimal.ZERO,
                new BigDecimal("150")),
            com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
    PlanningProfileMoney displayedProfile = service.displayProfile(profile, CurrencyType.PLN);
    assertEquals(new BigDecimal("600"), displayedProfile.totalNetWorth());
    assertEquals(new BigDecimal("400"), displayedProfile.marketPortfolioValue());
    SimulationChartData canonicalCharts =
        new SimulationChartData(
            Map.of(
                SimulationScenario.BASE,
                List.of(
                    new SimulationChartData.BalancePoint(
                        2027, 41, new BigDecimal("100"), new BigDecimal("25")))),
            List.of(
                new SimulationChartData.IncomeSpendingPoint(
                    2027, new BigDecimal("10"), new BigDecimal("20"))),
            List.of(
                new SimulationChartData.CompositionPoint(
                    2027,
                    new BigDecimal("1"),
                    new BigDecimal("2"),
                    new BigDecimal("3"),
                    new BigDecimal("4"))));
    SimulationChartData displayedCharts = service.displayCharts(canonicalCharts, CurrencyType.PLN);
    assertEquals(
        new BigDecimal("400"),
        displayedCharts.balances().get(SimulationScenario.BASE).getFirst().netWorth());
    assertEquals(
        new BigDecimal("40"), displayedCharts.incomeSpending().getFirst().recurringIncome());
    assertEquals(new BigDecimal("16"), displayedCharts.composition().getFirst().equities());
    assertEquals(
        new BigDecimal("100"),
        canonicalCharts.balances().get(SimulationScenario.BASE).getFirst().netWorth());
  }

  @DisplayName("historical Presentation Hides Legacy Passive Income Without Deleting It")
  @Test
  void historicalPresentationHidesLegacyPassiveIncomeWithoutDeletingIt() {
    PlanningCurrencyPresentationService service =
        new PlanningCurrencyPresentationService(
            Mockito.mock(CurrencyConversion.class),
            Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC));
    PastPlanningYear past =
        new PastPlanningYear(
            2025,
            PlanningYearStatus.DRAFT,
            null,
            Map.of(
                PlanningMetric.PASSIVE_INCOME,
                value(PlanningMetric.PASSIVE_INCOME, "200000"),
                PlanningMetric.RENTAL_INCOME,
                value(PlanningMetric.RENTAL_INCOME, "171509")),
            Map.of());

    PastPlanningYear displayed = service.display(past, CurrencyType.USD);

    assertFalse(displayed.values().containsKey(PlanningMetric.PASSIVE_INCOME));
    assertEquals(
        "171509", displayed.values().get(PlanningMetric.RENTAL_INCOME).value().toPlainString());
  }

  @DisplayName("historical Timeline Does Not Invent Funding Sources From Accounting Buckets")
  @Test
  void historicalTimelineDoesNotInventFundingSourcesFromAccountingBuckets() {
    PlanningMetricValue core = value(PlanningMetric.CORE_SPENDING, "180000");
    PlanningMetricValue extras = value(PlanningMetric.DISCRETIONARY_SPENDING, "60000");
    PlanningMetricValue withdrawal = value(PlanningMetric.MARKET_WITHDRAWAL, "74320");
    PlanningMetricValue fixed = value(PlanningMetric.FIXED_INCOME, "135849");
    PlanningMetricValue equity = value(PlanningMetric.EQUITY, "578516");
    PastPlanningYear past =
        new PastPlanningYear(
            2025,
            PlanningYearStatus.CLOSED,
            null,
            Map.of(
                PlanningMetric.CORE_SPENDING, core,
                PlanningMetric.DISCRETIONARY_SPENDING, extras,
                PlanningMetric.MARKET_WITHDRAWAL, withdrawal,
                PlanningMetric.FIXED_INCOME, fixed,
                PlanningMetric.EQUITY, equity),
            Map.of());
    PlanningTimeline timeline =
        new PlanningTimeline(
            List.of(
                new PlanningTimelineYear(
                    2025, 40, PlanningTimelineState.ACTUAL, past, null, null)));

    PlanningTimelineMoney displayed =
        new PlanningCurrencyPresentationService(
                Mockito.mock(CurrencyConversion.class),
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC))
            .displayTimelineMoney(timeline, CurrencyType.USD)
            .get(2025);

    assertEquals(new BigDecimal("240000"), displayed.annualCosts());
    assertNull(displayed.totalIncome());
    assertNull(displayed.reserveWithdrawal());
    assertNull(displayed.investmentEnd());
  }

  @DisplayName(
      "leaves Needs Review Timeline Rows Empty Until Historical Facts Or Projection Exists")
  @Test
  void leavesNeedsReviewTimelineRowsEmptyUntilHistoricalFactsOrProjectionExists() {
    PlanningTimeline timeline =
        new PlanningTimeline(
            List.of(
                new PlanningTimelineYear(
                    2025, 40, PlanningTimelineState.NEEDS_REVIEW, null, null, null)));

    PlanningTimelineMoney displayed =
        new PlanningCurrencyPresentationService(
                Mockito.mock(CurrencyConversion.class),
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC))
            .displayTimelineMoney(timeline, CurrencyType.USD)
            .get(2025);

    assertNull(displayed.annualCosts());
    assertNull(displayed.totalIncome());
    assertNull(displayed.fundingGap());
    assertNull(displayed.investmentEnd());
  }

  @DisplayName("live Timeline Uses All Known Income For Its Funding Gap")
  @Test
  void liveTimelineUsesAllKnownIncomeForItsFundingGap() {
    Map<PlanningMetric, PlanningMetricValue> live =
        Map.of(
            PlanningMetric.CORE_SPENDING, value(PlanningMetric.CORE_SPENDING, "240000"),
            PlanningMetric.DISCRETIONARY_SPENDING,
                value(PlanningMetric.DISCRETIONARY_SPENDING, "0"),
            PlanningMetric.RENTAL_INCOME, value(PlanningMetric.RENTAL_INCOME, "174804"),
            PlanningMetric.PORTFOLIO_FUNDING, value(PlanningMetric.PORTFOLIO_FUNDING, "0"),
            PlanningMetric.SAFE_RESERVE, value(PlanningMetric.SAFE_RESERVE, "135849"),
            PlanningMetric.BOND_VALUE, value(PlanningMetric.BOND_VALUE, "900000"),
            PlanningMetric.BOND_INCOME, value(PlanningMetric.BOND_INCOME, "38880"),
            PlanningMetric.EQUITY, value(PlanningMetric.EQUITY, "578516"));
    CurrentPlanningYear current =
        new CurrentPlanningYear(
            2026,
            4L,
            55L,
            Instant.now(),
            live,
            Map.of(PlanningMetric.CORE_SPENDING, value(PlanningMetric.CORE_SPENDING, "999999")));
    PlanningTimeline timeline =
        new PlanningTimeline(
            List.of(
                new PlanningTimelineYear(
                    2026, 41, PlanningTimelineState.LIVE, null, current, null)));

    PlanningTimelineMoney displayed =
        new PlanningCurrencyPresentationService(
                Mockito.mock(CurrencyConversion.class),
                Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC))
            .displayTimelineMoney(timeline, CurrencyType.USD, liveAssumptions())
            .get(2026);

    assertEquals(new BigDecimal("240000"), displayed.annualCosts());
    assertEquals(new BigDecimal("333684"), displayed.totalIncome());
    assertEquals(new BigDecimal("174804"), displayed.rentalIncome());
    assertEquals(new BigDecimal("38880"), displayed.bondIncome());
    assertEquals(BigDecimal.ZERO, displayed.fundingGap());
    assertNull(displayed.reserveWithdrawal());
    assertEquals(new BigDecimal("135849"), displayed.reserveEnd());
  }

  @DisplayName("projected Timeline Uses Canonical Funding Components")
  @Test
  void projectedTimelineUsesCanonicalFundingComponents() {
    SimulationYear projection = Mockito.mock(SimulationYear.class);
    when(projection.totalExpenses()).thenReturn(new BigDecimal("100"));
    when(projection.rentalIncome()).thenReturn(BigDecimal.ZERO);
    when(projection.totalIncome()).thenReturn(BigDecimal.ZERO);
    when(projection.bondIncome()).thenReturn(BigDecimal.ZERO);
    when(projection.funding())
        .thenReturn(
            new SimulationFunding(
                new BigDecimal("100"),
                new BigDecimal("80"),
                BigDecimal.ZERO,
                new BigDecimal("20"),
                new BigDecimal("60"),
                new BigDecimal("30"),
                new BigDecimal("70"),
                new BigDecimal("200"),
                BigDecimal.ZERO,
                new BigDecimal("40"),
                new BigDecimal("160"),
                new BigDecimal("10")));

    PlanningTimeline timeline =
        new PlanningTimeline(
            List.of(
                new PlanningTimelineYear(
                    2027, 42, PlanningTimelineState.PROJECTED, null, null, projection)));

    PlanningTimelineMoney displayed =
        new PlanningCurrencyPresentationService(
                Mockito.mock(CurrencyConversion.class),
                Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC))
            .displayTimelineMoney(timeline, CurrencyType.USD)
            .get(2027);

    assertEquals(new BigDecimal("100"), displayed.fundingGap());
    assertEquals(new BigDecimal("20"), displayed.reserveWithdrawal());
    assertEquals(new BigDecimal("30"), displayed.longTermFunding());
    assertEquals(new BigDecimal("40"), displayed.investmentWithdrawal());
    assertEquals(new BigDecimal("10"), displayed.unfunded());
    assertEquals(new BigDecimal("60"), displayed.reserveEnd());
    assertEquals(new BigDecimal("70"), displayed.longTermCapitalEnd());
    assertEquals(new BigDecimal("160"), displayed.investmentEnd());
  }

  private static PlanningMetricValue value(PlanningMetric metric, String amount) {
    return new PlanningMetricValue(
        metric, new BigDecimal(amount), null, PlanningValueSource.ACCOUNTING_DERIVED, null);
  }

  private static SimulationAssumptions liveAssumptions() {
    return new SimulationAssumptions(
        41,
        95,
        new BigDecimal("240000"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        67,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        2026,
        BigDecimal.ZERO,
        List.of(),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        SimulationFundingStrategy.RESERVE_AND_HARVEST,
        BigDecimal.ONE,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        true,
        65,
        new BigDecimal("120000"),
        BigDecimal.ZERO,
        SimulationAssumptions.DEFAULT_FUNDING_ORDER,
        ExpenseProfile.EMPTY,
        ProjectedIncomePolicy.SOURCE);
  }

  @DisplayName("current Year Review Exposes Source Baseline And Missing Planning Inputs")
  @Test
  void currentYearReviewExposesSourceBaselineAndMissingPlanningInputs() {
    PlanningCurrencyPresentationService service =
        new PlanningCurrencyPresentationService(
            Mockito.mock(CurrencyConversion.class),
            Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC));
    CurrentPlanningYear current =
        new CurrentPlanningYear(
            2026,
            4L,
            Instant.now(),
            Map.of(
                PlanningMetric.NET_WORTH,
                new PlanningMetricValue(
                    PlanningMetric.NET_WORTH,
                    new BigDecimal("100"),
                    null,
                    PlanningValueSource.PORTFOLIO_DERIVED,
                    null),
                PlanningMetric.CORE_SPENDING,
                new PlanningMetricValue(
                    PlanningMetric.CORE_SPENDING,
                    null,
                    new BigDecimal("20"),
                    PlanningValueSource.USER_OVERRIDE,
                    "manual")),
            Map.of(
                PlanningMetric.NET_WORTH,
                new PlanningMetricValue(
                    PlanningMetric.NET_WORTH,
                    new BigDecimal("120"),
                    null,
                    PlanningValueSource.SIMULATION_BASELINE,
                    null)));

    CurrentYearReview review = service.displayCurrentYear(current, CurrencyType.USD);

    assertEquals("Manual input required", review.status());
    assertEquals(List.of("Annual extras"), review.missingMetrics());
    assertEquals(true, review.baselineSet());
    CurrentYearMetricReview netWorth =
        review.metrics().stream()
            .filter(metric -> metric.metric() == PlanningMetric.NET_WORTH)
            .findFirst()
            .orElseThrow();
    assertEquals("100", netWorth.currentValue());
    assertEquals("120", netWorth.baselineValue());
    assertEquals("Live Investory data", netWorth.source());
    CurrentYearMetricReview spending =
        review.metrics().stream()
            .filter(metric -> metric.metric() == PlanningMetric.CORE_SPENDING)
            .findFirst()
            .orElseThrow();
    assertEquals(true, spending.manual());
    assertEquals("Manual planning input", spending.source());
  }

  @DisplayName(
      "plan Progress Display Converts For Presentation Without Changing Canonical Difference")
  @Test
  void planProgressDisplayConvertsForPresentationWithoutChangingCanonicalDifference() {
    CurrencyConversion rates = Mockito.mock(CurrencyConversion.class);
    when(rates.convertToBaseCurrency(
            any(BigDecimal.class),
            eq(CurrencyType.PLN),
            eq(CurrencyType.USD),
            any(LocalDate.class)))
        .thenAnswer(
            invocation -> ((BigDecimal) invocation.getArgument(0)).multiply(BigDecimal.valueOf(4)));
    PlanningCurrencyPresentationService service =
        new PlanningCurrencyPresentationService(
            rates, Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC));
    PlanProgress progress =
        PlanProgress.from(
            List.of(
                new PlanProgressPoint(
                    2025,
                    LocalDate.of(2025, 12, 31),
                    new BigDecimal("600"),
                    new BigDecimal("500"),
                    new BigDecimal("100"),
                    PlanProgressState.AHEAD,
                    7L,
                    2L)));

    PlanProgressView displayed = service.displayPlanProgress(progress, CurrencyType.PLN);

    assertEquals(new BigDecimal("100"), progress.headline().difference());
    assertEquals("+400 PLN", displayed.headlineDifference());
    assertEquals("Ahead of plan", displayed.headlineState());
    assertEquals("31 Dec 2025", displayed.latestBoundary());
  }
}
