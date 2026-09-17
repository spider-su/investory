package com.smartbox.investory.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;

public class TokenAuthenticationService {
  private static final String HMAC = "HmacSHA256";
  private final String secret;
  private final Duration lifetime;

  public TokenAuthenticationService(
      @Value("${app.security.token-secret}") String secret,
      @Value("${app.security.token-lifetime:PT12H}") Duration lifetime) {
    if (secret == null || secret.length() < 32)
      throw new IllegalArgumentException("Token secret must be at least 32 characters");
    this.secret = secret;
    this.lifetime = lifetime;
  }

  public String issue(UserDetails user) {
    String payload = user.getUsername() + "|" + Instant.now().plus(lifetime).toEpochMilli();
    String encoded =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    return encoded + "." + sign(encoded);
  }

  public String subject(String token) {
    try {
      String[] parts = token.split("\\.", -1);
      if (parts.length != 2
          || !MessageDigest.isEqual(
              parts[1].getBytes(StandardCharsets.US_ASCII),
              sign(parts[0]).getBytes(StandardCharsets.US_ASCII))) return null;
      String[] payload =
          new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8)
              .split("\\|", -1);
      return payload.length == 2
              && Instant.ofEpochMilli(Long.parseLong(payload[1])).isAfter(Instant.now())
          ? payload[0]
          : null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  private String sign(String value) {
    try {
      Mac mac = Mac.getInstance(HMAC);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC));
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("Cannot sign authentication token", e);
    }
  }
}
