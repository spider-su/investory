package com.smartbox.investory.ui.retirement;

import java.util.Map;

/** Raw, user-facing plan editor values. Percentages are percentage points, never decimal rates. */
public record PlanEditorInput(Map<String, String> values) {
  public static PlanEditorInput from(Map<String, String> values) {
    return new PlanEditorInput(Map.copyOf(values));
  }

  public String value(String name) {
    return values.get(name);
  }
}
