package com.example.demo.controllers.rest.ghostfolio;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Development-only authentication shim expected by the Ghostfolio frontend.
 *
 * <p>The returned token is intentionally opaque. Investory's Ghostfolio compatibility security
 * chain accepts it only while the {@code ghostfolio} Spring profile is enabled.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class GhostfolioAuthController {

  @PostMapping("/anonymous")
  public ResponseEntity<Map<String, Object>> anonymous(
      @RequestBody(required = false) Map<String, Object> ignoredBody) {
    String token = "investory-dev-" + UUID.randomUUID();

    return ResponseEntity.ok(
        Map.of(
            "accessToken",
            token,
            "expiresIn",
            86400,
            "expiresAt",
            Instant.now().plusSeconds(86400).toString(),
            "tokenType",
            "Bearer"));
  }
}
