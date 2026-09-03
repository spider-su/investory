package com.smartbox.investory.retirement.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.longterm.api.LongTermAssetAnnualSnapshotReader;
import com.smartbox.investory.longterm.api.LongTermAssetProfileReader;
import com.smartbox.investory.longterm.api.model.LongTermAssetAnnualSnapshotModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProfileSnapshotModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProfileSummaryModel;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.PlanEditorPreview;
import com.smartbox.investory.retirement.planning.ForwardSimulationInputService;
import com.smartbox.investory.retirement.planning.PlanningCurrencyPresentationService;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Plan Editor Preview Service")
class PlanEditorPreviewServiceTest {
  @DisplayName("preview Uses Generic Capital Fields")
  @Test
  void previewUsesGenericCapitalFields() {
    var names =
        java.util.Arrays.stream(PlanEditorPreview.PreviewYear.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName)
            .toList();

    assertTrue(names.contains("investmentStart"));
    assertTrue(names.contains("investmentEnd"));
    assertTrue(names.contains("fundingGap"));
    assertTrue(names.contains("reserveTransfer"));
    assertTrue(names.contains("longTermFunding"));
    assertTrue(names.contains("investmentReturn"));
    assertTrue(names.contains("status"));
    assertTrue(names.contains("state"));
    assertFalse(names.contains("equityHarvest"));
    assertFalse(names.contains("longTermAvailable"));
  }

  @DisplayName("current Facts Come From Canonical Long Term Asset Snapshot")
  @Test
  void currentFactsComeFromCanonicalLongTermAssetSnapshot() {
    ForwardSimulationInputService inputs = mock(ForwardSimulationInputService.class);
    RetirementSimulation simulations = mock(RetirementSimulation.class);
    LongTermAssetAnnualSnapshotReader longTermAssets =
        mock(LongTermAssetAnnualSnapshotReader.class);
    LongTermAssetProfileReader currentLongTermAssets = mock(LongTermAssetProfileReader.class);
    PlanningCurrencyPresentationService presentation =
        mock(PlanningCurrencyPresentationService.class);
    Clock clock = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);
    PlanEditorPreviewService service =
        new PlanEditorPreviewService(
            inputs, simulations, longTermAssets, currentLongTermAssets, presentation, clock);
    InvestmentProfile profile = mock(InvestmentProfile.class);
    LongTermAssetAnnualSnapshotModel facts =
        new LongTermAssetAnnualSnapshotModel(
            new BigDecimal("3650000"),
            new BigDecimal("171509"),
            new BigDecimal("900000"),
            new BigDecimal("48000"),
            new BigDecimal("100000"),
            BigDecimal.ZERO);
    when(profile.portfolioId()).thenReturn(7L);
    when(currentLongTermAssets.snapshot(
            7L, Instant.now(clock).atZone(ZoneOffset.UTC).toLocalDate()))
        .thenReturn(profileSnapshot(facts));

