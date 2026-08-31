package com.smartbox.investory.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

@DisplayName("Time Config")
class TimeConfigTest {

  @DisplayName("provides Application Clock")
  @Test
  void providesApplicationClock() {
    try (var context = new AnnotationConfigApplicationContext(TimeConfig.class)) {
      assertThat(context.getBean(Clock.class)).isNotNull();
    }
  }
}
