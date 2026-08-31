package com.smartbox.investory.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UiVisualRegressionBaselineContractTest {
  private static final Path BASELINES = Path.of("../docs/quality/ui-baselines");

  @Test
  void responsiveProfileBaselinesCoverTheSupportedViewportMatrix() throws Exception {
    Map<String, String> viewports =
        Map.of(
            "desktop", "1920x1080",
            "laptop", "1440x900",
            "tablet", "1024x768",
            "mobile", "390x844");

    for (var viewport : viewports.entrySet()) {
      Path baseline = BASELINES.resolve("profile-" + viewport.getKey() + ".png");
      assertThat(baseline).as("%s baseline at %s", viewport.getKey(), viewport.getValue()).exists();
      assertThat(Files.size(baseline)).isGreaterThan(10_000L);
    }
  }
}
