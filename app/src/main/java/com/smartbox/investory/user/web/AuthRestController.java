package com.smartbox.investory.user.web;

import com.smartbox.investory.config.TokenAuthenticationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthRestController {
  private final AuthenticationManager authenticationManager;
  private final TokenAuthenticationService tokens;

  @Value("${app.security.token-lifetime:PT12H}")
  private java.time.Duration lifetime;

  @PostMapping("/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    try {
      var authentication =
          authenticationManager.authenticate(
              UsernamePasswordAuthenticationToken.unauthenticated(
                  request.loginName(), request.password()));
      String token =
          tokens.issue(
              (org.springframework.security.core.userdetails.UserDetails)
                  authentication.getPrincipal());
      return new LoginResponse(token, token, "Bearer", lifetime.toSeconds());
    } catch (org.springframework.security.core.AuthenticationException e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }
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
