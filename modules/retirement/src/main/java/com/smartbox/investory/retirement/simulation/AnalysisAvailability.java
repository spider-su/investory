package com.smartbox.investory.retirement.simulation;

import java.util.Objects;
import java.util.Optional;

/** Explicit result state for analysis values that may not exist for a horizon. */
public sealed interface AnalysisAvailability<T>
    permits AnalysisAvailability.Available, AnalysisAvailability.Unavailable {
  boolean available();

  Optional<T> value();

  String reason();

  record Available<T>(T content) implements AnalysisAvailability<T> {
    public Available {
      Objects.requireNonNull(content, "Available analysis value is required");
    }

    @Override public boolean available() { return true; }

    @Override public Optional<T> value() { return Optional.of(content); }

    @Override public String reason() { return ""; }
  }

  record Unavailable<T>(String reason) implements AnalysisAvailability<T> {
    public Unavailable {
      if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Reason is required");
    }

    @Override public boolean available() { return false; }

    @Override public Optional<T> value() { return Optional.empty(); }
  }
}
