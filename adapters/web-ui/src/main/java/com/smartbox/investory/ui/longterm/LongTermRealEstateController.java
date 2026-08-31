package com.smartbox.investory.ui.longterm;

import com.smartbox.investory.longterm.api.LongTermAssetRateConversion;
import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.api.model.RealEstateEntryModel;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Real-estate, rental-contract, and valuation MVC endpoints. */
@Controller
@RequiredArgsConstructor
public class LongTermRealEstateController {
  private final LongTermAssetsClient assets;
  private final Clock clock;

  @GetMapping("/long-term-assets/new/real-estate")
  public String realEstateForm(@RequestParam(defaultValue = "1") Long portfolioId, Model model) {
    model.addAttribute("portfolioId", portfolioId);
    return "real-estate-form";
  }

  @PostMapping("/long-term-assets/real-estate")
  public String saveRealEstate(
      @RequestParam Long portfolioId,
      @ModelAttribute RealEstateForm entry,
      @RequestParam(name = "expectedAnnualGrowthRatePercent", required = false) BigDecimal growth,
      RedirectAttributes feedback) {
    try {
      assets.saveRealEstate(
          portfolioId,
          new RealEstateEntryModel(
              entry.name(),
              entry.currency(),
              entry.acquisitionDate(),
              entry.acquisitionValue(),
              entry.currentValue(),
              entry.taxBase(),
              entry.monthlyRent(),
              entry.monthlyParkingIncome(),
              entry.monthlyAdministrationCost(),
              entry.monthlyOtherCost(),
              entry.annualPropertyTax(),
              entry.annualInsurance(),
              entry.effectiveFrom(),
              LongTermAssetRateConversion.percentToRate(growth),
              entry.notes()));
      return "redirect:/long-term-assets?portfolioId=" + portfolioId;
    } catch (IllegalArgumentException | ResourceNotFoundException exception) {
      feedback.addFlashAttribute("error", LongTermAssetPageSupport.assetError(exception));
      return "redirect:/long-term-assets?portfolioId=" + portfolioId;
    }
  }

