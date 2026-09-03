package com.smartbox.investory.investment.web;

import com.smartbox.investory.investment.api.reporting.InvestmentDailyPerformanceApi;
import com.smartbox.investory.investment.api.reporting.model.DailyPerformanceDetail;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/investment/performance")
@Validated
@RequiredArgsConstructor
public class DailyPerformanceController {
  private final InvestmentDailyPerformanceApi performanceApi;

  @GetMapping("/daily-attribution")
  public DailyPerformanceDetail attribution(
      @RequestParam @Positive Long portfolioId,
      @RequestParam LocalDate date,
      @RequestParam(required = false) String accountIds) {
    requirePositivePortfolio(portfolioId);
    Set<Long> ids = Set.copyOf(AccountIdParser.parse(accountIds));
    return performanceApi.load(portfolioId, date, ids);
  }

  private static void requirePositivePortfolio(Long portfolioId) {
    if (portfolioId == null || portfolioId <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "portfolioId must be positive");
    }
  }
}
