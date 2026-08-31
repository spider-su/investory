package com.smartbox.investory.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Retirement Analysis Architecture")
class RetirementAnalysisArchitectureTest {
  @DisplayName("analysis Controller Consumes Projection Context Instead Of Simulation Engine")
  @Test
  void analysisControllerConsumesProjectionContextInsteadOfSimulationEngine() throws Exception {
    String controller =
        Files.readString(
            Path.of(
                "../adapters/web-ui/src/main/java/com/smartbox/investory/ui/retirement/analysis/RetirementAnalysisController.java"));
    String simulationController =
        Files.readString(
            Path.of(
                "../adapters/web-ui/src/main/java/com/smartbox/investory/ui/retirement/simulation/RetirementSimulationController.java"));

    assertTrue(controller.contains("projections.load"));
    assertTrue(controller.contains("analyses.analyze"));
    assertFalse(controller.contains("compareScenarios"));
    assertFalse(controller.contains("RetirementSimulation"));
    assertFalse(simulationController.contains("@GetMapping(\"/analysis\")"));
  }
}
