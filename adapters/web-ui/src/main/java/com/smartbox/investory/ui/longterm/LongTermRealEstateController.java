package com.smartbox.investory.ui.longterm;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** HTTP orchestration for real-estate pages. */
@Controller
public class LongTermRealEstateController {
  private final LongTermRealEstateCommandHandler commands;

  public LongTermRealEstateController(LongTermAssetsClient assets, Clock clock) {
    this.commands = new LongTermRealEstateCommandHandler(assets, clock);
  }

  @GetMapping("/portfolios/{portfolioId}/long-term-assets/new/real-estate")
  public String realEstateForm(
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId, Model model) {
    model.addAttribute("portfolioId", portfolioId);
    return "real-estate-form";
  }

  @PostMapping("/portfolios/{portfolioId}/long-term-assets/real-estate")
  public String saveRealEstate(
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @ModelAttribute RealEstateForm form,
      @RequestParam(name = "expectedAnnualGrowthRatePercent", required = false) BigDecimal growth,
      RedirectAttributes feedback) {
    commands.save(portfolioId, form, growth, feedback);
    return redirect(portfolioId);
  }

  @PostMapping("/portfolios/{portfolioId}/long-term-assets/{id}/rental-contracts")
  public String addRentalContract(
      @PathVariable Long id,
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @ModelAttribute("rentalContract") RentalContractForm form,
      BindingResult binding,
      RedirectAttributes feedback) {
    commands.add(id, portfolioId, form, binding, feedback);
    return rental(id, portfolioId);
  }

  @PostMapping("/portfolios/{portfolioId}/long-term-assets/{id}/rental-contracts/{contractId}")
  public String updateRentalContract(
      @PathVariable Long id,
      @PathVariable Long contractId,
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @ModelAttribute("contractEditForm") RentalContractForm form,
      BindingResult binding,
      RedirectAttributes feedback) {
    commands.update(id, contractId, portfolioId, form, binding, feedback);
    return rental(id, portfolioId);
  }

  @PostMapping(
      "/portfolios/{portfolioId}/long-term-assets/{id}/rental-contracts/{contractId}/delete")
  public String deleteRentalContract(
      @PathVariable Long id,
      @PathVariable Long contractId,
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      RedirectAttributes feedback) {
    commands.delete(id, contractId, portfolioId, feedback);
    return rental(id, portfolioId);
  }

  @PostMapping("/portfolios/{portfolioId}/long-term-assets/{id}/rental-contracts/{contractId}/end")
  public String endRentalContract(
      @PathVariable Long id,
      @PathVariable Long contractId,
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @RequestParam LocalDate endDate,
      RedirectAttributes feedback) {
    commands.end(id, contractId, portfolioId, endDate, feedback);
    return rental(id, portfolioId);
  }

  @PostMapping(
      "/portfolios/{portfolioId}/long-term-assets/{id}/rental-contracts/{contractId}/terminate")
  public String terminateRentalContract(
      @PathVariable Long id,
      @PathVariable Long contractId,
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @RequestParam LocalDate terminationDate,
      RedirectAttributes feedback) {
    commands.terminate(id, contractId, portfolioId, terminationDate, feedback);
    return rental(id, portfolioId);
  }

  @PostMapping("/portfolios/{portfolioId}/long-term-assets/{id}/property-growth")
  public String savePropertyGrowth(
      @PathVariable Long id,
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @RequestParam(required = false) BigDecimal growthRatePercent,
      @RequestParam LocalDate effectiveFrom,
      RedirectAttributes feedback) {
    commands.propertyGrowth(id, portfolioId, growthRatePercent, effectiveFrom, feedback);
    return asset(id, portfolioId);
  }

  @PostMapping("/portfolios/{portfolioId}/long-term-assets/{id}/valuation-periods")
  public String addValuationPeriod(
      @PathVariable Long id,
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @RequestParam LocalDate validFrom,
      @RequestParam(required = false) LocalDate validTo,
      @RequestParam BigDecimal expectedAnnualGrowthRatePercent,
      RedirectAttributes feedback) {
    commands.addValuation(
        id, portfolioId, validFrom, validTo, expectedAnnualGrowthRatePercent, feedback);
    return asset(id, portfolioId);
  }

  @PostMapping("/portfolios/{portfolioId}/long-term-assets/{id}/valuation-periods/{periodId}")
  public String updateValuationPeriod(
      @PathVariable Long id,
      @PathVariable Long periodId,
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @RequestParam LocalDate validFrom,
      @RequestParam(required = false) LocalDate validTo,
      @RequestParam BigDecimal expectedAnnualGrowthRatePercent,
      RedirectAttributes feedback) {
    commands.updateValuation(
        id, periodId, portfolioId, validFrom, validTo, expectedAnnualGrowthRatePercent, feedback);
    return asset(id, portfolioId);
  }

  @PostMapping(
      "/portfolios/{portfolioId}/long-term-assets/{id}/valuation-periods/{periodId}/delete")
  public String deleteValuationPeriod(
      @PathVariable Long id,
      @PathVariable Long periodId,
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      RedirectAttributes feedback) {
    commands.deleteValuation(id, periodId, portfolioId, feedback);
    return asset(id, portfolioId);
  }

  private String redirect(Long p) {
    return "redirect:/portfolios/" + p + "/long-term-assets";
  }

  private String rental(Long id, Long p) {
    return LongTermAssetPageSupport.rentalRedirect(id, p);
  }

  private String asset(Long id, Long p) {
    return LongTermAssetPageSupport.assetRedirect(id, p);
  }
}
