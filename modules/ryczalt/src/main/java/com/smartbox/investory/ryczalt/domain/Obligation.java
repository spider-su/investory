package com.smartbox.investory.ryczalt.domain;

import java.util.Objects;

/** Accounting obligation; payment matching is intentionally not modeled yet. */
public record Obligation(ObligationType type) {
  public Obligation {
    type = Objects.requireNonNull(type, "type");
  }
}
