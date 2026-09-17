package com.smartbox.investory.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartbox.investory.profile.api.model.EmploymentPeriod;
import com.smartbox.investory.profile.api.model.EmploymentType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmploymentContextResolverTest {
  private final EmploymentContextResolver resolver = new EmploymentContextResolver();
  private final List<EmploymentPeriod> history =
      List.of(
          new EmploymentPeriod(
              1L, EmploymentType.UOP, LocalDate.of(2022, 1, 1), LocalDate.of(2024, 6, 30)),
          new EmploymentPeriod(2L, EmploymentType.JDG, LocalDate.of(2023, 1, 1), null),
          new EmploymentPeriod(3L, EmploymentType.UOP, LocalDate.of(2025, 2, 1), null));

  @Test
  void preservesSeparatePeriodsAndTheUopGap() {
    assertThat(resolver.resolve(history, LocalDate.of(2022, 6, 1)))
        .isEqualTo(new com.smartbox.investory.profile.api.model.EmploymentContext(true, false));
    assertThat(resolver.resolve(history, LocalDate.of(2023, 6, 1)))
        .isEqualTo(new com.smartbox.investory.profile.api.model.EmploymentContext(true, true));
    assertThat(resolver.resolve(history, LocalDate.of(2024, 12, 1)))
        .isEqualTo(new com.smartbox.investory.profile.api.model.EmploymentContext(false, true));
    assertThat(resolver.resolve(history, LocalDate.of(2025, 6, 1)))
        .isEqualTo(new com.smartbox.investory.profile.api.model.EmploymentContext(true, true));
  }

  @Test
  void boundariesAreInclusiveAndInvalidRangesAreRejected() {
    assertThat(resolver.resolve(history, LocalDate.of(2024, 6, 30)).hasUop()).isTrue();
    assertThat(resolver.resolve(history, LocalDate.of(2024, 7, 1)).hasUop()).isFalse();
    assertThatThrownBy(
            () ->
                new EmploymentPeriod(
                    4L, EmploymentType.UOP, LocalDate.of(2025, 1, 2), LocalDate.of(2025, 1, 1)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
