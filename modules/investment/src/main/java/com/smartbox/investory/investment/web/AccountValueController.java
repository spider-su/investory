package com.smartbox.investory.investment.web;

import com.smartbox.investory.investment.api.reporting.InvestmentPerformanceApi;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for canonical selected-account value series. */
@RestController
@RequestMapping("/api/v1/investment/performance")
public class AccountValueController {
  private final InvestmentPerformanceApi performance;

  public AccountValueController(InvestmentPerformanceApi performance) {
    this.performance = performance;
  }

  @GetMapping("/account-values")
  public InvestmentPerformanceApi.AccountValueView accountValues(
      @RequestParam(required = false) String accountIds, @RequestParam Long portfolioId) {
    List<Long> ids = accountIds == null ? null : AccountIdParser.parse(accountIds);
    return performance.loadAccountValues(portfolioId, ids);
  }
}
