package com.smartbox.investory.retirement.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.longterm.api.LongTermAssetAnnualSnapshot;
import com.smartbox.investory.longterm.api.LongTermAssetAnnualSnapshotReader;
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
  void previewUsesFixedIncomeNamesForMarketBucketFields() {
    var names =
        java.util.Arrays.stream(PlanEditorPreviewService.PreviewYear.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName)
            .toList();

    assertTrue(names.contains("fixedIncomeStart"));
    assertTrue(names.contains("fixedIncomeEnd"));
    assertFalse(names.contains("bondsStart"));
    assertFalse(names.contains("bondsEnd"));
  }

  @Test
  void currentFactsComeFromCanonicalLongTermAssetSnapshot() {
    ForwardSimulationInputService inputs = mock(ForwardSimulationInputService.class);
    RetirementSimulationService simulations = mock(RetirementSimulationService.class);
    LongTermAssetAnnualSnapshotReader longTermAssets =
        mock(LongTermAssetAnnualSnapshotReader.class);
    PlanningCurrencyPresentationService presentation =
        mock(PlanningCurrencyPresentationService.class);
    Clock clock = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);
    PlanEditorPreviewService service =
        new PlanEditorPreviewService(inputs, simulations, longTermAssets, presentation, clock);
    InvestmentProfile profile = mock(InvestmentProfile.class);
    LongTermAssetAnnualSnapshot facts =
        new LongTermAssetAnnualSnapshot(
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
    RetirementSimulationService simulations = mock(RetirementSimulationService.class);
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
        .thenReturn(new LongTermAssetAnnualSnapshot(null, null, null, null, null, null));

    when(presentation.toDisplay(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

    PlanEditorPreviewService.PlanEditorPreview preview =
        service.preview(profile, assumptions, CurrencyType.PLN);

    assertEquals(new BigDecimal("0.083333333333"), preview.monthlyLivingCosts());
  }
}
