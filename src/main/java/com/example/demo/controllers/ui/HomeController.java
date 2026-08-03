package com.example.demo.controllers.ui;

import com.example.demo.services.BenchmarkService;
import com.example.demo.services.PortfolioService;
import com.example.demo.services.models.InstrumentPerformance;
import com.example.demo.services.models.Portfolio;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class HomeController {

  private final PortfolioService portfolioService;
  private final BenchmarkService benchmarkService;

  @GetMapping("/")
  public String home() {
    return "home";
  }

  @GetMapping("/dashboard")
  public String getPortfolioDashboard(
      Model model,
      @RequestParam(required = false) List<Long> accountIds,
      @RequestParam(defaultValue = "false") boolean benchmarkAccountsSubmitted) {
    Portfolio stats = portfolioService.calculateTotalProfitLoss();
    model.addAttribute("stats", stats);
    model.addAttribute("topGainers", topGainers(stats.getPerformancePerSymbol()));
    model.addAttribute("topLosers", topLosers(stats.getPerformancePerSymbol()));
    model.addAttribute(
        "benchmark",
        benchmarkAccountsSubmitted
            ? benchmarkService.calculate(accountIds)
            : benchmarkService.calculate());
    return "dashboard";
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
