package com.smartbox.investory.ui.longterm;

import com.smartbox.investory.longterm.api.*;
import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.presentation.FinancialPresentation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class LongTermAssetController {
  private final LongTermAssetsClient assets;
  private final Clock clock;

  @GetMapping("/long-term-assets")
  public String list(
      @RequestParam(defaultValue = "1") Long portfolioId,
      @RequestParam(defaultValue = "false") boolean showArchived,
      Model model) {
    LocalDate date = LocalDate.now(clock);
    var page = assets.page(portfolioId, date);
    var groups = page.groups();
    var total = page.aggregate();
    model.addAttribute("portfolioId", portfolioId);
    model.addAttribute("assets", page.assets());
    model.addAttribute(
        "archivedAssets", showArchived ? assets.archived(portfolioId, date) : java.util.List.of());
    model.addAttribute("groups", groups);
    model.addAttribute("total", total);
    model.addAttribute("currency", total.currency());
    model.addAttribute(
        "longTermHeaderTotal", FinancialPresentation.compactMoney(total.totalCurrentValue()));
    model.addAttribute(
        "longTermHeaderIncome",
        FinancialPresentation.compactMoney(total.annualEconomics().netAnnualIncomeAfterTax()));
    model.addAttribute(
        "longTermHeaderYield",
        FinancialPresentation.percentage(total.annualEconomics().netYieldAfterTax()));
    model.addAttribute(
        "longTermGrossIncome",
        FinancialPresentation.compactMoney(total.annualEconomics().grossAnnualIncome()));
    model.addAttribute(
        "longTermExpensesTax",
        FinancialPresentation.compactMoney(total.annualEconomics().annualExpensesAndTax()));
    model.addAttribute(
        "longTermNetAnnualIncome",
        FinancialPresentation.compactMoney(total.annualEconomics().netAnnualIncomeAfterTax()));
    model.addAttribute(
        "longTermNetMonthlyIncome",
        FinancialPresentation.compactMoney(total.annualEconomics().monthlyNetIncomeAfterTax()));
    model.addAttribute(
        "longTermGrossYield",
        FinancialPresentation.percentage(total.annualEconomics().grossYield()));
    model.addAttribute(
        "groupShares",
        groups.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    AssetGroupView::key,
                    group ->
                        LongTermAssetPageSupport.share(
                            group.totalValue(), total.totalCurrentValue()))));

    groups.stream()
        .max(java.util.Comparator.comparing(g -> g.totalValue()))
        .ifPresent(
            g -> {
              model.addAttribute("longTermLargestClass", g.title());
              model.addAttribute(
                  "longTermLargestClassValue", FinancialPresentation.compactMoney(g.totalValue()));
              model.addAttribute(
                  "longTermLargestClassShare",
                  LongTermAssetPageSupport.share(g.totalValue(), total.totalCurrentValue()));
            });
    return "long-term-assets";
  }

  @GetMapping("/long-term-assets/new")
  public String createForm(@RequestParam(defaultValue = "1") Long portfolioId, Model model) {
    LongTermAssetForm asset = new LongTermAssetForm();
    asset.setPortfolioId(portfolioId);
    asset.setActive(true);
    model.addAttribute("asset", asset);
    model.addAttribute("portfolioId", portfolioId);
    return "long-term-asset-form";
  }

  @GetMapping("/long-term-assets/new/cash-reserve")
  public String cashReserveForm(@RequestParam(defaultValue = "1") Long portfolioId, Model model) {
    model.addAttribute("portfolioId", portfolioId);
    return "cash-reserve-form";
  }

  @PostMapping("/long-term-assets/cash-reserve")
  public String saveCashReserve(
      @RequestParam Long portfolioId,
      @RequestParam(required = false) Long id,
      @RequestParam String name,
      @RequestParam CurrencyType currency,
      @RequestParam BigDecimal value,
      @RequestParam(required = false) BigDecimal annualReturnPercent,
      @RequestParam(required = false) String notes,
      RedirectAttributes feedback) {
    try {
      var saved =
          assets.saveCashReserve(
              new CashReserveCommand(
                  portfolioId,
                  id,
                  name,
                  currency,
                  value,
                  LongTermAssetRateConversion.percentToRate(annualReturnPercent),
                  notes),
              LocalDate.now(clock));
      return LongTermAssetPageSupport.assetRedirect(saved.id(), portfolioId);
    } catch (IllegalArgumentException | ResourceNotFoundException exception) {
      feedback.addFlashAttribute("error", LongTermAssetPageSupport.assetError(exception));
      return id == null
          ? "redirect:/long-term-assets?portfolioId=" + portfolioId
          : LongTermAssetPageSupport.assetRedirect(id, portfolioId);
    }
  }

  @PostMapping("/long-term-assets")
  public String create(
      @ModelAttribute LongTermAssetForm form,
      @RequestParam Long portfolioId,
      RedirectAttributes feedback) {
    try {
      form.setActive(true);
      assets.create(form.command(portfolioId));
      return "redirect:/long-term-assets?portfolioId=" + portfolioId;
    } catch (IllegalArgumentException | ResourceNotFoundException exception) {
      feedback.addFlashAttribute("error", LongTermAssetPageSupport.assetError(exception));
      return "redirect:/long-term-assets?portfolioId=" + portfolioId;
    }
  }

  @GetMapping("/long-term-assets/{id}")
  public String detail(
      @PathVariable Long id, @RequestParam(defaultValue = "1") Long portfolioId, Model model) {
    LocalDate today = LocalDate.now(clock);
    var view = assets.details(portfolioId, id, today);
    model.addAttribute("asset", view.asset());
    model.addAttribute("portfolioId", portfolioId);
    model.addAttribute("summary", view.summary());
    model.addAttribute("contracts", view.contracts());
    model.addAttribute("bondDetails", view.bondDetails());
    model.addAttribute("depositDetails", view.depositDetails());
    model.addAttribute("valuationPeriods", view.valuationPeriods());
    model.addAttribute("expectedPropertyGrowth", view.expectedPropertyGrowth());
    model.addAttribute("today", today);
    java.util.Map<Long, RentalContractForm> contractForms =
        view.contracts().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    RentalContractView::id, RentalContractForm::from));
    if (model.containsAttribute("editContractId") && model.containsAttribute("contractEditForm")) {
      Object contractId = model.asMap().get("editContractId");
      if (contractId instanceof Long value)
        contractForms.put(value, (RentalContractForm) model.asMap().get("contractEditForm"));
    }
    model.addAttribute("contractForms", contractForms);
    if (!model.containsAttribute("rentalContract"))
      model.addAttribute("rentalContract", new RentalContractForm());
    view.contracts().stream()
        .findFirst()
        .map(RentalContractView::effectiveEndDate)
        .filter(java.util.Objects::nonNull)
        .map(date -> date.plusDays(1))
        .ifPresent(date -> model.addAttribute("suggestedNextContractStart", date));
    return switch (view.asset().type()) {
      case BOND -> "bond-detail";
      case REAL_ESTATE -> "real-estate-detail";
      case CASH_RESERVE -> "cash-reserve-detail";
      default -> "long-term-asset-detail";
    };
  }

  @PostMapping("/long-term-assets/{id}")
  public String update(
      @PathVariable Long id,
      @ModelAttribute LongTermAssetForm form,
      @RequestParam Long portfolioId,
      @RequestParam(required = false) BigDecimal taxBase,
      RedirectAttributes feedback) {
    try {
      assets.update(form.command(portfolioId, id, taxBase));
    } catch (IllegalArgumentException | ResourceNotFoundException exception) {
      feedback.addFlashAttribute("error", LongTermAssetPageSupport.assetError(exception));
    }
    return LongTermAssetPageSupport.assetRedirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/archive")
  public String archive(@PathVariable Long id, @RequestParam Long portfolioId) {
    assets.archive(portfolioId, id);
    return "redirect:/long-term-assets?portfolioId=" + portfolioId;
  }

  @PostMapping("/long-term-assets/{id}/reactivate")
  public String reactivate(@PathVariable Long id, @RequestParam Long portfolioId) {
    assets.reactivate(portfolioId, id);
    return LongTermAssetPageSupport.assetRedirect(id, portfolioId);
  }
}
