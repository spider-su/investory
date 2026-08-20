package com.smartbox.investory.investment.web;

import com.smartbox.investory.config.BuildMetadata;
import com.smartbox.investory.investment.reporting.dashboard.application.DashboardFacade;
import com.smartbox.investory.investment.reporting.dashboard.application.DashboardPageView;
import com.smartbox.investory.investment.reporting.dashboard.application.DashboardQuery;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomeController {

  private final DashboardFacade dashboardFacade;
  private final BuildMetadata buildMetadata;

  public HomeController(DashboardFacade dashboardFacade) {
    this(dashboardFacade, BuildMetadata.development());
  }

  @Autowired
  public HomeController(DashboardFacade dashboardFacade, BuildMetadata buildMetadata) {
    this.dashboardFacade = dashboardFacade;
    this.buildMetadata = buildMetadata;
  }

  @Value("${app.ui.yahoo-url:}")
  private String yahooFinanceUrl;

  @GetMapping("/")
  public String home() {
    return "home";
  }

  @GetMapping("/dashboard")
  public String getPortfolioDashboard(
      Model model,
      @RequestParam(required = false) List<Long> accountIds,
      @RequestParam(defaultValue = "false") boolean benchmarkAccountsSubmitted,
      @RequestParam(required = false) String period,
      @RequestParam(defaultValue = "1") Long portfolioId) {
    DashboardPageView dashboard =
        dashboardFacade.loadDashboard(
            new DashboardQuery(accountIds, benchmarkAccountsSubmitted, period, portfolioId));
    model.addAttribute("dashboard", dashboard);
    model.addAttribute("selectedPeriod", dashboard.selectedPeriod());
    model.addAttribute("periods", dashboard.periods());
    model.addAttribute("portfolioId", portfolioId);
    model.addAttribute("yahooFinanceUrl", yahooFinanceUrl);
    model.addAttribute("buildMetadata", buildMetadata);
    return "dashboard";
  }
}
