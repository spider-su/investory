package com.smartbox.investory.user.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserInvitationService {
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Duration INVITATION_LIFETIME = Duration.ofHours(48);

  private final JdbcTemplate jdbc;
  private final PasswordEncoder passwordEncoder;

  public CreatedInvitation create(
      String email, String displayName, long profileId, String profileRole, String creator) {
    String normalizedEmail = normalizeEmail(email);
    String normalizedName = displayName == null ? "" : displayName.trim();
    if (normalizedName.isBlank()) throw new IllegalArgumentException("Display name is required");
    if (normalizedEmail.length() > 64)
      throw new IllegalArgumentException("Email must be at most 64 characters");
    if (!profileRole.equals("OWNER") && !profileRole.equals("USER"))
      throw new IllegalArgumentException("Profile role must be OWNER or USER");

    var creatorIds =
        jdbc.query(
            "SELECT id FROM investory.app_users WHERE username = ? AND active",
            (rs, rowNum) -> rs.getLong("id"),
            creator);
    Long creatorId = creatorIds.isEmpty() ? null : creatorIds.getFirst();
    String token = randomToken();
    Instant expiresAt = Instant.now().plus(INVITATION_LIFETIME);
    jdbc.update(
        "INSERT INTO investory.app_user_invitations "
            + "(email, display_name, profile_id, profile_role, token_hash, expires_at, created_by) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
        normalizedEmail,
        normalizedName,
        profileId,
        profileRole,
        hash(token),
        expiresAt,
        creatorId);
    return new CreatedInvitation(token, expiresAt);
  }

  @Transactional
  public void accept(String token, String password) {
    if (password == null || password.length() < 12)
      throw new IllegalArgumentException("Password must contain at least 12 characters");
    Invitation invitation;
    try {
      invitation =
          jdbc.queryForObject(
              "SELECT id, email, display_name, profile_id, profile_role, expires_at "
                  + "FROM investory.app_user_invitations WHERE token_hash = ? FOR UPDATE",
              (rs, rowNum) ->
                  new Invitation(
                      rs.getLong("id"),
                      rs.getString("email"),
                      rs.getString("display_name"),
                      rs.getLong("profile_id"),
                      rs.getString("profile_role"),
                      rs.getTimestamp("expires_at").toInstant()),
              hash(token));
    } catch (EmptyResultDataAccessException e) {
      throw new IllegalArgumentException("Invitation is invalid");
    }
    if (invitation.expiresAt().isBefore(Instant.now()))
      throw new IllegalArgumentException("Invitation has expired");
    String username = normalizeEmail(invitation.email());
    Integer existing =
        jdbc.queryForObject(
            "SELECT count(*) FROM investory.app_users WHERE lower(username) = lower(?)",
            Integer.class,
            username);
    if (existing != null && existing > 0) throw new IllegalArgumentException("User already exists");
    jdbc.update(
        "INSERT INTO investory.app_users "
            + "(username, email, display_name, active, password_hash, role) "
            + "VALUES (?, ?, ?, true, ?, 'USER')",
        username,
        invitation.email(),
        invitation.displayName(),
        passwordEncoder.encode(password));
    Long userId =
        jdbc.queryForObject(
            "SELECT id FROM investory.app_users WHERE username = ?", Long.class, username);
    jdbc.update(
        "INSERT INTO investory.profile_memberships (user_id, profile_id, role) VALUES (?, ?, ?)",
        userId,
        invitation.profileId(),
        invitation.profileRole());
    jdbc.update(
        "UPDATE investory.app_user_invitations SET consumed_at = now() WHERE id = ?",
        invitation.id());
  }

  private static String normalizeEmail(String value) {
    String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    if (!normalized.contains("@") || normalized.length() < 5)
      throw new IllegalArgumentException("Valid email is required");
    return normalized;
  }

  private static String randomToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  private static String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public record CreatedInvitation(String token, Instant expiresAt) {}

  private record Invitation(
      long id,
      String email,
      String displayName,
      long profileId,
      String profileRole,
      Instant expiresAt) {}
}
