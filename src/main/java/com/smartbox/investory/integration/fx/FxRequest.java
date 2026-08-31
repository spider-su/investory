package com.smartbox.investory.integration.fx;

import com.smartbox.investory.infrastructure.CurrencyType;
import java.time.LocalDate;
import java.util.List;

public record FxRequest(CurrencyType base, List<CurrencyType> targets, LocalDate effectiveDate) {
  public FxRequest {
    targets = targets == null ? List.of() : List.copyOf(targets);
  }
}
