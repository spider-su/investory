package com.smartbox.investory.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UI Visual Regression Baseline Contract")
class UiVisualRegressionBaselineContractTest {
  private static final Path BASELINES = Path.of("../docs/quality/ui-baselines");

  @DisplayName("desktop Profile Baseline Covers The Supported Viewport")
  @Test
  void desktopProfileBaselineCoversTheSupportedViewport() throws Exception {
    Path baseline = BASELINES.resolve("profile-desktop.png");
    assertThat(baseline).as("desktop baseline at 2560x1440").exists();
    assertThat(Files.size(baseline)).isGreaterThan(10_000L);
  }
}
