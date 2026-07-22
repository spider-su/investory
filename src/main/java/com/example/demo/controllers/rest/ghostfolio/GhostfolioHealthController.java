package com.example.demo.controllers.rest.ghostfolio;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal Ghostfolio health endpoint used by the frontend and local diagnostics.
 */
@RestController
@RequestMapping("/api/v1")
public class GhostfolioHealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "OK");
    }
}
