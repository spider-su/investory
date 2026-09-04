package com.smartbox.investory.ui.profile;

import com.smartbox.investory.ui.investment.InvestmentDashboardClient;
import jakarta.validation.constraints.Positive;
import java.time.Clock;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Validated
@RequiredArgsConstructor
public class InvestmentProfileController {
  private final ProfileClient profile;
  private final InvestmentDashboardClient investmentDashboard;
  private final RetirementProfileClient retirementProfile;
  private final Clock clock;

  @GetMapping("/portfolios/{portfolioId}/investment-profile")
  public String profile(
      @org.springframework.web.bind.annotation.PathVariable @Positive Long portfolioId,
      Model model) {
    var profile = this.profile.loadProfile(portfolioId);
    var performanceKpi = investmentDashboard.loadPerformanceKpi(portfolioId);
    var investmentResult = investmentDashboard.investmentResult(portfolioId);
    var annualCost = retirementProfile.currentYearAnnualCost(portfolioId, profile.currency());
    var page =
        InvestmentProfilePageView.from(
            profile,
            performanceKpi,
            investmentResult,
            annualCost,
            YearMonth.now(clock).getMonthValue());
    model.addAttribute("profile", page);
    model.addAttribute("portfolioId", portfolioId);
    model.addAttribute("profileHeaderNetWorth", page.totalNetWorthCompactDisplay());
    model.addAttribute(
        "profileHeaderIncome", page.incomeSummary().combinedAnnualIncomeCompactDisplay());
    model.addAttribute("profileMarketAnnualizedReturn", page.marketAnnualizedReturnDisplay());
    model.addAttribute("profileMarketKpiMeta", page.marketKpiMeta());
    model.addAttribute(
        "profileMarketProjectedIncome", page.incomeSummary().marketAnnualIncomeCompactDisplay());
    model.addAttribute(
        "profileLongTermExpectedIncome", page.incomeSummary().longTermAnnualIncomeCompactDisplay());
    model.addAttribute("profileMarketYtdIncome", page.marketReceivedYtdDisplay());
    model.addAttribute("profileLongTermYtdIncome", page.longTermReceivedYtdDisplay());
    model.addAttribute("profileAnnualCost", page.annualCostDisplay());
    model.addAttribute("profileAnnualCostMeta", page.annualCostMeta());
    model.addAttribute("profileHeaderCurrency", page.currency());
    return "investment-profile";
  }
}
