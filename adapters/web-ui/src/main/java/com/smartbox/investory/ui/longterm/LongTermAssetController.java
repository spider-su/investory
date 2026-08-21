package com.smartbox.investory.ui.longterm;

import com.smartbox.investory.longterm.api.*;
import com.smartbox.investory.longterm.api.model.CashFlowTypeModel;
import com.smartbox.investory.longterm.api.model.FrequencyModel;
import com.smartbox.investory.longterm.api.model.InterestTreatmentModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetTypeModel;
import com.smartbox.investory.longterm.api.model.RealEstateEntryModel;
import com.smartbox.investory.longterm.infrastructure.InterestTreatment;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType;
import com.smartbox.investory.longterm.infrastructure.rental.CashFlowType;
import com.smartbox.investory.longterm.infrastructure.rental.Frequency;
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
  private final LongTermAssetsApi assets;
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
    model.addAttribute(
        "longTermHeaderTotal", FinancialPresentation.wholeNumber(total.totalCurrentValue()));
    model.addAttribute(
        "longTermHeaderIncome",
        FinancialPresentation.wholeNumber(total.annualEconomics().netAnnualIncomeAfterTax()));
    model.addAttribute(
        "longTermHeaderYield",
        FinancialPresentation.percentage(total.annualEconomics().netYieldAfterTax())
            + " net yield");
    model.addAttribute(
        "longTermHeaderMonthly",
        FinancialPresentation.wholeNumber(
            total
                .annualEconomics()
                .netAnnualIncomeAfterTax()
                .divide(BigDecimal.valueOf(12), 2, java.math.RoundingMode.HALF_UP)));
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
    groups.stream()
        .max(java.util.Comparator.comparing(g -> g.totalValue()))
        .ifPresent(
            g -> {
              model.addAttribute("longTermLargestClass", g.title());
              model.addAttribute(
                  "longTermLargestClassValue", FinancialPresentation.wholeNumber(g.totalValue()));
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
            new LongTermAssetsApi.CashReserveCommand(
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
            new LongTermAssetsApi.BondCommand(
                portfolioId,
                null,
                name,
                currency,
                value,
                acquisitionDate,
                maturityDate,
                InterestTreatmentModel.valueOf(interestTreatment.name()),
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
      @ModelAttribute RealEstateEntryModel entry,
      @RequestParam(name = "expectedAnnualGrowthRatePercent", required = false) BigDecimal growth) {
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
      @RequestParam LocalDate startDate,
      @RequestParam(required = false) LocalDate endDate,
      @RequestParam(required = false) Boolean rentalTaxPaidByTenant,
      @RequestParam(required = false) BigDecimal rent,
      @RequestParam(required = false) BigDecimal parkingRent,
      @RequestParam(required = false) BigDecimal administrationFee,
      @RequestParam(required = false) BigDecimal utilities,
      @RequestParam(required = false) BigDecimal otherIncome,
      @RequestParam(required = false) BigDecimal otherExpense) {
    assets.createRentalContract(
        new LongTermAssetsApi.RentalContractCommand(
            portfolioId,
            id,
            startDate,
            endDate,
            rentalTaxPaidByTenant,
            java.util.List.of(
                new LongTermAssetsApi.RentalTermCommand(
                    CashFlowTypeModel.RENT, zero(rent), FrequencyModel.MONTHLY, false),
                new LongTermAssetsApi.RentalTermCommand(
                    CashFlowTypeModel.PARKING_RENT, zero(parkingRent), FrequencyModel.MONTHLY, false),
                new LongTermAssetsApi.RentalTermCommand(
                    CashFlowTypeModel.ADMIN_FEE, zero(administrationFee), FrequencyModel.MONTHLY, true),
                new LongTermAssetsApi.RentalTermCommand(
                    CashFlowTypeModel.UTILITIES, zero(utilities), FrequencyModel.MONTHLY, true),
                new LongTermAssetsApi.RentalTermCommand(
                    CashFlowTypeModel.OTHER_INCOME, zero(otherIncome), FrequencyModel.MONTHLY, false),
                new LongTermAssetsApi.RentalTermCommand(
                    CashFlowTypeModel.OTHER_EXPENSE, zero(otherExpense), FrequencyModel.MONTHLY, false))));
    return redirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/rental-contracts/{contractId}/end")
  public String endRentalContract(
      @PathVariable Long id,
      @PathVariable Long contractId,
      @RequestParam Long portfolioId,
      @RequestParam LocalDate endDate) {
    assets.endRentalContract(portfolioId, id, contractId, endDate);
    return redirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/rental-contracts/{contractId}/terminate")
  public String terminateRentalContract(
      @PathVariable Long id,
      @PathVariable Long contractId,
      @RequestParam Long portfolioId,
      @RequestParam LocalDate terminationDate) {
    assets.terminateRentalContract(portfolioId, id, contractId, terminationDate);
    return redirect(id, portfolioId);
  }

  private static BigDecimal zero(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  @GetMapping("/long-term-assets/{id}")
  public String detail(
      @PathVariable Long id, @RequestParam(defaultValue = "1") Long portfolioId, Model model) {
    var view = assets.details(portfolioId, id, LocalDate.now(clock));
    model.addAttribute("asset", view.asset());
    model.addAttribute("portfolioId", portfolioId);
    model.addAttribute("summary", view.summary());
    model.addAttribute("contracts", view.contracts());
    model.addAttribute("bondDetails", view.bondDetails());
    model.addAttribute("depositDetails", view.depositDetails());
    model.addAttribute("valuationPeriods", view.valuationPeriods());
    model.addAttribute("bondRatePeriods", view.bondRatePeriods());
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
      @RequestParam(required = false) BigDecimal taxBase) {
    assets.update(form.command(portfolioId, id, taxBase));
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
        new LongTermAssetsApi.CashFlowCommand(
            portfolioId, id, null, CashFlowTypeModel.valueOf(type.name()), amount,
            FrequencyModel.valueOf(frequency.name()), validFrom, validTo, paidByTenant),
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
        new LongTermAssetsApi.CashFlowCommand(
            portfolioId,
            id,
            flowId,
            null,
            amount,
            FrequencyModel.valueOf(frequency.name()),
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
        new LongTermAssetsApi.BondDetailsCommand(
            form.maturityDate, form.taxRate,
            InterestTreatmentModel.valueOf(form.interestTreatment.name()), form.redemptionValue));
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
        new LongTermAssetsApi.BondCommand(
            portfolioId,
            id,
            name,
            currency,
            value,
            acquisitionDate,
            maturityDate,
                InterestTreatmentModel.valueOf(interestTreatment.name()),
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
        new LongTermAssetsApi.DepositDetailsCommand(
            form.maturityDate, form.annualInterestRate, form.taxRate,
            InterestTreatmentModel.valueOf(form.interestTreatment.name())));
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
        new LongTermAssetsApi.ValuationCommand(
            validFrom, validTo, expectedAnnualGrowthRatePercent));
    return redirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/bond-rate-periods")
  public String addBondRatePeriod(
      @PathVariable Long id, @RequestParam Long portfolioId, @ModelAttribute BondRateForm form) {
    assets.addBondRate(
        portfolioId,
        id,
        new LongTermAssetsApi.BondRateCommand(
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
        new LongTermAssetsApi.RentalTaxCommand(form.validFrom, form.validTo, ratePercent, rate));
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

    LongTermAssetsApi.AssetCommand command(Long p) {
      return command(p, id, null);
    }

    LongTermAssetsApi.AssetCommand command(Long p, Long i, BigDecimal tax) {
      return new LongTermAssetsApi.AssetCommand(
          p,
          i,
          name,
          LongTermAssetTypeModel.valueOf(type.name()),
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
