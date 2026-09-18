package com.smartbox.investory.user.application;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Resolves the authenticated user's default profile without introducing profile switching. */
@Service
@RequiredArgsConstructor
public class CurrentProfileService {
  private final JdbcTemplate jdbc;

  public CurrentProfileResponse find(String username) {
    List<Profile> profiles =
        jdbc.query(
            "SELECT p.id, p.name, pm.role "
                + "FROM investory.profile_memberships pm "
                + "JOIN investory.app_users u ON u.id = pm.user_id "
                + "JOIN investory.portfolios p ON p.id = pm.profile_id "
                + "WHERE u.username = ? AND u.active "
                + "ORDER BY pm.created_at, p.id",
            (rs, rowNum) ->
                new Profile(rs.getLong("id"), rs.getString("name"), rs.getString("role")),
            username);
    if (profiles.isEmpty())
      throw new IllegalStateException("Authenticated user has no accessible profile");
    return new CurrentProfileResponse(username, profiles.getFirst(), profiles);
  }

  public record CurrentProfileResponse(
      String username, Profile currentProfile, List<Profile> accessibleProfiles) {}

  public record Profile(long id, String name, String role) {}
}
