package com.smartbox.investory.testsupport.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class MutableApplicationTimeTest {

  @Test
  void controlsApplicationTimeAndClockTogether() {
    var time =
        MutableApplicationTime.fixed(
            Instant.parse("2026-09-04T22:30:00Z"), ZoneId.of("Europe/Warsaw"));

    assertThat(time.today()).hasToString("2026-09-05");
    assertThat(time.clock().instant()).isEqualTo(time.now());

    time.advance(Duration.ofHours(2));

    assertThat(time.now()).isEqualTo(Instant.parse("2026-09-05T00:30:00Z"));
    assertThat(time.clock().instant()).isEqualTo(time.now());
  }
}
