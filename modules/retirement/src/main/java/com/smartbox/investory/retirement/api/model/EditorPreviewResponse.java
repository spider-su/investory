package com.smartbox.investory.retirement.api.model;

import java.util.List;

public record EditorPreviewResponse(
    boolean available,
    List<PlanInputWarning> warnings,
    DerivedValues derived,
    PlanEditorPreview preview) {
  public record DerivedValues(String effectiveRentalGrowth, String effectiveSpendingGrowth) {}
}
