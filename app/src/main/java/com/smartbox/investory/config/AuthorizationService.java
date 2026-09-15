package com.smartbox.investory.config;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/** Small application authorization boundary. Profile scope is always part of a decision. */
@Service
public class AuthorizationService {
  private final JdbcTemplate jdbc;
  private final boolean profileOwnershipRequired;

  public AuthorizationService(JdbcTemplate jdbc) {
    this(jdbc, true);
  }

  @Autowired
  public AuthorizationService(
      JdbcTemplate jdbc,
      @Value("${app.security.profile-ownership-required:true}") boolean profileOwnershipRequired) {
    this.jdbc = jdbc;
    this.profileOwnershipRequired = profileOwnershipRequired;
  }

  public boolean canRead(Long profileId, Authentication authentication) {
    return isAdmin(authentication)
        || (!profileOwnershipRequired && authenticated(authentication))
        || hasMembership(profileId, authentication, null);
  }

  public boolean canWrite(Long profileId, Authentication authentication) {
    return isAdmin(authentication)
        || (!profileOwnershipRequired && authenticated(authentication))
        || hasMembership(profileId, authentication, ProfileRole.OWNER);
  }

  public boolean canManageIntegrations(Authentication authentication) {
    return isAdmin(authentication);
  }

  public boolean canAdminister(Authentication authentication) {
    return isAdmin(authentication);
  }

  private boolean hasMembership(
      Long profileId, Authentication authentication, ProfileRole requiredRole) {
    if (profileId == null || authentication == null || !authentication.isAuthenticated())
      return false;
    String role = requiredRole == null ? null : requiredRole.name();
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM investory.profile_memberships pm "
                + "JOIN investory.app_users u ON u.id = pm.user_id "
                + "WHERE pm.profile_id = ? AND u.username = ? AND u.active "
                + "AND (CAST(? AS varchar) IS NULL OR pm.role = ?)",
            Integer.class,
            profileId,
            authentication.getName(),
            role,
            role);
    return count != null && count > 0;
  }

  private static boolean isAdmin(Authentication authentication) {
    return authenticated(authentication)
        && authentication.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
  }

  private static boolean authenticated(Authentication authentication) {
    return authentication != null && authentication.isAuthenticated();
  }

  /** Normalizes Spring's ROLE_ prefix for capability consumers. */
  public static String roleName(Authentication authentication) {
    if (isAdmin(authentication)) return "ADMIN";
    if (authentication == null) return "";
    return authentication.getAuthorities().stream()
        .map(a -> a.getAuthority().replaceFirst("^ROLE_", "").toUpperCase(Locale.ROOT))
        .filter(name -> name.equals("PROFILE_OWNER") || name.equals("PROFILE_USER"))
        .findFirst()
        .orElse("");
  }
}
