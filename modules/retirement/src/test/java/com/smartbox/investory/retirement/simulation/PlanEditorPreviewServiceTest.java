package com.smartbox.investory.retirement.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.longterm.api.LongTermAssetAnnualSnapshotReader;
import com.smartbox.investory.longterm.api.model.LongTermAssetAnnualSnapshotModel;
import com.smartbox.investory.retirement.planning.ForwardSimulationInputService;
import com.smartbox.investory.retirement.planning.PlanningCurrencyPresentationService;
import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlanEditorPreviewServiceTest {
  @Test
  void previewUsesGenericCapitalFields() {
    var names =
        java.util.Arrays.stream(PlanEditorPreviewService.PreviewYear.class.getRecordComponents())
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

  @Test
  void currentFactsComeFromCanonicalLongTermAssetSnapshot() {
    ForwardSimulationInputService inputs = mock(ForwardSimulationInputService.class);
    RetirementSimulation simulations = mock(RetirementSimulation.class);
    LongTermAssetAnnualSnapshotReader longTermAssets =
        mock(LongTermAssetAnnualSnapshotReader.class);
    PlanningCurrencyPresentationService presentation =
        mock(PlanningCurrencyPresentationService.class);
    Clock clock = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);
    PlanEditorPreviewService service =
        new PlanEditorPreviewService(inputs, simulations, longTermAssets, presentation, clock);
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
    when(longTermAssets.currentAnnualSnapshot(
            7L, Instant.now(clock).atZone(ZoneOffset.UTC).toLocalDate()))
        .thenReturn(facts);

    assertSame(facts, service.currentFacts(profile));
  }

  @Test
  void previewUsesRoundedDivisionForNonTerminatingMonthlyCosts() {
    ForwardSimulationInputService inputs = mock(ForwardSimulationInputService.class);
    RetirementSimulation simulations = mock(RetirementSimulation.class);
    LongTermAssetAnnualSnapshotReader longTermAssets =
        mock(LongTermAssetAnnualSnapshotReader.class);
    PlanningCurrencyPresentationService presentation =
        mock(PlanningCurrencyPresentationService.class);
    Clock clock = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);
    PlanEditorPreviewService service =
        new PlanEditorPreviewService(inputs, simulations, longTermAssets, presentation, clock);
    InvestmentProfile profile = mock(InvestmentProfile.class);
    SimulationAssumptions assumptions =
        new SimulationAssumptions(
            40,
            40,
            BigDecimal.ONE,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
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
            BigDecimal.ZERO);
    SimulationResult empty =
        new SimulationResult(SimulationScenario.BASE, false, null, BigDecimal.ZERO, List.of());
    when(profile.portfolioId()).thenReturn(7L);
    when(inputs.prepare(any(), any()))
        .thenReturn(
            new com.smartbox.investory.retirement.planning.ForwardSimulationInput(
                mock(ForwardSimulationContext.class), profile, Optional.of(assumptions)));
    when(simulations.simulate(profile, assumptions, SimulationScenario.BASE)).thenReturn(empty);
    when(longTermAssets.currentAnnualSnapshot(any(), any()))
        .thenReturn(new LongTermAssetAnnualSnapshotModel(null, null, null, null, null, null));

    when(presentation.toDisplay(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

    PlanEditorPreviewService.PlanEditorPreview preview =
        service.preview(profile, assumptions, CurrencyType.PLN);

    assertEquals(new BigDecimal("0.083333333333"), preview.monthlyLivingCosts());
    assertEquals(2026, preview.planStartYear());
    assertEquals(40, preview.ageAtPlanStart());
    assertEquals(2026, preview.currentPlanningYear());
    assertEquals(40, preview.currentPlanningAge());
    assertEquals(1, preview.planHorizonYears());
  }

  @Test
  void incomePreviewStartsWithCurrentYearCanonicalFacts() {
    ForwardSimulationInputService inputs = mock(ForwardSimulationInputService.class);
    RetirementSimulation simulations = mock(RetirementSimulation.class);
    LongTermAssetAnnualSnapshotReader longTermAssets =
        mock(LongTermAssetAnnualSnapshotReader.class);
    PlanningCurrencyPresentationService presentation =
        mock(PlanningCurrencyPresentationService.class);
    Clock clock = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);
    PlanEditorPreviewService service =
        new PlanEditorPreviewService(inputs, simulations, longTermAssets, presentation, clock);
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
            new com.smartbox.investory.retirement.planning.ForwardSimulationInput(
                mock(ForwardSimulationContext.class), profile, Optional.empty()));
    when(longTermAssets.currentAnnualSnapshot(any(), any())).thenReturn(facts);
    when(presentation.toDisplay(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

    PlanEditorPreviewService.PreviewYear current =
        service.preview(profile, assumptions, CurrencyType.PLN).years().getFirst();

    assertEquals(2026, current.year());
    assertEquals("WORKING", current.lifecycle());
    assertEquals(new BigDecimal("120000"), current.employmentIncome());
    assertEquals(new BigDecimal("47411"), current.rentalIncome());
    assertEquals(new BigDecimal("10545"), current.bondIncome());
    assertEquals("CURRENT", current.state());
  }

  @Test
  void previewIncludesHistoricalCurrentAndProjectedYearsFromTheTemporalAnchor() {
    ForwardSimulationInputService inputs = mock(ForwardSimulationInputService.class);
    RetirementSimulation simulations = mock(RetirementSimulation.class);
    LongTermAssetAnnualSnapshotReader longTermAssets = mock(LongTermAssetAnnualSnapshotReader.class);
    PlanningCurrencyPresentationService presentation = mock(PlanningCurrencyPresentationService.class);
    Clock clock = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);
    PlanEditorPreviewService service =
        new PlanEditorPreviewService(inputs, simulations, longTermAssets, presentation, clock);
    InvestmentProfile profile = mock(InvestmentProfile.class);
    SimulationAssumptions assumptions =
        SimulationAssumptions.defaults(profile, 40, 50, 2025).withRetirementAge(45);
    SimulationYear projected =
        SimulationYear.generic(
            42,
            2027,
            false,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO);
    LongTermAssetAnnualSnapshotModel historical =
        new LongTermAssetAnnualSnapshotModel(
            null, new BigDecimal("170000"), null, new BigDecimal("30000"), null, null);
    LongTermAssetAnnualSnapshotModel current =
        new LongTermAssetAnnualSnapshotModel(
            null, new BigDecimal("174804"), null, new BigDecimal("38880"), null, null);
    when(profile.portfolioId()).thenReturn(7L);
    when(inputs.prepare(any(), any()))
        .thenReturn(
            new com.smartbox.investory.retirement.planning.ForwardSimulationInput(
                mock(ForwardSimulationContext.class), profile, Optional.of(assumptions)));
    when(simulations.simulate(profile, assumptions, SimulationScenario.BASE))
        .thenReturn(
            new SimulationResult(
                SimulationScenario.BASE, false, null, BigDecimal.ZERO, List.of(projected)));
    when(longTermAssets.historicalAnnualSnapshot(7L, 2025)).thenReturn(historical);
    when(longTermAssets.currentAnnualSnapshot(any(), any())).thenReturn(current);
    when(presentation.toDisplay(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

    List<PlanEditorPreviewService.PreviewYear> years =
        service.preview(profile, assumptions, CurrencyType.PLN).years();

    assertEquals(List.of(2025, 2026, 2027), years.stream().map(year -> year.year()).toList());
    assertEquals(List.of(40, 41, 42), years.stream().map(year -> year.age()).toList());
    assertEquals(List.of("HISTORICAL", "CURRENT", "PROJECTED"), years.stream().map(year -> year.state()).toList());
    assertEquals(new BigDecimal("170000"), years.get(0).rentalIncome());
    assertEquals(new BigDecimal("30000"), years.get(0).bondIncome());
    assertEquals(new BigDecimal("174804"), years.get(1).rentalIncome());
    assertEquals(new BigDecimal("38880"), years.get(1).bondIncome());
    verify(longTermAssets).historicalAnnualSnapshot(7L, 2025);
    verify(longTermAssets).currentAnnualSnapshot(7L, Instant.now(clock).atZone(ZoneOffset.UTC).toLocalDate());
  }

  @Test
  void projectedPreviewUsesSimulationYearDirectly() {
    ForwardSimulationInputService inputs = mock(ForwardSimulationInputService.class);
    RetirementSimulation simulations = mock(RetirementSimulation.class);
    LongTermAssetAnnualSnapshotReader longTermAssets = mock(LongTermAssetAnnualSnapshotReader.class);
    PlanningCurrencyPresentationService presentation = mock(PlanningCurrencyPresentationService.class);
    Clock clock = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);
    PlanEditorPreviewService service =
        new PlanEditorPreviewService(inputs, simulations, longTermAssets, presentation, clock);
    InvestmentProfile profile = mock(InvestmentProfile.class);
    SimulationAssumptions assumptions = SimulationAssumptions.defaults(profile, 40, 41, 2026);
    var context = mock(ForwardSimulationContext.class);
    var projected = assumptions;
    var row =
        SimulationYear.generic(
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
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("8614"),
            new BigDecimal("732"),
            BigDecimal.ZERO,
            new BigDecimal("9346"),
            BigDecimal.ZERO,
            BigDecimal.ZERO);
    when(profile.portfolioId()).thenReturn(7L);
    when(inputs.prepare(any(), any()))
        .thenReturn(
            new com.smartbox.investory.retirement.planning.ForwardSimulationInput(
                context, profile, Optional.of(projected)));
    when(simulations.simulate(profile, projected, SimulationScenario.BASE))
        .thenReturn(new SimulationResult(SimulationScenario.BASE, false, null, BigDecimal.ZERO, List.of(row)));
    when(longTermAssets.currentAnnualSnapshot(any(), any()))
        .thenReturn(new LongTermAssetAnnualSnapshotModel(null, null, null, null, null, null));
    when(presentation.toDisplay(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

    PlanEditorPreviewService.PlanEditorPreview preview =
        service.preview(profile, assumptions, CurrencyType.PLN);
    PlanEditorPreviewService.PreviewYear previewYear = preview.years().get(1);

    assertEquals(2027, previewYear.year());
    assertEquals(new BigDecimal("732"), previewYear.investmentReturn());
    assertEquals(2027, preview.plannedIncomeReferenceYear());
    assertEquals(new BigDecimal("732"), preview.plannedAnnualIncome());
    assertEquals(
        previewYear.fundingGap(),
        previewYear.reserveWithdrawal()
            .add(previewYear.longTermFunding())
            .add(previewYear.investmentWithdrawal())
            .add(previewYear.unfunded()));
  }
}
