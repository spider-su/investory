package com.smartbox.investory.investment.web;

import com.smartbox.investory.investment.accounting.PortfolioService;
import com.smartbox.investory.investment.accounting.model.DailyPerformanceDetail;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DailyPerformanceController {
  private final PortfolioService portfolioService;

  @GetMapping("/dashboard/daily-attribution")
  public DailyPerformanceDetail attribution(
      @RequestParam LocalDate date, @RequestParam(required = false) String accountIds) {
    Set<Long> ids =
        accountIds == null || accountIds.isBlank()
            ? Set.of()
            : Arrays.stream(accountIds.split(","))
                .map(String::trim)
                .map(Long::valueOf)
                .collect(Collectors.toSet());
    return portfolioService.dailyPerformanceDetail(date, ids);
  }
}
