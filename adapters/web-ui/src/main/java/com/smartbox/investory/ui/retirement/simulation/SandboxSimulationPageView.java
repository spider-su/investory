package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.SimulationResult;
import java.math.BigDecimal;
import java.util.List;

public record SandboxSimulationPageView(
    SandboxSimulationForm form, SimulationResult result, List<Row> rows) {
  public record Row(
      int age,
      int year,
      BigDecimal spending,
      BigDecimal rentalIncome,
      BigDecimal pensionIncome,
      BigDecimal cash,
      BigDecimal bonds,
      BigDecimal equities,
      BigDecimal total,
      BigDecimal gap) {}
}
