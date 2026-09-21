package com.smartbox.investory.ryczalt.parity;

import java.util.List;

/** Diagnostic result container for the old-versus-new certified-period harness. */
public record ParityReport(List<ParityDifference> differences) {
  public ParityReport {
    differences = List.copyOf(differences);
  }

  public boolean isParity() {
    return differences.isEmpty();
  }
}
