package com.smartbox.investory.user.application;

import com.smartbox.investory.user.infrastructure.AppUserEntity;
import com.smartbox.investory.user.infrastructure.AppUserRepository;
import com.smartbox.investory.user.infrastructure.ProfileMembershipEntity;
import com.smartbox.investory.user.infrastructure.ProfileMembershipRepository;
import com.smartbox.investory.user.infrastructure.UserInvitationEntity;
import com.smartbox.investory.user.infrastructure.UserInvitationRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserInvitationService {
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Duration INVITATION_LIFETIME = Duration.ofHours(48);

  private final AppUserRepository users;
  private final ProfileMembershipRepository memberships;
  private final UserInvitationRepository invitations;
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

    Long creatorId =
        users.findByUsernameAndActiveTrue(creator).map(AppUserEntity::getId).orElse(null);
    String token = randomToken();
    Instant expiresAt = Instant.now().plus(INVITATION_LIFETIME);
    UserInvitationEntity invitation = new UserInvitationEntity();
    invitation.setEmail(normalizedEmail);
    invitation.setDisplayName(normalizedName);
    invitation.setProfileId(profileId);
    invitation.setProfileRole(profileRole);
    invitation.setTokenHash(hash(token));
    invitation.setExpiresAt(expiresAt);
    invitation.setCreatedBy(creatorId);
    invitation.setCreatedAt(Instant.now());
    invitations.save(invitation);
    return new CreatedInvitation(token, expiresAt);
  }

  @Transactional
  public void accept(String token, String password) {
    if (password == null || password.length() < 12)
      throw new IllegalArgumentException("Password must contain at least 12 characters");
    UserInvitationEntity invitation =
        invitations
            .findByTokenHashForUpdate(hash(token))
            .orElseThrow(() -> new IllegalArgumentException("Invitation is invalid"));
    if (invitation.getConsumedAt() != null)
      throw new IllegalArgumentException("Invitation is invalid");
    if (invitation.getExpiresAt().isBefore(Instant.now()))
      throw new IllegalArgumentException("Invitation has expired");
    String username = normalizeEmail(invitation.getEmail());
    if (users.existsByUsernameIgnoreCase(username))
      throw new IllegalArgumentException("User already exists");
    AppUserEntity user = new AppUserEntity();
    user.setUsername(username);
    user.setEmail(invitation.getEmail());
    user.setDisplayName(invitation.getDisplayName());
    user.setActive(true);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setRole("USER");
    user.setCreatedAt(Instant.now());
    user.setUpdatedAt(Instant.now());
    Long userId = users.save(user).getId();
    ProfileMembershipEntity membership = new ProfileMembershipEntity();
    membership.setUserId(userId);
    membership.setProfileId(invitation.getProfileId());
    membership.setRole(invitation.getProfileRole());
    membership.setCreatedAt(Instant.now());
    memberships.save(membership);
    if (invitations.consumeIfAvailable(invitation.getId()) != 1)
      throw new IllegalArgumentException("Invitation is invalid");
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
}
