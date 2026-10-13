package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.model.PlanningTimelineState;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import com.smartbox.investory.ui.profile.ProfileClient;
import java.time.Clock;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/** Read-only visualization of existing profile and retirement planning facts. */
@Controller
@RequiredArgsConstructor
public class FinancialLandscapeController {
  private final ProfileClient profiles;
  private final RetirementPlanClient plans;
  private final RetirementProjectionClient projections;
  private final RetirementTimelineClient timeline;
  private final Clock clock;

  @GetMapping("/portfolios/{portfolioId}/financial-landscape")
  public String landscape(
      @PathVariable Long portfolioId,
      @RequestParam(name = "view", defaultValue = "full") String view,
      Model model) {
    InvestmentProfile profile = profiles.loadProfile(portfolioId);
    var currency = profile.currency();
    var planId = plans.resolvePlanId(portfolioId, null).orElse(null);
    var display = projections.loadDisplay(portfolioId, planId, null, null, currency);
    var response =
        timeline.loadForwardTimeline(
            portfolioId, display.projection(), SimulationScenario.BASE, currency);
    int currentYear = Year.now(clock).getValue();
    var year =
        response.timeline().years().stream()
            .filter(row -> row.state() == PlanningTimelineState.LIVE)
            .findFirst()
            .orElse(null);
    var money = year == null ? null : response.money().get(year.year());

    Map<String, Object> values = new LinkedHashMap<>();
    values.put("currency", currency.name());
    values.put("year", year == null ? currentYear : year.year());
    values.put("annualIncome", profile.incomeSummary().combinedNetAnnualIncome());
    values.put("annualCashIncome", money == null ? null : money.totalIncome());
    values.put("annualMarketResult", profile.incomeSummary().marketNetAnnualIncome());
    values.put("annualLongTermIncome", profile.incomeSummary().longTermAnnualIncome());
    values.put("annualExpenses", money == null ? null : money.annualCosts());
    values.put("annualSurplus", money == null ? null : money.fundingSurplus());
    values.put("annualGap", money == null ? null : money.fundingGap());
    values.put("liquidAssets", profile.liquidAssets());
    values.put("illiquidAssets", profile.illiquidAssets());
    values.put("netWorth", profile.totalNetWorth());
    values.put("reserve", profile.retirementReserve());
    model.addAttribute("portfolioId", portfolioId);
    model.addAttribute("landscape", values);
    return "compact".equalsIgnoreCase(view) ? "financial-landscape-compact" : "financial-landscape";
  }
}
