package com.example.demo.controllers.rest.ghostfolio;

import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assets")
public class GhostfolioAssetsController {

  @GetMapping(value = "/{languageCode}/site.webmanifest", produces = "application/manifest+json")
  public Map<String, Object> manifest(@PathVariable String languageCode) {
    return Map.ofEntries(
        Map.entry("name", "Investory"),
        Map.entry("short_name", "Investory"),
        Map.entry("description", "Investory portfolio analytics"),
        Map.entry("start_url", "/" + languageCode),
        Map.entry("display", "standalone"),
        Map.entry("background_color", "#ffffff"),
        Map.entry("theme_color", "#ffffff"),
        Map.entry(
            "icons",
            List.of(
                Map.of(
                    "src",
                    "/assets/favicon-192x192.png",
                    "sizes",
                    "192x192",
                    "type",
                    MediaType.IMAGE_PNG_VALUE),
                Map.of(
                    "src",
                    "/assets/favicon-512x512.png",
                    "sizes",
                    "512x512",
                    "type",
                    MediaType.IMAGE_PNG_VALUE))));
  }
}
