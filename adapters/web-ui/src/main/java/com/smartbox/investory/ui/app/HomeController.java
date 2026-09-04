package com.smartbox.investory.ui.app;

import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi.DashboardPageView;
import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi.DashboardQuery;
import com.smartbox.investory.ui.common.BuildMetadata;
import com.smartbox.investory.ui.investment.InvestmentDashboardClient;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomeController {

  private final InvestmentDashboardClient investmentDashboardFacade;
  private final BuildMetadata buildMetadata;

  public HomeController(InvestmentDashboardClient investmentDashboardFacade) {
    this(investmentDashboardFacade, BuildMetadata.development());
  }

  @Autowired
  public HomeController(
      InvestmentDashboardClient investmentDashboardFacade, BuildMetadata buildMetadata) {
    this.investmentDashboardFacade = investmentDashboardFacade;
    this.buildMetadata = buildMetadata;
  }

  @Value("${app.ui.yahoo-url:}")
  private String yahooFinanceUrl;

  @GetMapping("/")
  public String home() {
    return "home";
  }

  @GetMapping("/dashboard")
  public String getDashboard(
      Model model,
      @RequestParam(required = false) List<Long> accountIds,
      @RequestParam(defaultValue = "false") boolean benchmarkAccountsSubmitted,
      @RequestParam(defaultValue = "YTD") String period,
      @RequestParam Long portfolioId) {
    return renderDashboard(model, accountIds, benchmarkAccountsSubmitted, period, portfolioId);
  }

  @GetMapping("/portfolios/{portfolioId}/dashboard")
  public String getPortfolioDashboard(
      Model model,
      @RequestParam(required = false) List<Long> accountIds,
      @RequestParam(defaultValue = "false") boolean benchmarkAccountsSubmitted,
      @RequestParam(defaultValue = "YTD") String period,
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId) {
    return renderDashboard(model, accountIds, benchmarkAccountsSubmitted, period, portfolioId);
  }

  private String renderDashboard(
      Model model,
      List<Long> accountIds,
      boolean benchmarkAccountsSubmitted,
      String period,
      Long portfolioId) {
    DashboardPeriod selectedPeriod = DashboardPeriod.fromUrlValue(period);
    DashboardPageView dashboard =
        investmentDashboardFacade.loadDashboard(
            new DashboardQuery(
                accountIds, benchmarkAccountsSubmitted, selectedPeriod, portfolioId));
    model.addAttribute("dashboard", dashboard);
    model.addAttribute("selectedPeriod", dashboard.selectedPeriod());
    model.addAttribute("periods", dashboard.periods());
    model.addAttribute("portfolioId", portfolioId);
    model.addAttribute("yahooFinanceUrl", yahooFinanceUrl);
    model.addAttribute("buildMetadata", buildMetadata);
    return "dashboard";
  }
}
