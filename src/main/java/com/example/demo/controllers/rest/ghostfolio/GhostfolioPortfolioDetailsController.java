package com.example.demo.controllers.rest.ghostfolio;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only compatibility projection for Ghostfolio's portfolio details contract. */
@RestController
@RequestMapping("/api/v1/portfolio")
@RequiredArgsConstructor
public class GhostfolioPortfolioDetailsController {

  private final GhostfolioCompatibilityService compatibilityService;

  @GetMapping("/details")
  public Map<String, Object> details(
      @RequestParam(name = "accounts", required = false) String accountIds,
      @RequestParam(name = "assetClasses", required = false) String ignoredAssetClasses,
      @RequestParam(name = "assetSubClasses", required = false) String ignoredAssetSubClasses,
      @RequestParam(name = "dataSource", required = false) String ignoredDataSource,
      @RequestParam(name = "holdingType", required = false) String ignoredHoldingType,
      @RequestParam(name = "range", required = false) String ignoredRange,
      @RequestParam(name = "symbol", required = false) String requestedSymbol,
      @RequestParam(name = "tags", required = false) String ignoredTags,
      @RequestParam(name = "withMarkets", required = false) Boolean ignoredWithMarkets) {
    return compatibilityService.details(accountIds, requestedSymbol);
  }
}
