package com.smartbox.investory.ui.app;

import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.ui.common.BuildMetadata;
import com.smartbox.investory.ui.investment.InvestmentDashboardClient;
import com.smartbox.investory.ui.investment.InvestmentDashboardPageView;
import com.smartbox.investory.ui.investment.InvestmentDashboardQuery;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomeController {

  private final InvestmentDashboardClient investmentDashboardFacade;
  private final BuildMetadata buildMetadata;

  public HomeController(
      InvestmentDashboardClient investmentDashboardFacade,
      BuildMetadata buildMetadata,
      @Value("${app.ui.yahoo-url:}") String yahooFinanceUrl,
      @Value("${app.security.google.enabled:false}") boolean googleLoginEnabled) {
    this.investmentDashboardFacade = investmentDashboardFacade;
    this.buildMetadata = buildMetadata;
    this.yahooFinanceUrl = yahooFinanceUrl;
    this.googleLoginEnabled = googleLoginEnabled;
  }

  private final String yahooFinanceUrl;
  private final boolean googleLoginEnabled;

  @GetMapping("/")
  public String home(org.springframework.ui.Model model) {
    model.addAttribute("googleLoginEnabled", googleLoginEnabled);
    return "home";
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
    List<Long> selectedAccountIds = accountIds == null ? List.of() : List.copyOf(accountIds);
    InvestmentDashboardPageView dashboard =
        investmentDashboardFacade.loadDashboard(
            new InvestmentDashboardQuery(
                selectedAccountIds, benchmarkAccountsSubmitted, selectedPeriod, portfolioId));
    model.addAttribute("dashboard", dashboard);
    model.addAttribute("selectedPeriod", dashboard.selectedPeriod());
    model.addAttribute("periods", dashboard.periods());
    model.addAttribute("portfolioId", portfolioId);
    model.addAttribute("yahooFinanceUrl", yahooFinanceUrl);
    model.addAttribute("buildMetadata", buildMetadata);
    return "dashboard";
  }
}
