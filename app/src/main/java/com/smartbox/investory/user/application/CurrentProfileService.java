package com.smartbox.investory.user.application;

import com.smartbox.investory.user.infrastructure.ProfileMembershipRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Resolves the authenticated user's default profile without introducing profile switching. */
@Service
@RequiredArgsConstructor
public class CurrentProfileService {
  private final ProfileMembershipRepository memberships;

  public CurrentProfileResponse find(String username) {
    List<Profile> profiles =
        memberships.findAccessibleProfiles(username).stream()
            .map(row -> new Profile(row.getId(), row.getName(), row.getRole()))
            .toList();
    if (profiles.isEmpty())
      throw new IllegalStateException("Authenticated user has no accessible profile");
    return new CurrentProfileResponse(username, profiles.getFirst(), profiles);
  }

  public record CurrentProfileResponse(
      String username, Profile currentProfile, List<Profile> accessibleProfiles) {}

  public record Profile(long id, String name, String role) {}
}
