package com.example.demo.controllers.rest.ghostfolio;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Read-only adapter for Ghostfolio account endpoints backed by Investory summaries. */
@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
public class GhostfolioAccountController {

  private final GhostfolioCompatibilityService compatibilityService;

  @GetMapping
  public Map<String, Object> accounts() {
    return compatibilityService.accounts();
  }

  @GetMapping("/{accountId}")
  public Map<String, Object> account(@PathVariable String accountId) {
    return compatibilityService
        .account(accountId)
        .orElseThrow(
            () -> new ResponseStatusException(NOT_FOUND, "Unknown Investory account " + accountId));
  }

  @GetMapping("/{accountId}/balances")
  public Map<String, Object> balances(@PathVariable String accountId) {
    return compatibilityService.accountBalances(accountId);
  }
}
