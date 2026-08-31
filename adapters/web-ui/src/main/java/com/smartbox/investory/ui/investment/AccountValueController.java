package com.smartbox.investory.ui.investment;

import com.smartbox.investory.investment.api.InvestmentPerformanceApi;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for canonical selected-account value series. */
@RestController
public class AccountValueController {
  private final InvestmentPerformanceApi performance;

  public AccountValueController(InvestmentPerformanceApi performance) {
    this.performance = performance;
  }

  @GetMapping("/dashboard/account-values")
  InvestmentPerformanceApi.AccountValueView accountValues(
      @RequestParam(required = false) String accountIds) {
    List<Long> ids =
        accountIds == null
            ? null
            : Arrays.stream(accountIds.split(","))
                .filter(value -> !value.isBlank())
                .map(Long::valueOf)
                .toList();
    return performance.loadAccountValues(ids);
  }
}
