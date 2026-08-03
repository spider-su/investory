package com.example.demo.controllers.rest.ghostfolio;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only mapping of Investory ledger operations to Ghostfolio activities. */
@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
public class GhostfolioActivitiesController {

  private final GhostfolioCompatibilityService compatibilityService;

  @GetMapping
  public Map<String, Object> activities(
      @RequestParam(name = "accounts", required = false) String accounts,
      @RequestParam(name = "activityTypes", required = false) String activityTypes,
      @RequestParam(name = "range", required = false) String range,
      @RequestParam(name = "symbol", required = false) String symbol,
      @RequestParam(name = "sortColumn", defaultValue = "date") String sortColumn,
      @RequestParam(name = "sortDirection", defaultValue = "desc") String sortDirection,
      @RequestParam(name = "take", defaultValue = "50") int take,
      @RequestParam(name = "skip", defaultValue = "0") int skip) {
    return compatibilityService.activities(
        accounts, activityTypes, range, symbol, sortColumn, sortDirection, take, skip);
  }
}
