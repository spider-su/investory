package com.smartbox.investory.marketradar.domain;

import java.time.Instant;
import java.util.List;

public record MediaObservation(
    String externalId,
    MediaSourceType sourceType,
    String source,
    String author,
    String headline,
    String url,
    Instant publishedAt,
    List<String> symbols,
    OpinionStance stance) {

  public MediaObservation {
    symbols = List.copyOf(symbols);
    stance = stance == null ? OpinionStance.UNKNOWN : stance;
  }
}
