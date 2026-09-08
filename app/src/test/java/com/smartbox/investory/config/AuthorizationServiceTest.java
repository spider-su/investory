package com.smartbox.investory.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTest {
  @Mock private JdbcTemplate jdbc;
  private AuthorizationService authorization;

  @BeforeEach
  void setUp() {
    authorization = new AuthorizationService(jdbc);
  }

  @Test
  void adminIsGlobal() {
    Authentication admin = authentication("admin", "ROLE_ADMIN");

    assertTrue(authorization.canRead(1L, admin));
    assertTrue(authorization.canWrite(2L, admin));
    assertTrue(authorization.canManageIntegrations(admin));
    assertTrue(authorization.canAdminister(admin));
  }

  @Test
  void ownerAndUserAreScopedToMembership() {
    Authentication owner = authentication("owner-a", "ROLE_PROFILE_OWNER");
    Authentication user = authentication("user-a", "ROLE_PROFILE_USER");
    when(jdbc.queryForObject(
            any(String.class), eq(Integer.class), eq(1L), eq("owner-a"), eq("OWNER"), eq("OWNER")))
        .thenReturn(1);
    when(jdbc.queryForObject(
            any(String.class), eq(Integer.class), eq(1L), eq("owner-a"), eq(null), eq(null)))
        .thenReturn(1);
    when(jdbc.queryForObject(
            any(String.class), eq(Integer.class), eq(1L), eq("user-a"), eq(null), eq(null)))
        .thenReturn(1);
    when(jdbc.queryForObject(
            any(String.class), eq(Integer.class), eq(1L), eq("user-a"), eq("OWNER"), eq("OWNER")))
        .thenReturn(0);
    when(jdbc.queryForObject(
            any(String.class), eq(Integer.class), eq(2L), any(String.class), any(), any()))
        .thenReturn(0);

    assertTrue(authorization.canRead(1L, owner));
    assertTrue(authorization.canWrite(1L, owner));
    assertTrue(authorization.canRead(1L, user));
    assertFalse(authorization.canWrite(1L, user));
    assertFalse(authorization.canRead(2L, owner));
    assertFalse(authorization.canRead(2L, user));
    assertFalse(authorization.canManageIntegrations(owner));
    assertFalse(authorization.canManageIntegrations(user));
  }

  private static Authentication authentication(String name, String role) {
    return new UsernamePasswordAuthenticationToken(
        name, "n/a", java.util.List.of(new SimpleGrantedAuthority(role)));
  }
}
