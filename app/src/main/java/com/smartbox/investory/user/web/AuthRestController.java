package com.smartbox.investory.user.web;

import com.smartbox.investory.config.TokenAuthenticationService;
import com.smartbox.investory.user.application.CurrentProfileService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthRestController {
  private final AuthenticationManager authenticationManager;
  private final TokenAuthenticationService tokens;
  private final CurrentProfileService profiles;

  @Value("${app.security.token-lifetime:PT12H}")
  private java.time.Duration lifetime;

  @Value("${app.security.web-session-cookie-name:investory_web_session}")
  private String webSessionCookieName;

  @Value("${app.security.web-session-cookie-secure:true}")
  private boolean webSessionCookieSecure;

  @Value("${app.security.web-session-cookie-same-site:Lax}")
  private String webSessionCookieSameSite;

  @PostMapping("/login")
  public LoginResponse login(
      @Valid @RequestBody LoginRequest request,
      @RequestHeader(value = "X-Investory-Client", required = false) String client,
      HttpServletResponse response) {
    try {
      var authentication =
          authenticationManager.authenticate(
              UsernamePasswordAuthenticationToken.unauthenticated(
                  request.loginName(), request.password()));
      String token =
          tokens.issue(
              (org.springframework.security.core.userdetails.UserDetails)
                  authentication.getPrincipal());
      if ("web".equalsIgnoreCase(client)) {
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(token, lifetime).toString());
        return new LoginResponse(null, null, "Cookie", lifetime.toSeconds());
      }
      return new LoginResponse(token, token, "Bearer", lifetime.toSeconds());
    } catch (org.springframework.security.core.AuthenticationException e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }
  }

  @PostMapping("/logout")
  public void logout(HttpServletResponse response) {
    response.addHeader(
        HttpHeaders.SET_COOKIE, sessionCookie("", java.time.Duration.ZERO).toString());
  }

  private ResponseCookie sessionCookie(String value, java.time.Duration maxAge) {
    return ResponseCookie.from(webSessionCookieName, value)
        .httpOnly(true)
        .secure(webSessionCookieSecure)
        .sameSite(webSessionCookieSameSite)
        .path("/")
        .maxAge(maxAge)
        .build();
  }

  @org.springframework.web.bind.annotation.GetMapping("/me")
  public CurrentProfileService.CurrentProfileResponse me(Authentication authentication) {
    return profiles.find(authentication.getName());
  }

  public record LoginRequest(String username, String email, @NotBlank String password) {
    String loginName() {
      String value = username == null || username.isBlank() ? email : username;
      if (value == null || value.isBlank())
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username or email is required");
      return value;
    }
  }

  public record LoginResponse(String token, String accessToken, String tokenType, long expiresIn) {}
}
