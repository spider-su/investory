package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.model.PlanningTimelineState;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import com.smartbox.investory.ui.profile.ProfileClient;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

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
  public String landscape(@PathVariable Long portfolioId, Model model) {
    InvestmentProfile profile = profiles.loadProfile(portfolioId);
    var currency = profile.currency();
    var planId = plans.resolvePlanId(portfolioId, null).orElse(null);
    var display = projections.loadDisplay(portfolioId, planId, null, null, currency);
    timeline.ensurePlanningTimeline(portfolioId);
    var response = timeline.loadForwardTimeline(
        portfolioId, display.projection(), SimulationScenario.BASE, currency);
    int currentYear = Year.now(clock).getValue();
    var year = response.timeline().years().stream()
        .filter(row -> row.state() == PlanningTimelineState.LIVE)
        .findFirst().orElse(null);
    var money = year == null ? null : response.money().get(year.year());

    Map<String, Object> values = new LinkedHashMap<>();
    values.put("currency", currency.name());
    values.put("year", year == null ? currentYear : year.year());
    values.put("annualIncome", money == null ? null : money.totalIncome());
    values.put("annualExpenses", money == null ? null : money.annualCosts());
    values.put("annualSurplus", money == null ? null : money.fundingSurplus());
    values.put("annualGap", money == null ? null : money.fundingGap());
    values.put("liquidAssets", profile.liquidAssets());
    values.put("illiquidAssets", profile.illiquidAssets());
    values.put("netWorth", profile.totalNetWorth());
    values.put("reserve", profile.retirementReserve());
    values.put("incomeSources", money == null ? Map.of() : Map.of(
        "Rental", zero(money.rentalIncome()),
        "Bonds", zero(money.bondIncome()),
        "Employment", zero(money.employmentIncome()),
        "Pension", zero(money.pensionIncome()),
        "Events", zero(money.eventIncome())));
    model.addAttribute("portfolioId", portfolioId);
    model.addAttribute("landscape", values);
    return "financial-landscape";
  }

  private static BigDecimal zero(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
