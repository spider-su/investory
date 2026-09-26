package com.smartbox.investory.ryczalt.pit28;

import java.util.List;

public record Pit28Readiness(Pit28ReadinessStatus status, List<Pit28Issue> issues) {
  public Pit28Readiness {
    issues = List.copyOf(issues == null ? List.of() : issues);
  }
}
