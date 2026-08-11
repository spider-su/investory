package com.example.demo.controllers.ui;

import com.example.demo.services.BenchmarkService;
import com.example.demo.services.PortfolioService;
import com.example.demo.services.dashboard.DashboardPeriod;
import com.example.demo.services.dashboard.DashboardPeriodFilterService;
import com.example.demo.services.models.Benchmark;
import com.example.demo.services.models.InstrumentPerformance;
import com.example.demo.services.models.Portfolio;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequiredArgsConstructor
public class HomeController {

  private final PortfolioService portfolioService;
  private final BenchmarkService benchmarkService;
  private final DashboardPeriodFilterService dashboardPeriodFilterService;

  @GetMapping("/")
  public String home() {
    return "home";
  }

  @GetMapping("/dashboard")
  public String getPortfolioDashboard(
      Model model,
      @RequestParam(required = false) List<Long> accountIds,
      @RequestParam(defaultValue = "false") boolean benchmarkAccountsSubmitted,
      @RequestParam(required = false) String period) {
    long dashboardStartedAt = System.nanoTime();
    DashboardPeriod selectedPeriod = DashboardPeriod.fromUrlValue(period);

    long sectionStartedAt = System.nanoTime();
    Portfolio stats = portfolioService.calculateTotalProfitLoss();
    dashboardPeriodFilterService.apply(stats, selectedPeriod);
    logSectionTiming("portfolio", sectionStartedAt);
    model.addAttribute("stats", stats);
    model.addAttribute("selectedPeriod", selectedPeriod);
    model.addAttribute("periods", DashboardPeriod.values());

    sectionStartedAt = System.nanoTime();
    model.addAttribute("topGainers", topGainers(stats.getPerformancePerSymbol()));
    logSectionTiming("top-gainers", sectionStartedAt);

    sectionStartedAt = System.nanoTime();
    model.addAttribute("topLosers", topLosers(stats.getPerformancePerSymbol()));
    logSectionTiming("top-losers", sectionStartedAt);

    sectionStartedAt = System.nanoTime();
    Benchmark benchmark =
        benchmarkAccountsSubmitted
            ? benchmarkService.calculate(accountIds)
            : benchmarkService.calculate();
    dashboardPeriodFilterService.apply(benchmark, selectedPeriod);
    model.addAttribute("benchmark", benchmark);
    logSectionTiming("benchmark", sectionStartedAt);

    log.info("Dashboard timing: total={} ms", elapsedMillis(dashboardStartedAt));
    return "dashboard";
  }

  private void logSectionTiming(String section, long startedAt) {
    log.info("Dashboard timing: section={} duration={} ms", section, elapsedMillis(startedAt));
  }

  private long elapsedMillis(long startedAt) {
    return (System.nanoTime() - startedAt) / 1_000_000;
  }

  private List<InstrumentPerformance> topGainers(List<InstrumentPerformance> performancePerSymbol) {
    if (performancePerSymbol == null) {
      return List.of();
    }
    return performancePerSymbol.stream()
        .filter(symbol -> symbol.getTotal() >= 0)
        .sorted(Comparator.comparingDouble(InstrumentPerformance::getTotal).reversed())
        .limit(10)
        .toList();
  }

  private List<InstrumentPerformance> topLosers(List<InstrumentPerformance> performancePerSymbol) {
    if (performancePerSymbol == null) {
      return List.of();
    }
    return performancePerSymbol.stream()
        .filter(symbol -> symbol.getTotal() < 0)
        .sorted(Comparator.comparingDouble(InstrumentPerformance::getTotal))
        .limit(10)
        .toList();
  }
}
