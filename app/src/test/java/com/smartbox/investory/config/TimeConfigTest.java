package com.smartbox.investory.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.shared.time.ApplicationTime;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

@DisplayName("Time Config")
class TimeConfigTest {

  @DisplayName("provides real application time by default")
  @Test
  void providesApplicationClock() {
    try (var context = new AnnotationConfigApplicationContext(TimeConfig.class)) {
      assertThat(context.getBean(Clock.class)).isNotNull();
      assertThat(context.getBean(ApplicationTime.class).businessZone().getId())
          .isEqualTo("Europe/Warsaw");
    }
  }

  @DisplayName("provides fixed application time when configured")
  @Test
  void providesFixedApplicationTime() {
    try (var context = new AnnotationConfigApplicationContext()) {
      TestPropertyValues.of("investory.time.fixed-instant=2026-08-31T12:00:00Z").applyTo(context);
      context.register(TimeConfig.class);
      context.refresh();

      assertThat(context.getBean(ApplicationTime.class).now())
          .isEqualTo(Instant.parse("2026-08-31T12:00:00Z"));
    }
  }
}
