package com.smartbox.investory.ui.investment;

import com.smartbox.investory.investment.api.reporting.InvestmentPerformanceApi;
import com.smartbox.investory.investment.api.reporting.InvestmentPerformanceApi.PerformanceBoardQuery;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for the canonical Investment performance read model. */
@RestController
@RequiredArgsConstructor
public class InvestmentPerformanceController {
  private final InvestmentPerformanceApi performanceApi;

  @GetMapping("/dashboard/performance-board")
  public InvestmentPerformanceApi.PerformanceBoardView performance(
      @RequestParam(required = false) String accountIds,
      @RequestParam(defaultValue = "monthly") String aggregation,
      @RequestParam(defaultValue = "return") String metric,
      @RequestParam(defaultValue = "line") String style,
      @RequestParam(required = false) String period) {
    List<Long> ids =
        accountIds == null
            ? null
            : Stream.of(accountIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Long::valueOf)
                .toList();
    return performanceApi.load(new PerformanceBoardQuery(ids, aggregation, metric, style, period));
  }

  @GetMapping("/dashboard/account-performance")
  public InvestmentPerformanceApi.PerformanceBoardView accountPerformance(
      @RequestParam String accountIds, @RequestParam(defaultValue = "monthly") String aggregation) {
    List<Long> ids =
        Stream.of(accountIds.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .map(Long::valueOf)
            .toList();
    return performanceApi.load(new PerformanceBoardQuery(ids, aggregation, "profit", "bars"));
  }
}
