package com.smartbox.investory.config;

import com.smartbox.investory.shared.time.ApplicationTime;
import com.smartbox.investory.shared.time.ClockApplicationTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

  public static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Warsaw");

  @Bean
  public Clock applicationClock(@Value("${investory.time.fixed-instant:}") String fixedInstant) {
    if (fixedInstant == null || fixedInstant.isBlank()) {
      return Clock.system(BUSINESS_ZONE);
    }
    return Clock.fixed(Instant.parse(fixedInstant), BUSINESS_ZONE);
  }

  @Bean
  public ApplicationTime applicationTime(Clock applicationClock) {
    return new ClockApplicationTime(applicationClock, BUSINESS_ZONE);
  }
}
