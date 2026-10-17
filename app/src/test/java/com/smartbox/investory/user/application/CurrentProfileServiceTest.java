package com.smartbox.investory.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.user.infrastructure.ProfileMembershipRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class CurrentProfileServiceTest {
  @Test
  void returnsTheDeterministicCurrentProfileAndAccessibleProfiles() {
    ProfileMembershipRepository memberships = mock(ProfileMembershipRepository.class);
    var first = new CurrentProfileService.Profile(7L, "Accounting", "OWNER");
    var second = new CurrentProfileService.Profile(9L, "Other", "USER");
    var firstRow = row(first);
    var secondRow = row(second);
    when(memberships.findAccessibleProfiles("user@example.test"))
        .thenReturn(List.of(firstRow, secondRow));

    var result = new CurrentProfileService(memberships).find("user@example.test");

    assertThat(result.username()).isEqualTo("user@example.test");
    assertThat(result.currentProfile()).isEqualTo(first);
    assertThat(result.accessibleProfiles()).containsExactly(first, second);
  }

  @Test
  void rejectsAnAuthenticatedUserWithoutAnAccessibleProfile() {
    ProfileMembershipRepository memberships = mock(ProfileMembershipRepository.class);
    when(memberships.findAccessibleProfiles("orphan@example.test")).thenReturn(List.of());

    assertThatThrownBy(() -> new CurrentProfileService(memberships).find("orphan@example.test"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no accessible profile");
  }

  private static ProfileMembershipRepository.ProfileRow row(CurrentProfileService.Profile profile) {
    return new ProfileMembershipRepository.ProfileRow() {
      public Long getId() {
        return profile.id();
      }

      public String getName() {
        return profile.name();
      }

      public String getRole() {
        return profile.role();
      }
    };
  }
}
