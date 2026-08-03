package com.example.demo.controllers.rest.ghostfolio;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Ghostfolio v2 portfolio adapter backed by Investory history and portfolio calculations. */
@RestController
@RequestMapping("/api/v2/portfolio")
@RequiredArgsConstructor
public class GhostfolioPortfolioController {

  private final GhostfolioCompatibilityService compatibilityService;

  @GetMapping("/performance")
  public Map<String, Object> performance(
      @RequestParam(name = "range", defaultValue = "max") String range,
      @RequestParam(name = "accounts", required = false) String accounts) {
    return compatibilityService.performance(accounts, range);
  }

  static double percentage(double value, double denominator) {
    return Math.abs(denominator) < 0.0000001d ? 0.0d : value / Math.abs(denominator);
  }
}
