package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Formats simulation amounts for the selected planning currency. */
final class SimulationValueFormatter {
  private final RetirementPresentationClient presentation;

  SimulationValueFormatter(RetirementPresentationClient presentation) {
    this.presentation = presentation;
  }

  BigDecimal money(BigDecimal amount, CurrencyType currency) {
    return presentation
        .toDisplay(amount, currency)
        .setScale(2, RoundingMode.HALF_UP)
        .stripTrailingZeros();
  }
}
