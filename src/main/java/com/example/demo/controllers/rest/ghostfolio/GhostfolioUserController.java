package com.example.demo.controllers.rest.ghostfolio;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Current-user adapter for the Ghostfolio Angular application.
 *
 * <p>The frontend needs a user and settings object before it will enter authenticated routes. This
 * adapter represents the single local Investory user and does not create a second user store.
 */
@RestController
@RequestMapping("/api/v1")
public class GhostfolioUserController {

  private final String baseCurrency;
  private final String displayName;
  private final GhostfolioCompatibilityService compatibilityService;
  private final Instant startedAt = Instant.now();

  public GhostfolioUserController(
      @Value("${investory.base-currency:USD}") String baseCurrency,
      @Value("${investory.ghostfolio.display-name:Investory}") String displayName,
      GhostfolioCompatibilityService compatibilityService) {
    this.baseCurrency = baseCurrency;
    this.displayName = displayName;
    this.compatibilityService = compatibilityService;
  }

  @GetMapping("/user")
  public Map<String, Object> currentUser() {
    Map<String, Object> settings =
        Map.ofEntries(
            Map.entry("baseCurrency", baseCurrency),
            Map.entry("currency", baseCurrency),
            Map.entry("locale", "en-US"),
            Map.entry("dateFormat", "yyyy-MM-dd"),
            Map.entry("isDarkMode", false),
            Map.entry("isRestrictedView", false),
            Map.entry("isZenMode", false),
            Map.entry("language", "en"),
            Map.entry("showExperimentalFeatures", true));

    Map<String, Object> accountsResponse = compatibilityService.accounts();

    return Map.ofEntries(
        Map.entry("id", "investory-local-user"),
        Map.entry("name", displayName),
        Map.entry("alias", displayName),
        Map.entry("role", "ADMIN"),
        Map.entry("provider", "ANONYMOUS"),
        Map.entry("createdAt", startedAt.toString()),
        Map.entry("updatedAt", startedAt.toString()),
        Map.entry("activitiesCount", accountsResponse.get("activitiesCount")),
        Map.entry("accounts", accountsResponse.get("accounts")),
        Map.entry("access", List.of()),
        Map.entry("permissions", List.of("read")),
        Map.entry("settings", settings),
        // Older Ghostfolio clients accessed the Prisma relation using this casing.
        Map.entry("Settings", Map.of("settings", settings)),
        Map.entry(
            "subscription",
            Map.of(
                "type", "Basic",
                "expiresAt", "",
                "isActive", true)));
  }

  @GetMapping("/user/settings")
  public Map<String, Object> userSettings() {
    return Map.of(
        "settings",
        Map.ofEntries(
            Map.entry("baseCurrency", baseCurrency),
            Map.entry("currency", baseCurrency),
            Map.entry("locale", "en-US"),
            Map.entry("dateFormat", "yyyy-MM-dd"),
            Map.entry("isDarkMode", false),
            Map.entry("isRestrictedView", false),
            Map.entry("isZenMode", false),
            Map.entry("language", "en"),
            Map.entry("showExperimentalFeatures", true)));
  }
}
