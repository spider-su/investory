package com.example.demo.controllers.rest.ghostfolio;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Compatibility endpoint for the Ghostfolio Angular bootstrap request.
 *
 * <p>This deliberately exposes only frontend configuration. Portfolio calculations remain in
 * Investory services.
 */
@RestController
@RequestMapping("/api/v1")
public class GhostfolioInfoController {

  private final String applicationVersion;
  private final String baseCurrency;

  public GhostfolioInfoController(
      @Value("${spring.application.version:dev}") String applicationVersion,
      @Value("${investory.base-currency:USD}") String baseCurrency) {
    this.applicationVersion = applicationVersion;
    this.baseCurrency = baseCurrency;
  }

  @GetMapping("/info")
  public Map<String, Object> info() {
    return Map.ofEntries(
        Map.entry("applicationVersion", applicationVersion),
        Map.entry("baseCurrency", baseCurrency),
        Map.entry("currency", baseCurrency),
        Map.entry("locale", "en-US"),
        Map.entry("platform", "Investory"),
        Map.entry("isUserSignupEnabled", false),
        Map.entry("isSignUpEnabled", false),
        Map.entry("isDemoMode", false),
        Map.entry("isPremium", false),
        Map.entry("authProviders", List.of("ANONYMOUS")),
        Map.entry("globalPermissions", List.of()),
        Map.entry(
            "features",
            Map.of(
                "authToken", true,
                "oidc", false,
                "subscription", false)));
  }
}
