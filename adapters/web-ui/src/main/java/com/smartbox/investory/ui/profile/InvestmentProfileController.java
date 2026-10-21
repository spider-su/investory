package com.smartbox.investory.ui.profile;

import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
import com.smartbox.investory.ui.investment.InvestmentDashboardClient;
import com.smartbox.investory.ui.retirement.simulation.FinancialLandscapePageAssembler;
import jakarta.validation.constraints.Positive;
import java.time.Clock;
import java.time.YearMonth;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Validated
public class InvestmentProfileController {
  private final ProfileClient profile;
  private final InvestmentDashboardClient investmentDashboard;
  private final RetirementProfileClient retirementProfile;
  private final PortfolioContextReader portfolioContexts;
  private final Executor profileReadExecutor;
  private final Clock clock;
  private final FinancialLandscapePageAssembler financialLandscapePageAssembler;

  public InvestmentProfileController(
      ProfileClient profile,
      InvestmentDashboardClient investmentDashboard,
      RetirementProfileClient retirementProfile,
      PortfolioContextReader portfolioContexts,
      @Qualifier("investmentProfileReadExecutor") Executor profileReadExecutor,
      Clock clock,
      FinancialLandscapePageAssembler financialLandscapePageAssembler) {
    this.profile = profile;
    this.investmentDashboard = investmentDashboard;
    this.retirementProfile = retirementProfile;
    this.portfolioContexts = portfolioContexts;
    this.profileReadExecutor = profileReadExecutor;
    this.clock = clock;
    this.financialLandscapePageAssembler = financialLandscapePageAssembler;
  }

  @GetMapping("/portfolios/{portfolioId}/investment-profile")
  public String profile(
      @org.springframework.web.bind.annotation.PathVariable @Positive Long portfolioId,
      Model model) {
    var context = portfolioContexts.findById(portfolioId).orElse(null);
    var profileFuture =
        CompletableFuture.supplyAsync(() -> profile.loadProfile(portfolioId), profileReadExecutor);
    var landscapeFuture =
        CompletableFuture.supplyAsync(
            () -> financialLandscapePageAssembler.assembleAttributes(portfolioId),
            profileReadExecutor);
    var annualCostFuture =
        context != null && context.localCurrency() != null
            ? CompletableFuture.supplyAsync(
                () -> retirementProfile.currentYearAnnualCost(portfolioId, context.localCurrency()),
                profileReadExecutor)
            : null;
    var profile = profileFuture.join();
    boolean investmentIncomeAvailable = profile.incomeSummary().investmentIncomeAvailable();
    var performanceKpi =
        investmentIncomeAvailable ? null : investmentDashboard.loadPerformanceKpi(portfolioId);
    var investmentResult =
        investmentIncomeAvailable ? null : investmentDashboard.investmentResultYtd(portfolioId);
    var annualCost =
        annualCostFuture == null
            ? retirementProfile.currentYearAnnualCost(portfolioId, profile.currency())
            : annualCostFuture.join();
    var page =
        InvestmentProfilePageView.from(
            profile,
            performanceKpi,
            investmentResult,
            annualCost,
            YearMonth.now(clock).getMonthValue());
    model.addAttribute("profile", page);
    model.addAttribute("portfolioId", portfolioId);
    model.addAttribute("landscape", landscapeFuture.join());
    return "investment-profile";
  }
}
