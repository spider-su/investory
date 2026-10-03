package com.smartbox.investory.marketradar.domain;

import java.time.LocalDate;
import java.util.List;

public record RadarSnapshot(
    String symbol,
    LocalDate date,
    RadarState state,
    double close,
    Double return20d,
    Double return60d,
    Double relativeVolume20d,
    Double distanceSma50,
    Double rsi14,
    List<String> reasons) {

  public RadarSnapshot {
    reasons = reasons == null ? List.of() : List.copyOf(reasons);
  }
}
