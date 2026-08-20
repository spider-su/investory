package com.smartbox.investory.ui.longterm;

import com.smartbox.investory.longterm.api.*;
import com.smartbox.investory.longterm.application.LongTermAssetService;
import com.smartbox.investory.longterm.application.LongTermAssetsFacade;
import com.smartbox.investory.longterm.application.RealEstateEntry;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.presentation.FinancialPresentation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class LongTermAssetController {
  private final LongTermAssetsFacade assets;
  private final Clock clock;

  @GetMapping("/long-term-assets")
  public String list(@RequestParam(defaultValue = "1") Long portfolioId, Model model) {
    LocalDate date = LocalDate.now(clock);
    var groups = assets.grouped(portfolioId, date);
    var total = assets.aggregate(portfolioId, date);
    model.addAttribute("portfolioId", portfolioId);
    model.addAttribute("assets", assets.list(portfolioId, date));
    model.addAttribute("groups", groups);
    model.addAttribute("total", total);
    model.addAttribute("longTermHeaderTotal", total.totalCurrentValueWholeDisplay());
    model.addAttribute("longTermHeaderIncome", total.netAnnualIncomeWholeDisplay());
    model.addAttribute("longTermHeaderYield", total.netYieldWithLabelDisplay());
    model.addAttribute("longTermHeaderMonthly", total.monthlyNetIncomeWholeDisplay());
    model.addAttribute(
        "longTermGrossIncome",
        FinancialPresentation.wholeNumber(total.annualEconomics().grossAnnualIncome()));
    model.addAttribute(
        "longTermExpensesTax",
        FinancialPresentation.wholeNumber(
            total.annualEconomics().annualExpenses().add(total.annualEconomics().annualTax())));
    model.addAttribute(
        "longTermGrossYield", FinancialPresentation.percentage(total.weightedGrossYield()));
    groups.stream()
        .max(java.util.Comparator.comparing(LongTermAssetService.AssetGroupSummary::totalValue))
        .ifPresent(
            g -> {
              model.addAttribute("longTermLargestClass", g.title());
              model.addAttribute(
                  "longTermLargestClassValue", FinancialPresentation.wholeNumber(g.totalValue()));
              model.addAttribute(
                  "longTermLargestClassShare", g.shareDisplay(total.totalCurrentValue()));
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
    asset.setType(LongTermAssetType.BOND);
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

  @PostMapping("/long-term-assets/cash-reserve")
  public String saveCashReserve(
      @RequestParam Long portfolioId,
      @RequestParam(required = false) Long id,
      @RequestParam String name,
      @RequestParam CurrencyType currency,
      @RequestParam BigDecimal value,
      @RequestParam(required = false) BigDecimal annualReturnPercent,
      @RequestParam(required = false) String notes) {
    var saved =
        assets.saveCashReserve(
            new LongTermAssetsFacade.CashReserveCommand(
                portfolioId, id, name, currency, value, annualReturnPercent, notes),
            LocalDate.now(clock));
    return redirect(saved.id(), portfolioId);
  }

  @PostMapping("/long-term-assets")
  public String create(@ModelAttribute AssetForm form, @RequestParam Long portfolioId) {
    assets.create(form.command(portfolioId));
    return "redirect:/long-term-assets?portfolioId=" + portfolioId;
  }

  @PostMapping("/long-term-assets/bond")
  public String createBond(
      @RequestParam Long portfolioId,
      @RequestParam String name,
      @RequestParam CurrencyType currency,
      @RequestParam BigDecimal value,
      @RequestParam LocalDate acquisitionDate,
      @RequestParam LocalDate maturityDate,
      @RequestParam InterestTreatment interestTreatment,
      @RequestParam BigDecimal annualRatePercent,
      @RequestParam(required = false) String notes) {
    var saved =
        assets.createBond(
            new LongTermAssetsFacade.BondCommand(
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
  }

  @GetMapping("/long-term-assets/new/real-estate")
  public String realEstateForm(@RequestParam(defaultValue = "1") Long portfolioId, Model model) {
    model.addAttribute("portfolioId", portfolioId);
    return "real-estate-form";
  }

  @PostMapping("/long-term-assets/real-estate")
  public String saveRealEstate(
      @RequestParam Long portfolioId,
      @ModelAttribute RealEstateEntry entry,
      @RequestParam(name = "expectedAnnualGrowthRatePercent", required = false) BigDecimal growth) {
    assets.saveRealEstate(
        portfolioId,
        new RealEstateEntry(
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
  }

  @GetMapping("/long-term-assets/{id}")
  public String detail(
      @PathVariable Long id, @RequestParam(defaultValue = "1") Long portfolioId, Model model) {
    var view = assets.details(portfolioId, id, LocalDate.now(clock));
    model.addAttribute("asset", view.asset());
    model.addAttribute("portfolioId", portfolioId);
    model.addAttribute("summary", view.summary());
    model.addAttribute("cashFlows", view.cashFlows());
    model.addAttribute("bondDetails", view.bondDetails());
    model.addAttribute("depositDetails", view.depositDetails());
    model.addAttribute("valuationPeriods", view.valuationPeriods());
    model.addAttribute("bondRatePeriods", view.bondRatePeriods());
    model.addAttribute("currentCashFlows", view.currentCashFlows());
    model.addAttribute("rentalPeriod", view.rentalPeriod());
    model.addAttribute("availableCashFlowTypes", view.availableCashFlowTypes());
    model.addAttribute("expectedPropertyGrowth", view.expectedPropertyGrowth());
    model.addAttribute(
        "legacyBondValueDifference",
        view.asset().acquisitionValue() != null
            && view.asset().currentValue() != null
            && view.asset().acquisitionValue().compareTo(view.asset().currentValue()) != 0);
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
      @RequestParam(required = false) LocalDate effectiveFrom,
      @RequestParam(required = false) LocalDate endDate,
      @RequestParam(required = false) BigDecimal taxBase) {
    assets.update(form.command(portfolioId, id, taxBase));
    if (form.getType() == LongTermAssetType.REAL_ESTATE && effectiveFrom != null)
      assets.saveRentalPeriod(portfolioId, id, effectiveFrom, endDate, LocalDate.now(clock));
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
      @PathVariable Long id, @RequestParam Long portfolioId, @RequestParam BigDecimal taxBase) {
    assets.saveTaxBase(portfolioId, id, taxBase);
    return redirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/cash-flows")
  public String addCashFlow(
      @PathVariable Long id,
      @RequestParam Long portfolioId,
      @RequestParam CashFlowType type,
      @RequestParam BigDecimal amount,
      @RequestParam Frequency frequency,
      @RequestParam(required = false) LocalDate validFrom,
      @RequestParam(required = false) LocalDate validTo,
      @RequestParam(required = false) Boolean paidByTenant) {
    assets.addCashFlow(
        new LongTermAssetsFacade.CashFlowCommand(
            portfolioId, id, null, type, amount, frequency, validFrom, validTo, paidByTenant),
        LocalDate.now(clock));
    return redirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/cash-flows/{flowId}")
  public String changeCashFlow(
      @PathVariable Long id,
      @PathVariable Long flowId,
      @RequestParam Long portfolioId,
      @RequestParam BigDecimal amount,
      @RequestParam Frequency frequency,
      @RequestParam(required = false) LocalDate effectiveFrom,
      @RequestParam(required = false) LocalDate validTo,
      @RequestParam(required = false) Boolean paidByTenant) {
    assets.changeCashFlow(
        new LongTermAssetsFacade.CashFlowCommand(
            portfolioId,
            id,
            flowId,
            null,
            amount,
            frequency,
            effectiveFrom,
            validTo,
            paidByTenant));
    return redirect(id, portfolioId);
  }

  public String addCashFlow(
      Long id,
      Long portfolioId,
      CashFlowType type,
      BigDecimal amount,
      Frequency frequency,
      LocalDate validFrom,
      LocalDate validTo) {
    return addCashFlow(id, portfolioId, type, amount, frequency, validFrom, validTo, null);
  }

  @PostMapping("/long-term-assets/{id}/rental-tax-ownership")
  public String saveRentalTaxOwnership(
      @PathVariable Long id,
      @RequestParam Long portfolioId,
      @RequestParam(defaultValue = "false") boolean paidByTenant) {
    assets.saveRentalTaxOwnership(portfolioId, id, paidByTenant);
    return redirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/property-growth")
  public String savePropertyGrowth(
      @PathVariable Long id,
      @RequestParam Long portfolioId,
      @RequestParam(required = false) BigDecimal growthRatePercent,
      @RequestParam LocalDate effectiveFrom) {
    assets.savePropertyGrowth(portfolioId, id, growthRatePercent, effectiveFrom);
    return redirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/bond-details")
  public String saveBondDetails(
      @PathVariable Long id, @RequestParam Long portfolioId, @ModelAttribute BondDetailsForm form) {
    assets.saveBondDetails(
        portfolioId,
        id,
        new LongTermAssetsFacade.BondDetailsCommand(
            form.maturityDate, form.taxRate, form.interestTreatment, form.redemptionValue));
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
      @RequestParam InterestTreatment interestTreatment,
      @RequestParam BigDecimal annualRatePercent,
      @RequestParam(required = false) String notes) {
    assets.updateBond(
        new LongTermAssetsFacade.BondCommand(
            portfolioId,
            id,
            name,
            currency,
            value,
            acquisitionDate,
            maturityDate,
            interestTreatment,
            annualRatePercent,
            notes));
    return redirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/deposit-details")
  public String saveDepositDetails(
      @PathVariable Long id,
      @RequestParam Long portfolioId,
      @ModelAttribute DepositDetailsForm form) {
    assets.saveDepositDetails(
        portfolioId,
        id,
        new LongTermAssetsFacade.DepositDetailsCommand(
            form.maturityDate, form.annualInterestRate, form.taxRate, form.interestTreatment));
    return redirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/valuation-periods")
  public String addValuationPeriod(
      @PathVariable Long id,
      @RequestParam Long portfolioId,
      @RequestParam LocalDate validFrom,
      @RequestParam(required = false) LocalDate validTo,
      @RequestParam BigDecimal expectedAnnualGrowthRatePercent) {
    assets.addValuation(
        portfolioId,
        id,
        new LongTermAssetsFacade.ValuationCommand(
            validFrom, validTo, expectedAnnualGrowthRatePercent));
    return redirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/bond-rate-periods")
  public String addBondRatePeriod(
      @PathVariable Long id, @RequestParam Long portfolioId, @ModelAttribute BondRateForm form) {
    assets.addBondRate(
        portfolioId,
        id,
        new LongTermAssetsFacade.BondRateCommand(
            form.validFrom, form.validTo, form.annualInterestRate));
    return redirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/rental-tax-policy")
  public String saveRentalTaxPolicy(
      @RequestParam Long portfolioId,
      @ModelAttribute RentalTaxForm form,
      @RequestParam(required = false) BigDecimal ratePercent,
      @RequestParam(required = false) BigDecimal rate) {
    assets.saveRentalTaxPolicy(
        portfolioId,
        new LongTermAssetsFacade.RentalTaxCommand(form.validFrom, form.validTo, ratePercent, rate));
    return "redirect:/long-term-assets?portfolioId=" + portfolioId;
  }

  private static String redirect(Long id, Long portfolioId) {
    return "redirect:/long-term-assets/" + id + "?portfolioId=" + portfolioId;
  }

  public static class AssetForm {
    private Long id, portfolioId;
    private String name, notes;
    private LongTermAssetType type;
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

    public LongTermAssetType getType() {
      return type;
    }

    public void setType(LongTermAssetType v) {
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

    LongTermAssetsFacade.AssetCommand command(Long p) {
      return command(p, id, null);
    }

    LongTermAssetsFacade.AssetCommand command(Long p, Long i, BigDecimal tax) {
      return new LongTermAssetsFacade.AssetCommand(
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
    public InterestTreatment interestTreatment;

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

    public InterestTreatment getInterestTreatment() {
      return interestTreatment;
    }

    public void setInterestTreatment(InterestTreatment v) {
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

  public static class BondRateForm {
    public LocalDate validFrom, validTo;
    public BigDecimal annualInterestRate;

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
