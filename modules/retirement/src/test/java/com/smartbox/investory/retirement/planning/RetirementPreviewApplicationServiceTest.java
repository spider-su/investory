package com.smartbox.investory.retirement.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.profile.api.ProfileSnapshotReader;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.RetirementPlanApi;
import com.smartbox.investory.retirement.api.model.EditorPreviewResponse;
import com.smartbox.investory.retirement.api.model.PlanEditorInput;
import com.smartbox.investory.retirement.api.model.PlanEditorPreview;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.retirement.planning.application.RetirementPreviewApplicationService;
import com.smartbox.investory.retirement.planning.input.PlanEditorInputNormalizer;
import com.smartbox.investory.retirement.preview.PlanEditorPreviewService;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RetirementPreviewApplicationServiceTest {
  private final ProfileSnapshotReader profiles = mock();
  private final RetirementPlanApi plans = mock();
  private final PlanEditorPreviewService previews = mock();
  private final PlanEditorInputNormalizer normalizer = mock();
  private final Clock clock = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);
  private final RetirementPreviewApplicationService service =
      new RetirementPreviewApplicationService(profiles, plans, previews, normalizer, clock);

  @Test
  void previewDelegatesProfileAssumptionsAndDisplayCurrency() {
    InvestmentProfile profile = mock();
    SimulationAssumptions assumptions = mock();
    PlanEditorPreview expected = mock();
    when(previews.preview(profile, assumptions, CurrencyType.USD)).thenReturn(expected);

    assertThat(service.preview(profile, assumptions, CurrencyType.USD)).isSameAs(expected);
  }

  @Test
  void editorPreviewUsesDefaultAssumptionsWhenNoPlanIsSelected() {
    InvestmentProfile profile = mock();
    PlanEditorInput input = mock();
    SimulationAssumptions normalizedAssumptions = mock();
    PlanEditorPreview expected = mock();
    when(profiles.loadProfile(7L)).thenReturn(profile);
    when(plans.resolvePlanId(7L, null)).thenReturn(Optional.empty());
    when(normalizer.normalize(
            input, SimulationAssumptions.defaults(40, 95, 2026), CurrencyType.USD))
        .thenReturn(new PlanEditorInputNormalizer.Normalized(normalizedAssumptions, List.of()));
    when(normalizedAssumptions.effectiveRentalIncomeGrowthRate())
        .thenReturn(new BigDecimal("0.03"));
    when(normalizedAssumptions.effectiveSpendingGrowthRate()).thenReturn(new BigDecimal("0.04"));
    when(previews.preview(profile, normalizedAssumptions, CurrencyType.USD)).thenReturn(expected);

    EditorPreviewResponse result = service.editorPreview(7L, null, CurrencyType.USD, input);

    assertThat(result.available()).isTrue();
    assertThat(result.warnings()).isEmpty();
    assertThat(result.derived().effectiveRentalGrowth()).isEqualTo("3.0%");
    assertThat(result.derived().effectiveSpendingGrowth()).isEqualTo("4.0%");
    assertThat(result.preview()).isSameAs(expected);
  }

  @Test
  void editorPreviewUsesSelectedPlanAssumptions() {
    InvestmentProfile profile = mock();
    PlanEditorInput input = mock();
    PlanEditorPreview expected = mock();
    var details = mock(com.smartbox.investory.retirement.api.model.PlanDetails.class);
    SimulationAssumptions base = mock();
    SimulationAssumptions normalized = mock();
    when(profiles.loadProfile(7L)).thenReturn(profile);
    when(plans.resolvePlanId(7L, 11L)).thenReturn(Optional.of(11L));
    when(plans.details(7L, 11L)).thenReturn(details);
    when(details.assumptions()).thenReturn(base);
    when(normalizer.normalize(input, base, CurrencyType.PLN))
        .thenReturn(new PlanEditorInputNormalizer.Normalized(normalized, List.of()));
    when(normalized.effectiveRentalIncomeGrowthRate()).thenReturn(BigDecimal.ZERO);
    when(normalized.effectiveSpendingGrowthRate()).thenReturn(new BigDecimal("-0.01"));
    when(previews.preview(profile, normalized, CurrencyType.PLN)).thenReturn(expected);

    EditorPreviewResponse result = service.editorPreview(7L, 11L, CurrencyType.PLN, input);

    assertThat(result.derived().effectiveRentalGrowth()).isEqualTo("0.0%");
    assertThat(result.derived().effectiveSpendingGrowth()).isEqualTo("-1.0%");
    verify(plans).details(7L, 11L);
  }
}
