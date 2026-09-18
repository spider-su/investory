package com.smartbox.investory.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class CurrentProfileServiceTest {
  @Test
  void returnsTheDeterministicCurrentProfileAndAccessibleProfiles() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    var first = new CurrentProfileService.Profile(7L, "Accounting", "OWNER");
    var second = new CurrentProfileService.Profile(9L, "Other", "USER");
    when(jdbc.query(any(String.class), any(RowMapper.class), eq("user@example.test")))
        .thenReturn(List.of(first, second));

    var result = new CurrentProfileService(jdbc).find("user@example.test");

    assertThat(result.username()).isEqualTo("user@example.test");
    assertThat(result.currentProfile()).isEqualTo(first);
    assertThat(result.accessibleProfiles()).containsExactly(first, second);
  }

  @Test
  void rejectsAnAuthenticatedUserWithoutAnAccessibleProfile() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.query(any(String.class), any(RowMapper.class), eq("orphan@example.test")))
        .thenReturn(List.of());

    assertThatThrownBy(() -> new CurrentProfileService(jdbc).find("orphan@example.test"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no accessible profile");
  }
}
