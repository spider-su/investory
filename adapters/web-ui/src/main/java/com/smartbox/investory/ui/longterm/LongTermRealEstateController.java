package com.smartbox.investory.ui.longterm;

import com.smartbox.investory.longterm.api.LongTermAssetsApi;
import com.smartbox.investory.longterm.api.model.AssetSummaryView;
import com.smartbox.investory.longterm.api.model.RealEstateCommand;
import com.smartbox.investory.longterm.api.model.RealEstateView;
import com.smartbox.investory.longterm.api.model.ResourceNotFoundException;
import com.smartbox.investory.shared.policy.FinancialPolicyDefaults;
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
  private final LongTermAssetsApi assets;
  private final Clock clock;

  public LongTermRealEstateController(LongTermAssetsApi assets, Clock clock) {
    this.assets = assets;
    this.clock = clock;
  }

  @GetMapping("/portfolios/{portfolioId}/long-term-assets/new/real-estate")
  public String realEstateForm(
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId, Model model) {
    model.addAttribute("portfolioId", portfolioId);
    return "real-estate-form";
  }

  @GetMapping("/portfolios/{portfolioId}/long-term-assets/{id}/real-estate")
  public String realEstateDetail(
      @PathVariable Long portfolioId, @PathVariable Long id, Model model) {
    populateDetail(portfolioId, id, model);
    return "real-estate-detail";
  }

  @PostMapping("/portfolios/{portfolioId}/long-term-assets/real-estate")
  public String saveRealEstate(
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @ModelAttribute RealEstateForm form,
      RedirectAttributes feedback) {
    try {
      var command =
          new RealEstateCommand(
              portfolioId,
              form.id(),
              form.name(),
              form.currency(),
              form.value(),
              form.taxBase(),
              form.acquisitionDate(),
              form.landRegisterNumber(),
              form.notes());
      RealEstateView saved =
          form.id() == null ? assets.createRealEstate(command) : assets.updateRealEstate(command);
      return detail(saved.id(), portfolioId);
    } catch (IllegalArgumentException | ResourceNotFoundException exception) {
      feedback.addFlashAttribute("error", LongTermAssetPageSupport.assetError(exception));
      return form.id() == null
          ? "redirect:/portfolios/" + portfolioId + "/long-term-assets/new/real-estate"
          : detail(form.id(), portfolioId);
    }
  }

  @PostMapping("/portfolios/{portfolioId}/long-term-assets/{id}/rental-contracts")
  public String addRentalContract(
      @PathVariable Long id,
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @ModelAttribute("rentalContract") RentalContractForm form,
      BindingResult binding,
      RedirectAttributes feedback) {
    if (!binding.hasErrors()) {
      try {
        assets.createRentalContract(form.createCommand(portfolioId, id));
      } catch (IllegalArgumentException | ResourceNotFoundException exception) {
        feedback.addFlashAttribute("error", LongTermAssetPageSupport.rentalError(exception));
      }
    } else LongTermAssetPageSupport.preserveBindingErrors(binding, feedback);
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
    if (!binding.hasErrors()) {
      try {
        assets.updateRentalContract(form.updateCommand(portfolioId, id, contractId));
      } catch (IllegalArgumentException | ResourceNotFoundException exception) {
        feedback.addFlashAttribute("error", LongTermAssetPageSupport.rentalError(exception));
      }
    } else LongTermAssetPageSupport.preserveBindingErrors(binding, feedback);
    return rental(id, portfolioId);
  }

  @PostMapping(
      "/portfolios/{portfolioId}/long-term-assets/{id}/rental-contracts/{contractId}/delete")
  public String deleteRentalContract(
      @PathVariable Long id,
      @PathVariable Long contractId,
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      RedirectAttributes feedback) {
    try {
      assets.deleteRentalContract(portfolioId, id, contractId);
    } catch (IllegalArgumentException | ResourceNotFoundException exception) {
      feedback.addFlashAttribute("error", LongTermAssetPageSupport.rentalError(exception));
    }
    return rental(id, portfolioId);
  }

  @PostMapping("/portfolios/{portfolioId}/long-term-assets/{id}/rental-contracts/{contractId}/end")
  public String endRentalContract(
      @PathVariable Long id,
      @PathVariable Long contractId,
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @RequestParam LocalDate endDate,
      RedirectAttributes feedback) {
    try {
      assets.endRentalContract(portfolioId, id, contractId, endDate);
    } catch (IllegalArgumentException | ResourceNotFoundException exception) {
      feedback.addFlashAttribute("error", LongTermAssetPageSupport.rentalError(exception));
    }
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
    try {
      assets.terminateRentalContract(portfolioId, id, contractId, terminationDate);
    } catch (IllegalArgumentException | ResourceNotFoundException exception) {
      feedback.addFlashAttribute("error", LongTermAssetPageSupport.rentalError(exception));
    }
    return rental(id, portfolioId);
  }

  private String rental(Long id, Long p) {
    return LongTermAssetPageSupport.rentalRedirect(id, p);
  }

  private String detail(Long id, Long portfolioId) {
    return "redirect:/portfolios/" + portfolioId + "/long-term-assets/" + id + "/real-estate";
  }

  private void populateDetail(Long portfolioId, Long id, Model model) {
    LocalDate today = LocalDate.now(clock);
    RealEstateView asset = assets.realEstate(portfolioId, id);
    AssetSummaryView summary = assets.realEstateSummary(portfolioId, id, today);
    var contracts = assets.rentalContracts(portfolioId, id, today);
    var contractForms =
        contracts.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    contract -> contract.id(), RentalContractForm::from));
    model.addAttribute("portfolioId", portfolioId);
    model.addAttribute("asset", asset);
    model.addAttribute("summary", summary);
    model.addAttribute("rentalTaxRate", FinancialPolicyDefaults.RENTAL_TAX_RATE);
    model.addAttribute("contracts", contracts);
    model.addAttribute("contractForms", contractForms);
    model.addAttribute("rentalContract", new RentalContractForm());
    model.addAttribute("today", today);
    model.addAttribute("suggestedNextContractStart", today);
  }
}
