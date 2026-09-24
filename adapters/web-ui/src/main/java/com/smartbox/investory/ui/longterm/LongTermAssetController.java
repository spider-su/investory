package com.smartbox.investory.ui.longterm;

import com.smartbox.investory.longterm.api.LongTermAssetRateConversion;
import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.ui.presentation.UiPresentation;
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

  @GetMapping("/portfolios/{portfolioId}/long-term-assets")
  public String list(
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @RequestParam(defaultValue = "false") boolean showArchived,
      Model model) {
    LocalDate date = LocalDate.now(clock);
    var overview = assets.overview(portfolioId, date);
    var groups = overview.groups();
    model.addAttribute("portfolioId", portfolioId);
    model.addAttribute("assets", groups.stream().flatMap(g -> g.assets().stream()).toList());
    model.addAttribute(
        "archivedAssets", showArchived ? assets.archived(portfolioId, date) : java.util.List.of());
    model.addAttribute("groups", groups);
    model.addAttribute("overview", overview);
    model.addAttribute("total", overview);
    model.addAttribute("currency", overview.currency());
    model.addAttribute("longTermLargestClass", overview.largestGroup());
    model.addAttribute("longTermLargestClassShare", overview.largestGroupShare());
    model.addAttribute("longTermHeaderTotal", UiPresentation.compactMoney(overview.totalValue()));
    model.addAttribute(
        "longTermHeaderIncome",
        UiPresentation.moneyWhole(overview.economics().netAnnualIncomeAfterTax()));
    model.addAttribute(
        "longTermHeaderYield", UiPresentation.percentage(overview.economics().netYieldAfterTax()));
    model.addAttribute(
        "longTermGrossIncome", UiPresentation.moneyWhole(overview.economics().grossAnnualIncome()));
    model.addAttribute(
        "longTermExpensesTax",
        UiPresentation.moneyWhole(overview.economics().annualExpensesAndTax()));
    model.addAttribute(
        "longTermNetAnnualIncome",
        UiPresentation.moneyWhole(overview.economics().netAnnualIncomeAfterTax()));
    model.addAttribute(
        "longTermNetMonthlyIncome",
        UiPresentation.moneyWhole(overview.economics().monthlyNetIncomeAfterTax()));
    model.addAttribute(
        "longTermGrossYield", UiPresentation.percentage(overview.economics().grossYield()));
    return "long-term-assets";
  }

  @GetMapping("/portfolios/{portfolioId}/long-term-assets/new/cash-reserve")
  public String cashReserveForm(
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId, Model model) {
    model.addAttribute("portfolioId", portfolioId);
    return "cash-reserve-form";
  }

  @GetMapping("/portfolios/{portfolioId}/long-term-assets/{id}/cash-reserve")
  public String editCashReserve(
      @PathVariable Long portfolioId, @PathVariable Long id, Model model) {
    var asset = assets.cashReserve(portfolioId, id);
    var form = new CashReserveForm();
    form.setId(asset.id());
    form.setName(asset.name());
    form.setCurrency(asset.currency());
    form.setValue(asset.value());
    form.setAcquisitionDate(asset.acquisitionDate());
    form.setInterestRate(LongTermAssetRateConversion.rateToPercent(asset.interestRate()));
    form.setMaturityDate(asset.maturityDate());
    form.setNotes(asset.notes());
    model.addAttribute("asset", form);
    model.addAttribute("portfolioId", portfolioId);
    return "cash-reserve-form";
  }

  @PostMapping("/portfolios/{portfolioId}/long-term-assets/cash-reserve")
  public String saveCashReserve(
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @ModelAttribute CashReserveForm form,
      RedirectAttributes feedback) {
    try {
      var command =
          new CashReserveCommand(
              portfolioId,
              form.getId(),
              form.getName(),
              form.getCurrency(),
              form.getValue(),
              form.getAcquisitionDate() == null ? LocalDate.now(clock) : form.getAcquisitionDate(),
              LongTermAssetRateConversion.percentToRate(
                  form.getInterestRate() == null
                      ? java.math.BigDecimal.ZERO
                      : form.getInterestRate()),
              form.getMaturityDate(),
              form.getNotes());
      if (form.getId() == null) assets.createCashReserve(command);
      else assets.updateCashReserve(command);
      return "redirect:/portfolios/" + portfolioId + "/long-term-assets";
    } catch (IllegalArgumentException | ResourceNotFoundException exception) {
      feedback.addFlashAttribute("error", LongTermAssetPageSupport.assetError(exception));
      return "redirect:/portfolios/" + portfolioId + "/long-term-assets";
    }
  }

  @PostMapping("/portfolios/{portfolioId}/long-term-assets/{id}/archive")
  public String archive(
      @PathVariable Long id,
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId) {
    assets.archive(portfolioId, id);
    return "redirect:/portfolios/" + portfolioId + "/long-term-assets";
  }

  @PostMapping("/portfolios/{portfolioId}/long-term-assets/{id}/reactivate")
  public String reactivate(
      @PathVariable Long id,
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId) {
    assets.reactivate(portfolioId, id);
    return "redirect:/portfolios/" + portfolioId + "/long-term-assets";
  }
}
