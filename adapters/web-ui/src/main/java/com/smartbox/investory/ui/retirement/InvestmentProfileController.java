package com.smartbox.investory.ui.retirement;

import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import com.smartbox.investory.retirement.api.InvestmentProfileFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class InvestmentProfileController {
  private final InvestmentProfileFacade facade;
  private final InvestmentDashboardApi investmentDashboard;

  @GetMapping("/investment-profile")
  public String profile(@RequestParam(defaultValue = "1") Long portfolioId, Model model) {
    var profile = facade.loadProfile(portfolioId);
    var performanceKpi = investmentDashboard.loadPerformanceKpi(portfolioId);
    model.addAttribute("profile", profile);
    model.addAttribute("portfolioId", portfolioId);
    model.addAttribute("profileHeaderNetWorth", profile.totalNetWorthCompactDisplay());
    model.addAttribute(
        "profileHeaderIncome", profile.incomeSummary().combinedAnnualIncomeCompactDisplay());
    model.addAttribute("profileMarketAnnualizedReturn", performanceKpi.annualizedReturnDisplay());
    model.addAttribute("profileMarketKpiStart", performanceKpi.kpiStartDate());
    model.addAttribute(
        "profileMarketProjectedIncome", profile.incomeSummary().marketAnnualIncomeCompactDisplay());
    model.addAttribute(
        "profileLongTermExpectedIncome",
        profile.incomeSummary().longTermAnnualIncomeCompactDisplay());
    model.addAttribute(
        "profileMarketYtdIncome", profile.incomeSummary().marketIncomeYtdCompactDisplay());
    model.addAttribute("profileHeaderCurrency", profile.currency());
    return "investment-profile";
  }
}
