package com.smartbox.investory.ui.longterm;

import com.smartbox.investory.longterm.api.*;
import com.smartbox.investory.longterm.api.model.CashFlowTypeModel;
import com.smartbox.investory.longterm.api.model.FrequencyModel;
import com.smartbox.investory.longterm.api.model.InterestTreatmentModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetTypeModel;
import com.smartbox.investory.longterm.api.model.RealEstateEntryModel;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.presentation.FinancialPresentation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class LongTermAssetController {
  private final LongTermAssetsApi assets;
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
        FinancialPresentation.wholeNumber(total.annualEconomics().grossAnnualIncome()));
    model.addAttribute(
        "longTermExpensesTax",
        FinancialPresentation.wholeNumber(
            total.annualEconomics().annualExpenses().add(total.annualEconomics().annualTax())));
    model.addAttribute(
        "longTermGrossYield",
        FinancialPresentation.percentage(total.annualEconomics().grossYield()));
    model.addAttribute(
        "groupShares",
        groups.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    LongTermAssetsApi.AssetGroupView::key,
                    group -> share(group.totalValue(), total.totalCurrentValue()))));

    groups.stream()
        .max(java.util.Comparator.comparing(g -> g.totalValue()))
        .ifPresent(
            g -> {
              model.addAttribute("longTermLargestClass", g.title());
              model.addAttribute(
                  "longTermLargestClassValue", FinancialPresentation.compactMoney(g.totalValue()));
              model.addAttribute(
                  "longTermLargestClassShare", share(g.totalValue(), total.totalCurrentValue()));
            });
    return "long-term-assets";
  }

  @GetMapping("/long-term-assets/new")
  public String createForm(@RequestParam(defaultValue = "1") Long portfolioId, Model model) {
    AssetForm asset = new AssetForm();
    asset.setPortfolioId(portfolioId);
    asset.setActive(true);
    model.addAttribute("asset", asset);
    model.addAttribute("portfolioId", portfolioId);
    return "long-term-asset-form";
  }

  @GetMapping("/long-term-assets/new/bond")
  public String bondForm(@RequestParam(defaultValue = "1") Long portfolioId, Model model) {
    AssetForm asset = new AssetForm();
    asset.setPortfolioId(portfolioId);
    asset.setType(LongTermAssetTypeModel.BOND);
    asset.setCurrency(CurrencyType.PLN);
    asset.setActive(true);
    model.addAttribute("asset", asset);
    model.addAttribute("portfolioId", portfolioId);
    return "bond-form";
  }

  @GetMapping("/long-term-assets/new/cash-reserve")
  public String cashReserveForm(@RequestParam(defaultValue = "1") Long portfolioId, Model model) {
    model.addAttribute("portfolioId", portfolioId);
    return "cash-reserve-form";
  }

  @GetMapping("/long-term-assets/new/deposit")
  public String depositForm(@RequestParam(defaultValue = "1") Long portfolioId, Model model) {
    model.addAttribute("portfolioId", portfolioId);
    return "deposit-form";
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
              new LongTermAssetsApi.CashReserveCommand(
                  portfolioId, id, name, currency, value, annualReturnPercent, notes),
              LocalDate.now(clock));
      return redirect(saved.id(), portfolioId);
    } catch (IllegalArgumentException | NoSuchElementException exception) {
      feedback.addFlashAttribute("error", assetError(exception));
      return id == null
          ? "redirect:/long-term-assets?portfolioId=" + portfolioId
          : redirect(id, portfolioId);
    }
  }

  @PostMapping("/long-term-assets")
  public String create(
      @ModelAttribute AssetForm form,
      @RequestParam Long portfolioId,
      RedirectAttributes feedback) {
    try {
      form.setActive(true);
      assets.create(form.command(portfolioId));
      return "redirect:/long-term-assets?portfolioId=" + portfolioId;
    } catch (IllegalArgumentException | NoSuchElementException exception) {
      feedback.addFlashAttribute("error", assetError(exception));
      return "redirect:/long-term-assets?portfolioId=" + portfolioId;
    }
  }

  @PostMapping("/long-term-assets/bond")
  public String createBond(
      @RequestParam Long portfolioId,
      @RequestParam String name,
      @RequestParam CurrencyType currency,
      @RequestParam BigDecimal value,
      @RequestParam LocalDate acquisitionDate,
      @RequestParam LocalDate maturityDate,
      @RequestParam InterestTreatmentModel interestTreatment,
      @RequestParam BigDecimal annualRatePercent,
      @RequestParam(required = false) String notes,
      RedirectAttributes feedback) {
    try {
      var saved = assets.createBond(
            new LongTermAssetsApi.BondCommand(
                portfolioId,
                null,
                name,
                currency,
                value,
                acquisitionDate,
                maturityDate,
                interestTreatment,
                annualRatePercent,
                notes));
      return redirect(saved.id(), portfolioId);
    } catch (IllegalArgumentException | NoSuchElementException exception) {
      feedback.addFlashAttribute("error", assetError(exception));
      return "redirect:/long-term-assets?portfolioId=" + portfolioId;
    }
  }

  @PostMapping("/long-term-assets/deposit")
  public String createDeposit(
      @RequestParam Long portfolioId,
      @RequestParam String name,
      @RequestParam CurrencyType currency,
      @RequestParam BigDecimal value,
      @RequestParam LocalDate acquisitionDate,
      @RequestParam LocalDate maturityDate,
      @RequestParam InterestTreatmentModel interestTreatment,
      @RequestParam BigDecimal annualInterestRate,
      @RequestParam BigDecimal taxRate,
      @RequestParam(required = false) String notes,
      RedirectAttributes feedback) {
    try {
      var saved = assets.createDeposit(
            new LongTermAssetsApi.DepositCommand(
                portfolioId,
                name,
                currency,
                value,
                acquisitionDate,
                maturityDate,
                interestTreatment,
                annualInterestRate,
                taxRate,
                notes));
      return redirect(saved.id(), portfolioId);
    } catch (IllegalArgumentException | NoSuchElementException exception) {
      feedback.addFlashAttribute("error", assetError(exception));
      return "redirect:/long-term-assets?portfolioId=" + portfolioId;
    }
  }

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
            growth,
            entry.notes()));
      return "redirect:/long-term-assets?portfolioId=" + portfolioId;
    } catch (IllegalArgumentException | NoSuchElementException exception) {
      feedback.addFlashAttribute("error", assetError(exception));
      return "redirect:/long-term-assets?portfolioId=" + portfolioId;
    }
  }

  private static String share(BigDecimal value, BigDecimal total) {
    return total == null || total.signum() == 0
        ? "0.0%"
        : FinancialPresentation.percentage(value.divide(total, 8, java.math.RoundingMode.HALF_UP));
  }

  @PostMapping("/long-term-assets/{id}/rental-contracts")
  public String addRentalContract(
      @PathVariable Long id,
      @RequestParam Long portfolioId,
      @ModelAttribute("rentalContract") RentalContractForm rentalContract,
      BindingResult binding,
      RedirectAttributes feedback) {
    if (binding.hasErrors()) {
      preserveBindingErrors(binding, feedback);
      feedback.addFlashAttribute("showAddContract", true);
      return rentalRedirect(id, portfolioId);
    }
    try {
      assets.createRentalContract(rentalContract.createCommand(portfolioId, id));
      feedback.addFlashAttribute("success", "Rental contract created.");
    } catch (IllegalArgumentException | NoSuchElementException exception) {
      feedback.addFlashAttribute("error", rentalError(exception));
      feedback.addFlashAttribute("rentalContract", rentalContract);
      feedback.addFlashAttribute("showAddContract", true);
    }
    return rentalRedirect(id, portfolioId);
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
      preserveBindingErrors(binding, feedback);
      feedback.addFlashAttribute("editContractId", contractId);
      return rentalRedirect(id, portfolioId);
    }
    try {
      assets.updateRentalContract(rentalContract.updateCommand(portfolioId, id, contractId));
      feedback.addFlashAttribute("success", "Rental contract updated.");
    } catch (IllegalArgumentException | NoSuchElementException exception) {
      feedback.addFlashAttribute("error", rentalError(exception));
      feedback.addFlashAttribute("editContractId", contractId);
      feedback.addFlashAttribute("contractEditForm", rentalContract);
    }
    return rentalRedirect(id, portfolioId);
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
    } catch (IllegalArgumentException | NoSuchElementException exception) {
      feedback.addFlashAttribute("error", rentalError(exception));
    }
    return rentalRedirect(id, portfolioId);
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
    } catch (IllegalArgumentException | NoSuchElementException exception) {
      feedback.addFlashAttribute("error", rentalError(exception));
    }
    return rentalRedirect(id, portfolioId);
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
      return rentalRedirect(id, portfolioId);
    }
    try {
      assets.terminateRentalContract(portfolioId, id, contractId, terminationDate);
      feedback.addFlashAttribute("success", "Early termination recorded.");
    } catch (IllegalArgumentException | NoSuchElementException exception) {
      feedback.addFlashAttribute("error", rentalError(exception));
    }
    return rentalRedirect(id, portfolioId);
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
                    LongTermAssetsApi.RentalContractView::id, RentalContractForm::from));
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
        .map(LongTermAssetsApi.RentalContractView::effectiveEndDate)
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
      @ModelAttribute AssetForm form,
      @RequestParam Long portfolioId,
      @RequestParam(required = false) BigDecimal taxBase,
      RedirectAttributes feedback) {
    try {
      assets.update(form.command(portfolioId, id, taxBase));
    } catch (IllegalArgumentException | NoSuchElementException exception) {
      feedback.addFlashAttribute("error", assetError(exception));
    }
    return redirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/archive")
  public String archive(@PathVariable Long id, @RequestParam Long portfolioId) {
    assets.archive(portfolioId, id);
    return "redirect:/long-term-assets?portfolioId=" + portfolioId;
  }

  @PostMapping("/long-term-assets/{id}/reactivate")
  public String reactivate(@PathVariable Long id, @RequestParam Long portfolioId) {
    assets.reactivate(portfolioId, id);
    return redirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/tax-base")
  public String updateTaxBase(
      @PathVariable Long id,
      @RequestParam Long portfolioId,
      @RequestParam BigDecimal taxBase,
      RedirectAttributes feedback) {
    mutate(() -> assets.saveTaxBase(portfolioId, id, taxBase), feedback);
    return redirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/rental-tax-ownership")
  public String saveRentalTaxOwnership(
      @PathVariable Long id,
      @RequestParam Long portfolioId,
      @RequestParam(defaultValue = "false") boolean paidByTenant,
      RedirectAttributes feedback) {
    mutate(() -> assets.saveRentalTaxOwnership(portfolioId, id, paidByTenant), feedback);
    return redirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/property-growth")
  public String savePropertyGrowth(
      @PathVariable Long id,
      @RequestParam Long portfolioId,
      @RequestParam(required = false) BigDecimal growthRatePercent,
      @RequestParam LocalDate effectiveFrom,
      RedirectAttributes feedback) {
    mutate(
        () -> assets.savePropertyGrowth(portfolioId, id, growthRatePercent, effectiveFrom),
        feedback);
    return redirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/bond-details")
  public String saveBondDetails(
      @PathVariable Long id,
      @RequestParam Long portfolioId,
      @ModelAttribute BondDetailsForm form,
      RedirectAttributes feedback) {
    mutate(
        () ->
            assets.saveBondDetails(
                portfolioId,
                id,
                new LongTermAssetsApi.BondDetailsCommand(
                    form.maturityDate,
                    form.taxRate,
                    form.interestTreatment,
                    form.redemptionValue)),
        feedback);
    return redirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/bond")
  public String updateBond(
      @PathVariable Long id,
      @RequestParam Long portfolioId,
      @RequestParam String name,
      @RequestParam CurrencyType currency,
      @RequestParam BigDecimal value,
      @RequestParam LocalDate acquisitionDate,
      @RequestParam LocalDate maturityDate,
      @RequestParam InterestTreatmentModel interestTreatment,
      @RequestParam BigDecimal annualRatePercent,
      @RequestParam(required = false) String notes,
      RedirectAttributes feedback) {
    mutate(
        () ->
            assets.updateBond(
                new LongTermAssetsApi.BondCommand(
                    portfolioId,
                    id,
                    name,
                    currency,
                    value,
                    acquisitionDate,
                    maturityDate,
                    interestTreatment,
                    annualRatePercent,
                    notes)),
        feedback);
    return redirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/deposit-details")
  public String saveDepositDetails(
      @PathVariable Long id,
      @RequestParam Long portfolioId,
      @ModelAttribute DepositDetailsForm form,
      RedirectAttributes feedback) {
    mutate(
        () ->
            assets.saveDepositDetails(
                portfolioId,
                id,
                new LongTermAssetsApi.DepositDetailsCommand(
                    form.maturityDate,
                    form.annualInterestRate,
                    form.taxRate,
                    form.interestTreatment)),
        feedback);
    return redirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/valuation-periods")
  public String addValuationPeriod(
      @PathVariable Long id,
      @RequestParam Long portfolioId,
      @RequestParam LocalDate validFrom,
      @RequestParam(required = false) LocalDate validTo,
      @RequestParam BigDecimal expectedAnnualGrowthRatePercent,
      RedirectAttributes feedback) {
    mutate(
        () ->
            assets.addValuation(
                portfolioId,
                id,
                new LongTermAssetsApi.ValuationCommand(
                    validFrom, validTo, expectedAnnualGrowthRatePercent)),
        feedback);
    return redirect(id, portfolioId);
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
    mutate(
        () ->
            assets.updateValuation(
                portfolioId,
                id,
                periodId,
                new LongTermAssetsApi.ValuationCommand(
                    validFrom, validTo, expectedAnnualGrowthRatePercent)),
        feedback);
    return redirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/valuation-periods/{periodId}/delete")
  public String deleteValuationPeriod(
      @PathVariable Long id,
      @PathVariable Long periodId,
      @RequestParam Long portfolioId,
      RedirectAttributes feedback) {
    mutate(() -> assets.deleteValuation(portfolioId, id, periodId), feedback);
    return redirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/rental-tax-policy")
  public String saveRentalTaxPolicy(
      @RequestParam Long portfolioId,
      @ModelAttribute RentalTaxForm form,
      @RequestParam(required = false) BigDecimal ratePercent,
      @RequestParam(required = false) BigDecimal rate,
      RedirectAttributes feedback) {
    mutate(
        () ->
            assets.saveRentalTaxPolicy(
                portfolioId,
                new LongTermAssetsApi.RentalTaxCommand(
                    form.validFrom, form.validTo, ratePercent, rate)),
        feedback);
    return "redirect:/long-term-assets?portfolioId=" + portfolioId;
  }

  @PostMapping("/long-term-assets/rental-tax-policy/{policyId}")
  public String updateRentalTaxPolicy(
      @PathVariable Long policyId,
      @RequestParam Long portfolioId,
      @ModelAttribute RentalTaxForm form,
      @RequestParam(required = false) BigDecimal ratePercent,
      @RequestParam(required = false) BigDecimal rate,
      RedirectAttributes feedback) {
    mutate(
        () ->
            assets.updateRentalTaxPolicy(
                portfolioId,
                policyId,
                new LongTermAssetsApi.RentalTaxCommand(
                    form.validFrom, form.validTo, ratePercent, rate)),
        feedback);
    return taxPolicyRedirect(portfolioId);
  }

  @PostMapping("/long-term-assets/rental-tax-policy/{policyId}/delete")
  public String deleteRentalTaxPolicy(
      @PathVariable Long policyId,
      @RequestParam Long portfolioId,
      RedirectAttributes feedback) {
    mutate(() -> assets.deleteRentalTaxPolicy(portfolioId, policyId), feedback);
    return taxPolicyRedirect(portfolioId);
  }

  private static String redirect(Long id, Long portfolioId) {
    return "redirect:/long-term-assets/" + id + "?portfolioId=" + portfolioId;
  }

  private static String rentalRedirect(Long id, Long portfolioId) {
    return redirect(id, portfolioId) + "#rental-contracts";
  }

  private static String taxPolicyRedirect(Long portfolioId) {
    return "redirect:/long-term-assets?portfolioId=" + portfolioId + "#rental-tax-policies";
  }

  private static void mutate(Runnable action, RedirectAttributes feedback) {
    try {
      action.run();
    } catch (IllegalArgumentException | NoSuchElementException exception) {
      feedback.addFlashAttribute("error", assetError(exception));
    }
  }

  private static String assetError(RuntimeException exception) {
    String message = exception.getMessage();
    if (message != null && message.toLowerCase(java.util.Locale.ROOT).contains("type"))
      return "Asset type cannot be changed.";
    return message == null || message.isBlank() ? "Long-term asset could not be updated." : message;
  }

  private static String rentalError(RuntimeException exception) {
    String message =
        exception.getMessage() == null
            ? ""
            : exception.getMessage().toLowerCase(java.util.Locale.ROOT);
    if (message.contains("email")) return "Enter a valid tenant email address.";
    if (message.contains("amount") || message.contains("term"))
      return "Enter valid, non-negative contract amounts.";
    if (message.contains("overlapping")) return "This contract overlaps another rental contract.";
    if (message.contains("start") || message.contains("end") || message.contains("termination"))
      return "Check the contract start, expected end, and termination dates.";
    if (message.contains("real-estate")) return "Rental contracts require a real-estate asset.";
    if (message.contains("not found")) return "Rental contract or property was not found.";
    return "Rental contract could not be saved. Check the entered values.";
  }

  private static void preserveBindingErrors(BindingResult binding, RedirectAttributes feedback) {
    binding.getModel().forEach(feedback::addFlashAttribute);
    feedback.addFlashAttribute("error", "Check the highlighted contract fields.");
    feedback.addFlashAttribute(
        "rentalRejectedValues",
        binding.getFieldErrors().stream()
            .filter(error -> error.getRejectedValue() != null)
            .collect(
                java.util.stream.Collectors.toMap(
                    org.springframework.validation.FieldError::getField,
                    error -> String.valueOf(error.getRejectedValue()),
                    (first, ignored) -> first)));
    feedback.addFlashAttribute(
        "rentalBindingErrors",
        binding.getFieldErrors().stream()
            .map(
                error ->
                    "Invalid "
                        + error.getField().replaceAll("([A-Z])", " $1").toLowerCase()
                        + (error.getRejectedValue() == null
                            ? "."
                            : ": " + error.getRejectedValue() + "."))
            .distinct()
            .toList());
  }

  public record RealEstateForm(
      String name,
      CurrencyType currency,
      LocalDate acquisitionDate,
      BigDecimal acquisitionValue,
      BigDecimal currentValue,
      BigDecimal taxBase,
      BigDecimal monthlyRent,
      BigDecimal monthlyParkingIncome,
      BigDecimal monthlyAdministrationCost,
      BigDecimal monthlyOtherCost,
      BigDecimal annualPropertyTax,
      BigDecimal annualInsurance,
      LocalDate effectiveFrom,
      String notes) {}

  @Getter
  @Setter
  public static class RentalContractForm {
    private String tenantName;
    private String tenantEmail;
    private String tenantPhone;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal monthlyTaxBase;
    private String rentalTaxOwnership = "INHERIT";
    private boolean endCurrentContractBeforeStart;

    private BigDecimal rent;
    private FrequencyModel rentFrequency = FrequencyModel.MONTHLY;
    private BigDecimal parkingRent;
    private FrequencyModel parkingRentFrequency = FrequencyModel.MONTHLY;
    private BigDecimal administrationFee;
    private FrequencyModel administrationFeeFrequency = FrequencyModel.MONTHLY;
    private boolean administrationFeePaidByTenant;
    private BigDecimal utilities;
    private FrequencyModel utilitiesFrequency = FrequencyModel.MONTHLY;
    private boolean utilitiesPaidByTenant;
    private BigDecimal otherIncome;
    private FrequencyModel otherIncomeFrequency = FrequencyModel.MONTHLY;
    private BigDecimal otherExpense;
    private FrequencyModel otherExpenseFrequency = FrequencyModel.MONTHLY;
    private boolean otherExpensePaidByTenant;
    private BigDecimal annualPropertyTax;
    private FrequencyModel propertyTaxFrequency = FrequencyModel.ANNUAL;
    private boolean propertyTaxPaidByTenant;
    private BigDecimal annualInsurance;
    private FrequencyModel insuranceFrequency = FrequencyModel.ANNUAL;
    private boolean insurancePaidByTenant;

    static RentalContractForm from(LongTermAssetsApi.RentalContractView contract) {
      var form = new RentalContractForm();
      form.tenantName = contract.tenantName();
      form.tenantEmail = contract.tenantEmail();
      form.tenantPhone = contract.tenantPhone();
      form.startDate = contract.startDate();
      form.endDate = contract.endDate();
      form.monthlyTaxBase = contract.monthlyTaxBase();
      form.rentalTaxOwnership =
          contract.rentalTaxPaidByTenant() == null
              ? "INHERIT"
              : contract.rentalTaxPaidByTenant() ? "TENANT" : "LANDLORD";
      contract.terms().forEach(term -> form.copy(term));
      return form;
    }

    private void copy(LongTermAssetsApi.RentalTermView term) {
      switch (term.type()) {
        case RENT -> {
          rent = term.amount();
          rentFrequency = term.frequency();
        }
        case PARKING_RENT -> {
          parkingRent = term.amount();
          parkingRentFrequency = term.frequency();
        }
        case ADMIN_FEE -> {
          administrationFee = term.amount();
          administrationFeeFrequency = term.frequency();
          administrationFeePaidByTenant = term.paidByTenant();
        }
        case UTILITIES -> {
          utilities = term.amount();
          utilitiesFrequency = term.frequency();
          utilitiesPaidByTenant = term.paidByTenant();
        }
        case OTHER_INCOME -> {
          otherIncome = term.amount();
          otherIncomeFrequency = term.frequency();
        }
        case OTHER_EXPENSE -> {
          otherExpense = term.amount();
          otherExpenseFrequency = term.frequency();
          otherExpensePaidByTenant = term.paidByTenant();
        }
        case PROPERTY_TAX -> {
          annualPropertyTax = term.amount();
          propertyTaxFrequency = term.frequency();
          propertyTaxPaidByTenant = term.paidByTenant();
        }
        case INSURANCE -> {
          annualInsurance = term.amount();
          insuranceFrequency = term.frequency();
          insurancePaidByTenant = term.paidByTenant();
        }
      }
    }

    LongTermAssetsApi.RentalContractCommand createCommand(Long portfolioId, Long assetId) {
      return new LongTermAssetsApi.RentalContractCommand(
          portfolioId,
          assetId,
          tenantName,
          tenantEmail,
          tenantPhone,
          startDate,
          endDate,
          monthlyTaxBase,
          rentalTaxPaidByTenant(),
          endCurrentContractBeforeStart,
          terms());
    }

    LongTermAssetsApi.UpdateRentalContractCommand updateCommand(
        Long portfolioId, Long assetId, Long contractId) {
      return new LongTermAssetsApi.UpdateRentalContractCommand(
          portfolioId,
          assetId,
          contractId,
          tenantName,
          tenantEmail,
          tenantPhone,
          startDate,
          endDate,
          monthlyTaxBase,
          rentalTaxPaidByTenant(),
          usesPropertyTaxPayerDefault(),
          terms());
    }

    private Boolean rentalTaxPaidByTenant() {
      return switch (rentalTaxOwnership == null ? "INHERIT" : rentalTaxOwnership) {
        case "TENANT" -> Boolean.TRUE;
        case "LANDLORD" -> Boolean.FALSE;
        default -> null;
      };
    }

    private boolean usesPropertyTaxPayerDefault() {
      return rentalTaxOwnership == null || "INHERIT".equals(rentalTaxOwnership);
    }

    private java.util.List<LongTermAssetsApi.RentalTermCommand> terms() {
      var terms = new java.util.ArrayList<LongTermAssetsApi.RentalTermCommand>();
      add(terms, CashFlowTypeModel.RENT, rent, rentFrequency, false);
      add(terms, CashFlowTypeModel.PARKING_RENT, parkingRent, parkingRentFrequency, false);
      add(
          terms,
          CashFlowTypeModel.ADMIN_FEE,
          administrationFee,
          administrationFeeFrequency,
          administrationFeePaidByTenant);
      add(terms, CashFlowTypeModel.UTILITIES, utilities, utilitiesFrequency, utilitiesPaidByTenant);
      add(terms, CashFlowTypeModel.OTHER_INCOME, otherIncome, otherIncomeFrequency, false);
      add(
          terms,
          CashFlowTypeModel.OTHER_EXPENSE,
          otherExpense,
          otherExpenseFrequency,
          otherExpensePaidByTenant);
      add(
          terms,
          CashFlowTypeModel.PROPERTY_TAX,
          annualPropertyTax,
          propertyTaxFrequency,
          propertyTaxPaidByTenant);
      add(
          terms,
          CashFlowTypeModel.INSURANCE,
          annualInsurance,
          insuranceFrequency,
          insurancePaidByTenant);
      return java.util.List.copyOf(terms);
    }

    private static void add(
        java.util.List<LongTermAssetsApi.RentalTermCommand> terms,
        CashFlowTypeModel type,
        BigDecimal amount,
        FrequencyModel frequency,
        boolean paidByTenant) {
      if (amount != null)
        terms.add(
            new LongTermAssetsApi.RentalTermCommand(
                type,
                amount,
                frequency == null ? defaultFrequency(type) : frequency,
                paidByTenant));
    }

    private static FrequencyModel defaultFrequency(CashFlowTypeModel type) {
      return type == CashFlowTypeModel.PROPERTY_TAX || type == CashFlowTypeModel.INSURANCE
          ? FrequencyModel.ANNUAL
          : FrequencyModel.MONTHLY;
    }
  }

  public static class AssetForm {
    private Long id, portfolioId;
    private String name, notes;
    private LongTermAssetTypeModel type;
    private CurrencyType currency;
    private LocalDate acquisitionDate;
    private BigDecimal acquisitionValue, currentValue, taxBase;
    private boolean active;

    public Long getId() {
      return id;
    }

    public void setId(Long v) {
      id = v;
    }

    public Long getPortfolioId() {
      return portfolioId;
    }

    public void setPortfolioId(Long v) {
      portfolioId = v;
    }

    public String getName() {
      return name;
    }

    public void setName(String v) {
      name = v;
    }

    public String getNotes() {
      return notes;
    }

    public void setNotes(String v) {
      notes = v;
    }

    public LongTermAssetTypeModel getType() {
      return type;
    }

    public void setType(LongTermAssetTypeModel v) {
      type = v;
    }

    public CurrencyType getCurrency() {
      return currency;
    }

    public void setCurrency(CurrencyType v) {
      currency = v;
    }

    public LocalDate getAcquisitionDate() {
      return acquisitionDate;
    }

    public void setAcquisitionDate(LocalDate v) {
      acquisitionDate = v;
    }

    public BigDecimal getAcquisitionValue() {
      return acquisitionValue;
    }

    public void setAcquisitionValue(BigDecimal v) {
      acquisitionValue = v;
    }

    public BigDecimal getCurrentValue() {
      return currentValue;
    }

    public void setCurrentValue(BigDecimal v) {
      currentValue = v;
    }

    public BigDecimal getTaxBase() {
      return taxBase;
    }

    public void setTaxBase(BigDecimal v) {
      taxBase = v;
    }

    public boolean isActive() {
      return active;
    }

    public void setActive(boolean v) {
      active = v;
    }

    LongTermAssetsApi.AssetCommand command(Long p) {
      return command(p, id, null);
    }

    LongTermAssetsApi.AssetCommand command(Long p, Long i, BigDecimal tax) {
      return new LongTermAssetsApi.AssetCommand(
          p,
          i,
          name,
          type,
          currency,
          acquisitionDate,
          acquisitionValue,
          currentValue,
          tax == null ? taxBase : tax,
          active,
          notes);
    }
  }

  public static class BondDetailsForm {
    public LocalDate maturityDate;
    public BigDecimal taxRate, redemptionValue;
    public InterestTreatmentModel interestTreatment;

    public LocalDate getMaturityDate() {
      return maturityDate;
    }

    public void setMaturityDate(LocalDate v) {
      maturityDate = v;
    }

    public BigDecimal getTaxRate() {
      return taxRate;
    }

    public void setTaxRate(BigDecimal v) {
      taxRate = v;
    }

    public BigDecimal getRedemptionValue() {
      return redemptionValue;
    }

    public void setRedemptionValue(BigDecimal v) {
      redemptionValue = v;
    }

    public InterestTreatmentModel getInterestTreatment() {
      return interestTreatment;
    }

    public void setInterestTreatment(InterestTreatmentModel v) {
      interestTreatment = v;
    }
  }

  public static class DepositDetailsForm extends BondDetailsForm {
    public BigDecimal annualInterestRate;

    public BigDecimal getAnnualInterestRate() {
      return annualInterestRate;
    }

    public void setAnnualInterestRate(BigDecimal v) {
      annualInterestRate = v;
    }
  }

  public static class RentalTaxForm {
    public LocalDate validFrom, validTo;

    public LocalDate getValidFrom() {
      return validFrom;
    }

    public void setValidFrom(LocalDate v) {
      validFrom = v;
    }

    public LocalDate getValidTo() {
      return validTo;
    }

    public void setValidTo(LocalDate v) {
      validTo = v;
    }
  }
}
