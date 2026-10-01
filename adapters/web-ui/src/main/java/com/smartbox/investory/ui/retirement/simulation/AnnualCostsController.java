package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.RetirementPresentationApi;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.ui.profile.ProfileClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AnnualCostsController {
  private static final List<String> DISTRIBUTION_COLORS =
      List.of("#376cd5", "#00916d", "#d37100", "#0891b2", "#a16207", "#8b5cf6", "#db2031");
  private static final String OTHER_DISTRIBUTION_COLOR = "#748094";

  private final ProfileClient profiles;
  private final RetirementPlanClient plans;
  private final AnnualCostsClient annualCosts;
  private final RetirementPresentationApi presentation;
  private final Clock clock;

  public AnnualCostsController(
      ProfileClient profiles,
      RetirementPlanClient plans,
      AnnualCostsClient annualCosts,
      Clock clock,
      @Qualifier("retirementPlanningApplicationService") RetirementPresentationApi presentation) {
    this.profiles = profiles;
    this.plans = plans;
    this.annualCosts = annualCosts;
    this.clock = clock;
    this.presentation = presentation;
  }

  @GetMapping("/portfolios/{portfolioId}/simulation/annual-costs")
  public String annualCosts(
      @PathVariable Long portfolioId,
      @RequestParam(required = false) Long planId,
      @RequestParam(required = false) Integer year,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario,
      Model model) {
    int selectedYear = year == null ? Year.now(clock).getValue() : year;
    var profile = profiles.loadProfile(portfolioId);
    CurrencyType currency = profile.currency();
    Long selectedPlanId = plans.resolvePlanId(portfolioId, planId).orElse(null);
    model.addAttribute("portfolioId", portfolioId);
    model.addAttribute("profile", profile);
    model.addAttribute("planningDisplayCurrency", currency);
    model.addAttribute("selectedScenario", selectedScenario);
    model.addAttribute("selectedPlanId", selectedPlanId);
    model.addAttribute("selectedYear", selectedYear);
    model.addAttribute("plans", plans.listPlans(portfolioId));
    model.addAttribute("noSavedPlan", selectedPlanId == null);
    if (selectedPlanId != null) {
      var costs = annualCosts.load(portfolioId, selectedPlanId, selectedYear);
      model.addAttribute("planName", costs.planName());
      List<CostGroupRow> groupRows =
          costs.groups().stream()
              .map(
                  group ->
                      new CostGroupRow(
                          group.id(),
                          group.name(),
                          presentation.toDisplay(group.monthlyAmount(), currency)))
              .toList();
      model.addAttribute("costGroups", groupRows);
      BigDecimal distributionTotal = sumMonthly(costs.groups()).add(costs.otherMonthlyCosts());
      List<DistributionRow> distributionRows =
          distributionRows(costs.groups(), costs.otherMonthlyCosts(), distributionTotal, currency);
      model.addAttribute("distributionRows", distributionRows);
      model.addAttribute("distributionTotal", display(distributionTotal, currency));
      model.addAttribute("distributionGradient", distributionGradient(distributionRows));
      model.addAttribute("assignedMonthlyCosts", display(sumMonthly(costs.groups()), currency));
      model.addAttribute("otherMonthlyCosts", display(costs.otherMonthlyCosts(), currency));
      model.addAttribute("monthlyLivingCosts", display(costs.monthlyLivingCosts(), currency));
      model.addAttribute("annualLivingCosts", display(costs.annualLivingCosts(), currency));
      model.addAttribute("annualExtras", display(costs.annualExtras(), currency));
      model.addAttribute(
          "monthlyAdditionalCosts",
          display(
              costs.annualExtras().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP),
              currency));
      model.addAttribute(
          "totalAnnualCosts",
          display(costs.annualLivingCosts().add(costs.annualExtras()), currency));
      model.addAttribute("approvedYear", costs.approvedYear());
      model.addAttribute(
          "approvedMonthlyLivingCosts",
          costs.approvedMonthlyLivingCosts() == null
              ? null
              : display(costs.approvedMonthlyLivingCosts(), currency));
    }
    return "annual-costs";
  }

  @PostMapping("/portfolios/{portfolioId}/simulation/annual-costs/groups")
  public String saveGroup(
      @PathVariable Long portfolioId,
      @RequestParam Long planId,
      @RequestParam int year,
      @RequestParam(required = false) Long groupId,
      @RequestParam String name,
      @RequestParam BigDecimal monthlyAmount,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario,
      RedirectAttributes redirect) {
    CurrencyType currency = resolveCurrency(portfolioId);
    try {
      annualCosts.saveGroup(
          portfolioId,
          planId,
          year,
          groupId,
          name,
          presentation.fromDisplay(monthlyAmount, currency, null));
    } catch (IllegalArgumentException exception) {
      redirect.addFlashAttribute("annualCostsError", exception.getMessage());
    }
    return redirect(portfolioId, planId, year, currency, selectedScenario);
  }

  @PostMapping("/portfolios/{portfolioId}/simulation/annual-costs/groups/{groupId}/delete")
  public String deleteGroup(
      @PathVariable Long portfolioId,
      @PathVariable Long groupId,
      @RequestParam Long planId,
      @RequestParam int year,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario) {
    CurrencyType currency = resolveCurrency(portfolioId);
    annualCosts.deleteGroup(portfolioId, planId, year, groupId);
    return redirect(portfolioId, planId, year, currency, selectedScenario);
  }

  @PostMapping("/portfolios/{portfolioId}/simulation/annual-costs/annual")
  public String saveAnnualExtras(
      @PathVariable Long portfolioId,
      @RequestParam Long planId,
      @RequestParam int year,
      @RequestParam BigDecimal annualAmount,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario,
      RedirectAttributes redirect) {
    CurrencyType currency = resolveCurrency(portfolioId);
    try {
      annualCosts.saveAnnualExtras(
          portfolioId, planId, year, presentation.fromDisplay(annualAmount, currency, null));
    } catch (IllegalArgumentException exception) {
      redirect.addFlashAttribute("annualCostsError", exception.getMessage());
    }
    return redirect(portfolioId, planId, year, currency, selectedScenario);
  }

  private BigDecimal display(BigDecimal amount, CurrencyType currency) {
    return presentation.toDisplay(amount, currency);
  }

  private BigDecimal sumMonthly(
      List<com.smartbox.investory.retirement.api.RetirementAnnualCostsApi.Group> groups) {
    return groups.stream()
        .map(com.smartbox.investory.retirement.api.RetirementAnnualCostsApi.Group::monthlyAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private List<DistributionRow> distributionRows(
      List<com.smartbox.investory.retirement.api.RetirementAnnualCostsApi.Group> groups,
      BigDecimal otherMonthly,
      BigDecimal total,
      CurrencyType currency) {
    List<DistributionRow> rows = new ArrayList<>();
    for (int i = 0; i < groups.size(); i++) {
      var group = groups.get(i);
      if (group.monthlyAmount().signum() <= 0) continue;
      rows.add(
          new DistributionRow(
              group.name(),
              display(group.monthlyAmount(), currency),
              DISTRIBUTION_COLORS.get(i % DISTRIBUTION_COLORS.size()),
              group.monthlyAmount()));
    }
    if (otherMonthly.signum() > 0) {
      rows.add(
          new DistributionRow(
              "Other", display(otherMonthly, currency), OTHER_DISTRIBUTION_COLOR, otherMonthly));
    }
    if (total.signum() <= 0) return List.of();
    return rows;
  }

  private static String distributionGradient(List<DistributionRow> rows) {
    BigDecimal total =
        rows.stream()
            .map(DistributionRow::canonicalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (total.signum() <= 0) return "conic-gradient(var(--iv-surface-muted) 0% 100%)";
    StringBuilder gradient = new StringBuilder("conic-gradient(");
    BigDecimal start = BigDecimal.ZERO;
    for (int i = 0; i < rows.size(); i++) {
      DistributionRow row = rows.get(i);
      BigDecimal end =
          i == rows.size() - 1
              ? BigDecimal.valueOf(100)
              : start.add(
                  row.canonicalAmount()
                      .multiply(BigDecimal.valueOf(100))
                      .divide(total, 2, RoundingMode.HALF_UP));
      if (i > 0) gradient.append(", ");
      gradient
          .append(row.color())
          .append(' ')
          .append(start.stripTrailingZeros().toPlainString())
          .append("% ")
          .append(end.stripTrailingZeros().toPlainString())
          .append('%');
      start = end;
    }
    return gradient.append(')').toString();
  }

  private CurrencyType resolveCurrency(Long portfolioId) {
    return profiles.loadProfile(portfolioId).currency();
  }

  private static String redirect(
      Long portfolioId, Long planId, int year, CurrencyType currency, SimulationScenario scenario) {
    return "redirect:/portfolios/"
        + portfolioId
        + "/simulation/annual-costs?planId="
        + planId
        + "&year="
        + year
        + "&planningDisplayCurrency="
        + currency
        + "&selectedScenario="
        + scenario;
  }

  private record CostGroupRow(Long id, String name, BigDecimal monthlyAmount) {}

  private record DistributionRow(
      String name, BigDecimal monthlyAmount, String color, BigDecimal canonicalAmount) {}
}
