package com.smartbox.investory.retirement.api.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record EditorPreviewResponse(
    boolean available,
    List<PlanInputWarning> warnings,
    DerivedValues derived,
    PlanEditorPreview preview,
    PlanningProfileMoney displayProfile,
    Map<String, BigDecimal> displayMoney,
    Map<Long, BigDecimal> displayEventAmounts) {
  public EditorPreviewResponse(
      boolean available,
      List<PlanInputWarning> warnings,
      DerivedValues derived,
      PlanEditorPreview preview) {
    this(available, warnings, derived, preview, null, Map.of(), Map.of());
  }

  public record DerivedValues(String effectiveRentalGrowth, String effectiveSpendingGrowth) {}
}
