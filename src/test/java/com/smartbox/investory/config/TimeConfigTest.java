package com.smartbox.investory.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class TimeConfigTest {

  @Test
  void providesApplicationClock() {
    try (var context = new AnnotationConfigApplicationContext(TimeConfig.class)) {
      assertThat(context.getBean(Clock.class)).isNotNull();
    }
  }
}