    assertSame(facts, service.currentFacts(profile));
  }

  @DisplayName("preview Uses Rounded Division For Non Terminating Monthly Costs")
  @Test
  void previewUsesRoundedDivisionForNonTerminatingMonthlyCosts() {
    ForwardSimulationInputService inputs = mock(ForwardSimulationInputService.class);
    RetirementSimulation simulations = mock(RetirementSimulation.class);
    LongTermAssetAnnualSnapshotReader longTermAssets =
        mock(LongTermAssetAnnualSnapshotReader.class);
    LongTermAssetProfileReader currentLongTermAssets = mock(LongTermAssetProfileReader.class);
    PlanningCurrencyPresentationService presentation =
        mock(PlanningCurrencyPresentationService.class);
    Clock clock = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);
    PlanEditorPreviewService service =
        new PlanEditorPreviewService(
            inputs, simulations, longTermAssets, currentLongTermAssets, presentation, clock);
    InvestmentProfile profile = mock(InvestmentProfile.class);
    SimulationAssumptions assumptions =
        new SimulationAssumptions(
            40,
            40,
            BigDecimal.ONE,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            99,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            2026,
            BigDecimal.ZERO,
            List.of(),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            SimulationFundingStrategy.RESERVE_AND_HARVEST,
            SimulationAssumptions.DEFAULT_SAFE_RESERVE_YEARS,
            SimulationAssumptions.DEFAULT_EQUITY_HARVEST_MINIMUM_RETURN_RATE,
            SimulationAssumptions.DEFAULT_EQUITY_GAIN_HARVEST_RATE,
            true,
            40,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            SimulationAssumptions.DEFAULT_FUNDING_ORDER,
            ExpenseProfile.EMPTY);
    SimulationResult empty =
        new SimulationResult(SimulationScenario.BASE, false, null, BigDecimal.ZERO, List.of());
    when(profile.portfolioId()).thenReturn(7L);
    when(inputs.prepare(any(), any()))
        .thenReturn(
            new com.smartbox.investory.retirement.api.model.ForwardSimulationInput(
                mock(ForwardSimulationContext.class), profile, Optional.of(assumptions)));
    when(simulations.simulate(eq(profile), eq(assumptions), eq(SimulationScenario.BASE), anyInt()))
        .thenReturn(empty);
    when(currentLongTermAssets.snapshot(any(), any()))
        .thenReturn(
            profileSnapshot(
                new LongTermAssetAnnualSnapshotModel(null, null, null, null, null, null)));

    when(presentation.toDisplay(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

    PlanEditorPreview preview = service.preview(profile, assumptions, CurrencyType.PLN);

    assertEquals(new BigDecimal("0.083333333333"), preview.monthlyLivingCosts());
    assertEquals(2026, preview.planStartYear());
    assertEquals(40, preview.ageAtPlanStart());
    assertEquals(2026, preview.currentPlanningYear());
    assertEquals(40, preview.currentPlanningAge());
    assertEquals(1, preview.planHorizonYears());
  }

  @DisplayName("income Preview Starts With Current Year Canonical Facts")
  @Test
  void incomePreviewStartsWithCurrentYearCanonicalFacts() {
    ForwardSimulationInputService inputs = mock(ForwardSimulationInputService.class);
    RetirementSimulation simulations = mock(RetirementSimulation.class);
    LongTermAssetAnnualSnapshotReader longTermAssets =
        mock(LongTermAssetAnnualSnapshotReader.class);
    LongTermAssetProfileReader currentLongTermAssets = mock(LongTermAssetProfileReader.class);
    PlanningCurrencyPresentationService presentation =
        mock(PlanningCurrencyPresentationService.class);
    Clock clock = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);
    PlanEditorPreviewService service =
        new PlanEditorPreviewService(
            inputs, simulations, longTermAssets, currentLongTermAssets, presentation, clock);
    InvestmentProfile profile = mock(InvestmentProfile.class);
    SimulationAssumptions assumptions =
        SimulationAssumptions.defaults(profile, 41, 42, 2026)
            .withRetirementAge(42)
            .withAnnualEmploymentIncome(new BigDecimal("120000"))
            .withAnnualPreRetirementContribution(new BigDecimal("12000"));
    LongTermAssetAnnualSnapshotModel facts =
        new LongTermAssetAnnualSnapshotModel(
            BigDecimal.ZERO,
            new BigDecimal("47411"),
            BigDecimal.ZERO,
            new BigDecimal("10545"),
            BigDecimal.ZERO,
            BigDecimal.ZERO);
    when(profile.portfolioId()).thenReturn(7L);
    when(inputs.prepare(any(), any()))
        .thenReturn(
            new com.smartbox.investory.retirement.api.model.ForwardSimulationInput(
                mock(ForwardSimulationContext.class), profile, Optional.empty()));
    when(currentLongTermAssets.snapshot(any(), any())).thenReturn(profileSnapshot(facts));
    when(presentation.toDisplay(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

    PlanEditorPreview.PreviewYear current =
        service.preview(profile, assumptions, CurrencyType.PLN).years().getFirst();

    assertEquals(2026, current.year());
    assertEquals("WORKING", current.lifecycle());
    assertEquals(new BigDecimal("120000"), current.employmentIncome());
    assertEquals(new BigDecimal("47411"), current.rentalIncome());
    assertEquals(new BigDecimal("10545"), current.bondIncome());
    assertEquals("CURRENT", current.state());
  }

  @DisplayName("preview Includes Historical Current And Projected Years From The Temporal Anchor")
  @Test
  void previewIncludesHistoricalCurrentAndProjectedYearsFromTheTemporalAnchor() {
    ForwardSimulationInputService inputs = mock(ForwardSimulationInputService.class);
    RetirementSimulation simulations = mock(RetirementSimulation.class);
    LongTermAssetAnnualSnapshotReader longTermAssets =
        mock(LongTermAssetAnnualSnapshotReader.class);
    LongTermAssetProfileReader currentLongTermAssets = mock(LongTermAssetProfileReader.class);
    PlanningCurrencyPresentationService presentation =
        mock(PlanningCurrencyPresentationService.class);
    Clock clock = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);
    PlanEditorPreviewService service =
        new PlanEditorPreviewService(
            inputs, simulations, longTermAssets, currentLongTermAssets, presentation, clock);
    InvestmentProfile profile = mock(InvestmentProfile.class);
    SimulationAssumptions assumptions =
        SimulationAssumptions.defaults(profile, 40, 50, 2025).withRetirementAge(45);
    SimulationYear projected = zeroYear(42, 2027, false);
    LongTermAssetAnnualSnapshotModel historical =
        new LongTermAssetAnnualSnapshotModel(
            null, new BigDecimal("170000"), null, new BigDecimal("30000"), null, null);
    LongTermAssetAnnualSnapshotModel current =
        new LongTermAssetAnnualSnapshotModel(
            null, new BigDecimal("174804"), null, new BigDecimal("38880"), null, null);
    when(profile.portfolioId()).thenReturn(7L);
    when(inputs.prepare(any(), any()))
        .thenReturn(
            new com.smartbox.investory.retirement.api.model.ForwardSimulationInput(
                mock(ForwardSimulationContext.class), profile, Optional.of(assumptions)));
    when(simulations.simulate(eq(profile), eq(assumptions), eq(SimulationScenario.BASE), anyInt()))
        .thenReturn(
            new SimulationResult(
                SimulationScenario.BASE, false, null, BigDecimal.ZERO, List.of(projected)));
    when(longTermAssets.historicalAnnualSnapshot(7L, 2025)).thenReturn(historical);
    when(currentLongTermAssets.snapshot(any(), any())).thenReturn(profileSnapshot(current));
    when(presentation.toDisplay(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

    List<PlanEditorPreview.PreviewYear> years =
        service.preview(profile, assumptions, CurrencyType.PLN).years();

    assertEquals(List.of(2025, 2026, 2027), years.stream().map(year -> year.year()).toList());
    assertEquals(List.of(40, 41, 42), years.stream().map(year -> year.age()).toList());
    assertEquals(
        List.of("HISTORICAL", "CURRENT", "PROJECTED"),
        years.stream().map(year -> year.state()).toList());
    assertEquals(new BigDecimal("170000"), years.get(0).rentalIncome());
    assertEquals(new BigDecimal("30000"), years.get(0).bondIncome());
    assertEquals(new BigDecimal("174804"), years.get(1).rentalIncome());
    assertEquals(new BigDecimal("38880"), years.get(1).bondIncome());
    assertEquals(new BigDecimal("213684"), years.get(1).totalIncome());
    verify(longTermAssets).historicalAnnualSnapshot(7L, 2025);
    verify(currentLongTermAssets)
        .snapshot(7L, Instant.now(clock).atZone(ZoneOffset.UTC).toLocalDate());
  }

  @DisplayName("projected Preview Uses Simulation Year Directly")
  @Test
  void projectedPreviewUsesSimulationYearDirectly() {
    ForwardSimulationInputService inputs = mock(ForwardSimulationInputService.class);
    RetirementSimulation simulations = mock(RetirementSimulation.class);
    LongTermAssetAnnualSnapshotReader longTermAssets =
        mock(LongTermAssetAnnualSnapshotReader.class);
    LongTermAssetProfileReader currentLongTermAssets = mock(LongTermAssetProfileReader.class);
    PlanningCurrencyPresentationService presentation =
        mock(PlanningCurrencyPresentationService.class);
    Clock clock = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);
    PlanEditorPreviewService service =
        new PlanEditorPreviewService(
            inputs, simulations, longTermAssets, currentLongTermAssets, presentation, clock);
    InvestmentProfile profile = mock(InvestmentProfile.class);
    SimulationAssumptions assumptions = SimulationAssumptions.defaults(profile, 40, 41, 2026);
    var context = mock(ForwardSimulationContext.class);
    var projected = assumptions;
    var row =
        SimulationYear.bucket(
            41,
            2027,
            true,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            zeroBucket(com.smartbox.investory.profile.api.model.EconomicBucket.LIQUID_CASH),
            zeroBucket(com.smartbox.investory.profile.api.model.EconomicBucket.FIXED_INCOME),
            new BucketResult(
                com.smartbox.investory.profile.api.model.EconomicBucket.EQUITY,
                new BigDecimal("8614"),
                new BigDecimal("732"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("9346")),
            zeroBucket(com.smartbox.investory.profile.api.model.EconomicBucket.REAL_ESTATE),
            BigDecimal.ZERO,
            BigDecimal.ZERO);
    when(profile.portfolioId()).thenReturn(7L);
    when(inputs.prepare(any(), any()))
        .thenReturn(
            new com.smartbox.investory.retirement.api.model.ForwardSimulationInput(
                context, profile, Optional.of(projected)));
    when(simulations.simulate(eq(profile), eq(projected), eq(SimulationScenario.BASE), anyInt()))
        .thenReturn(
            new SimulationResult(
                SimulationScenario.BASE, false, null, BigDecimal.ZERO, List.of(row)));
    when(currentLongTermAssets.snapshot(any(), any()))
        .thenReturn(
            profileSnapshot(
                new LongTermAssetAnnualSnapshotModel(null, null, null, null, null, null)));
    when(presentation.toDisplay(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

    PlanEditorPreview preview = service.preview(profile, assumptions, CurrencyType.PLN);
    PlanEditorPreview.PreviewYear previewYear = preview.years().get(1);

    assertEquals(2027, previewYear.year());
    assertEquals(new BigDecimal("732"), previewYear.investmentReturn());
    assertEquals(2027, preview.plannedIncomeReferenceYear());
    assertEquals(BigDecimal.ZERO, preview.plannedAnnualIncome());
    assertEquals(
        previewYear.fundingGap(),
        previewYear
            .reserveWithdrawal()
            .add(previewYear.longTermFunding())
            .add(previewYear.investmentWithdrawal())
            .add(previewYear.unfunded()));
  }

  private static SimulationYear zeroYear(int age, int year, boolean retired) {
    return SimulationYear.bucket(
        age,
        year,
        retired,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        zeroBucket(com.smartbox.investory.profile.api.model.EconomicBucket.LIQUID_CASH),
        zeroBucket(com.smartbox.investory.profile.api.model.EconomicBucket.FIXED_INCOME),
        zeroBucket(com.smartbox.investory.profile.api.model.EconomicBucket.EQUITY),
        zeroBucket(com.smartbox.investory.profile.api.model.EconomicBucket.REAL_ESTATE),
        BigDecimal.ZERO,
        BigDecimal.ZERO);
  }

  private static BucketResult zeroBucket(
      com.smartbox.investory.profile.api.model.EconomicBucket bucket) {
    return new BucketResult(
        bucket,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO);
  }

  private static LongTermAssetProfileSnapshotModel profileSnapshot(
      LongTermAssetAnnualSnapshotModel annualSnapshot) {
    return new LongTermAssetProfileSnapshotModel(
        new LongTermAssetProfileSummaryModel(CurrencyType.USD, BigDecimal.ZERO, BigDecimal.ZERO),
        List.of(),
        List.of(),
        annualSnapshot);
  }
}
