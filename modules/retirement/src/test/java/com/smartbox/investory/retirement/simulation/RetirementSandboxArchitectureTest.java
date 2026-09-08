package com.smartbox.investory.retirement.simulation;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.smartbox.investory.retirement.analysis.*;
import com.smartbox.investory.retirement.preview.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RetirementSandboxArchitectureTest {
  @Test
  void sandboxHasNoIndependentSimulationAlgorithm() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/smartbox/investory/retirement/preview/RetirementSandboxSimulationService.java"));

    assertFalse(source.contains("RetirementBucketEngine"));
    assertFalse(source.contains("for (int age"));
    assertFalse(source.contains("failureAge ="));
    assertFalse(source.contains("totalUnfunded ="));
  }
}