  @PostMapping("/long-term-assets/{id}/rental-contracts")
  public String addRentalContract(
      @PathVariable Long id,
      @RequestParam Long portfolioId,
      @ModelAttribute("rentalContract") RentalContractForm rentalContract,
      BindingResult binding,
      RedirectAttributes feedback) {
    if (binding.hasErrors()) {
      LongTermAssetPageSupport.preserveBindingErrors(binding, feedback);
      feedback.addFlashAttribute("showAddContract", true);
      return LongTermAssetPageSupport.rentalRedirect(id, portfolioId);
    }
    try {
      assets.createRentalContract(rentalContract.createCommand(portfolioId, id));
      feedback.addFlashAttribute("success", "Rental contract created.");
    } catch (IllegalArgumentException | ResourceNotFoundException exception) {
      feedback.addFlashAttribute("error", LongTermAssetPageSupport.rentalError(exception));
      feedback.addFlashAttribute("rentalContract", rentalContract);
      feedback.addFlashAttribute("showAddContract", true);
    }
    return LongTermAssetPageSupport.rentalRedirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/rental-contracts/{contractId}")
  public String updateRentalContract(
      @PathVariable Long id,
      @PathVariable Long contractId,
      @RequestParam Long portfolioId,
      @ModelAttribute("contractEditForm") RentalContractForm rentalContract,
      BindingResult binding,
      RedirectAttributes feedback) {
    if (binding.hasErrors()) {
      LongTermAssetPageSupport.preserveBindingErrors(binding, feedback);
      feedback.addFlashAttribute("editContractId", contractId);
      return LongTermAssetPageSupport.rentalRedirect(id, portfolioId);
    }
    try {
      assets.updateRentalContract(rentalContract.updateCommand(portfolioId, id, contractId));
      feedback.addFlashAttribute("success", "Rental contract updated.");
    } catch (IllegalArgumentException | ResourceNotFoundException exception) {
      feedback.addFlashAttribute("error", LongTermAssetPageSupport.rentalError(exception));
      feedback.addFlashAttribute("editContractId", contractId);
      feedback.addFlashAttribute("contractEditForm", rentalContract);
    }
    return LongTermAssetPageSupport.rentalRedirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/rental-contracts/{contractId}/delete")
  public String deleteRentalContract(
      @PathVariable Long id,
      @PathVariable Long contractId,
      @RequestParam Long portfolioId,
      RedirectAttributes feedback) {
    try {
      assets.deleteRentalContract(portfolioId, id, contractId);
      feedback.addFlashAttribute("success", "Rental contract deleted.");
    } catch (IllegalArgumentException | ResourceNotFoundException exception) {
      feedback.addFlashAttribute("error", LongTermAssetPageSupport.rentalError(exception));
    }
    return LongTermAssetPageSupport.rentalRedirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/rental-contracts/{contractId}/end")
  public String endRentalContract(
      @PathVariable Long id,
      @PathVariable Long contractId,
      @RequestParam Long portfolioId,
      @RequestParam LocalDate endDate,
      RedirectAttributes feedback) {
    try {
      assets.endRentalContract(portfolioId, id, contractId, endDate);
      feedback.addFlashAttribute("success", "Expected contract end updated.");
    } catch (IllegalArgumentException | ResourceNotFoundException exception) {
      feedback.addFlashAttribute("error", LongTermAssetPageSupport.rentalError(exception));
    }
    return LongTermAssetPageSupport.rentalRedirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/rental-contracts/{contractId}/terminate")
  public String terminateRentalContract(
      @PathVariable Long id,
      @PathVariable Long contractId,
      @RequestParam Long portfolioId,
      @RequestParam LocalDate terminationDate,
      RedirectAttributes feedback) {
    if (terminationDate.isAfter(LocalDate.now(clock))) {
      feedback.addFlashAttribute("error", "Actual termination date cannot be later than today.");
      return LongTermAssetPageSupport.rentalRedirect(id, portfolioId);
    }
    try {
      assets.terminateRentalContract(portfolioId, id, contractId, terminationDate);
      feedback.addFlashAttribute("success", "Early termination recorded.");
    } catch (IllegalArgumentException | ResourceNotFoundException exception) {
      feedback.addFlashAttribute("error", LongTermAssetPageSupport.rentalError(exception));
    }
    return LongTermAssetPageSupport.rentalRedirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/property-growth")
  public String savePropertyGrowth(
      @PathVariable Long id,
      @RequestParam Long portfolioId,
      @RequestParam(required = false) BigDecimal growthRatePercent,
      @RequestParam LocalDate effectiveFrom,
      RedirectAttributes feedback) {
    LongTermAssetPageSupport.applyAssetMutation(
        () ->
            assets.savePropertyGrowth(
                portfolioId,
                id,
                LongTermAssetRateConversion.percentToRate(growthRatePercent),
                effectiveFrom),
        feedback);
    return LongTermAssetPageSupport.assetRedirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/valuation-periods")
  public String addValuationPeriod(
      @PathVariable Long id,
      @RequestParam Long portfolioId,
      @RequestParam LocalDate validFrom,
      @RequestParam(required = false) LocalDate validTo,
      @RequestParam BigDecimal expectedAnnualGrowthRatePercent,
      RedirectAttributes feedback) {
    LongTermAssetPageSupport.applyAssetMutation(
        () ->
            assets.addValuation(
                portfolioId,
                id,
                new ValuationCommand(
                    validFrom,
                    validTo,
                    LongTermAssetRateConversion.percentToRate(expectedAnnualGrowthRatePercent))),
        feedback);
    return LongTermAssetPageSupport.assetRedirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/valuation-periods/{periodId}")
  public String updateValuationPeriod(
      @PathVariable Long id,
      @PathVariable Long periodId,
      @RequestParam Long portfolioId,
      @RequestParam LocalDate validFrom,
      @RequestParam(required = false) LocalDate validTo,
      @RequestParam BigDecimal expectedAnnualGrowthRatePercent,
      RedirectAttributes feedback) {
    LongTermAssetPageSupport.applyAssetMutation(
        () ->
            assets.updateValuation(
                portfolioId,
                id,
                periodId,
                new ValuationCommand(
                    validFrom,
                    validTo,
                    LongTermAssetRateConversion.percentToRate(expectedAnnualGrowthRatePercent))),
        feedback);
    return LongTermAssetPageSupport.assetRedirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/valuation-periods/{periodId}/delete")
  public String deleteValuationPeriod(
      @PathVariable Long id,
      @PathVariable Long periodId,
      @RequestParam Long portfolioId,
      RedirectAttributes feedback) {
    LongTermAssetPageSupport.applyAssetMutation(
        () -> assets.deleteValuation(portfolioId, id, periodId), feedback);
    return LongTermAssetPageSupport.assetRedirect(id, portfolioId);
  }
}
