package com.smartbox.investory.investment.web;

import com.smartbox.investory.investment.api.reporting.InvestmentDailyPerformanceApi;
import com.smartbox.investory.investment.api.reporting.model.DailyPerformanceDetail;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/investment/performance")
@RequiredArgsConstructor
public class DailyPerformanceController {
  private final InvestmentDailyPerformanceApi performanceApi;

  @GetMapping("/daily-attribution")
  public DailyPerformanceDetail attribution(
      @RequestParam LocalDate date, @RequestParam(required = false) String accountIds) {
    Set<Long> ids = Set.copyOf(AccountIdParser.parse(accountIds));
    return performanceApi.load(date, ids);
  }
}
