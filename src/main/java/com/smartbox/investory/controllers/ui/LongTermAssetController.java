package com.smartbox.investory.controllers.ui;

import com.smartbox.investory.application.longterm.LongTermAssetService;
import com.smartbox.investory.application.longterm.RealEstateEntry;
import com.smartbox.investory.infrastructure.longterm.CashFlowType;
import com.smartbox.investory.infrastructure.longterm.Frequency;
import com.smartbox.investory.infrastructure.longterm.InterestTreatment;
import com.smartbox.investory.infrastructure.longterm.LongTermAsset;
import com.smartbox.investory.infrastructure.longterm.LongTermAssetCashFlow;
import com.smartbox.investory.services.PlanningPresentation;
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
  private final LongTermAssetService service;
  private final Clock clock;

  @GetMapping("/long-term-assets")
  public String list(@RequestParam(defaultValue = "1") Long portfolioId, Model model) {
    LocalDate date = LocalDate.now(clock);
    model.addAttribute("portfolioId", portfolioId);
    model.addAttribute("assets", service.list(portfolioId, date));
    var groups = service.grouped(portfolioId, date);
    model.addAttribute("groups", groups);
    var total = service.aggregateForLongTermAssets(portfolioId, date);
    model.addAttribute("total", total);
    model.addAttribute("longTermHeaderTotal", total.totalCurrentValueWholeDisplay());
    model.addAttribute("longTermHeaderIncome", total.netAnnualIncomeWholeDisplay());
    model.addAttribute("longTermHeaderYield", total.netYieldWithLabelDisplay());
    model.addAttribute("longTermHeaderMonthly", total.monthlyNetIncomeWholeDisplay());
    model.addAttribute(
        "longTermGrossIncome",
        PlanningPresentation.wholeNumber(total.annualEconomics().grossAnnualIncome()));
    model.addAttribute(
        "longTermExpensesTax",
        PlanningPresentation.wholeNumber(
            total.annualEconomics().annualExpenses().add(total.annualEconomics().annualTax())));
    model.addAttribute(
        "longTermGrossYield", PlanningPresentation.percentage(total.weightedGrossYield()));
    groups.stream()
        .max(java.util.Comparator.comparing(group -> group.totalValue()))
        .ifPresent(
            largest -> {
              model.addAttribute("longTermLargestClass", largest.title());
              model.addAttribute(
                  "longTermLargestClassValue",
                  PlanningPresentation.wholeNumber(largest.totalValue()));
              model.addAttribute(
                  "longTermLargestClassShare", largest.shareDisplay(total.totalCurrentValue()));
            });
    return "long-term-assets";
  }

  @GetMapping("/long-term-assets/new")
  public String createForm(@RequestParam(defaultValue = "1") Long portfolioId, Model model) {
    LongTermAsset asset = new LongTermAsset();
    asset.setPortfolioId(portfolioId);
    asset.setActive(true);
    model.addAttribute("asset", asset);
    model.addAttribute("portfolioId", portfolioId);
    return "long-term-asset-form";
  }

  @GetMapping("/long-term-assets/new/bond")
  public String bondForm(@RequestParam(defaultValue = "1") Long portfolioId, Model model) {
    LongTermAsset asset = new LongTermAsset();
    asset.setPortfolioId(portfolioId);
    asset.setType(com.smartbox.investory.infrastructure.longterm.LongTermAssetType.BOND);
    asset.setCurrency(com.smartbox.investory.infrastructure.CurrencyType.PLN);
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
      @RequestParam com.smartbox.investory.infrastructure.CurrencyType currency,
      @RequestParam BigDecimal value,
      @RequestParam(required = false) BigDecimal annualReturnPercent,
      @RequestParam(required = false) String notes) {
    LongTermAsset asset =
        service.saveCashReserve(
            portfolioId,
            id,
            name,
            currency,
            value,
            percentInputToRate(annualReturnPercent),
            notes,
            LocalDate.now(clock));
    return redirect(asset.getId(), portfolioId);
  }

  @PostMapping("/long-term-assets")
  public String create(@ModelAttribute LongTermAsset asset, @RequestParam Long portfolioId) {
    asset.setPortfolioId(portfolioId);
    if (asset.getType() == com.smartbox.investory.infrastructure.longterm.LongTermAssetType.BOND
        && asset.getCurrentValue() == null) asset.setCurrentValue(asset.getAcquisitionValue());
    service.save(asset);
    return "redirect:/long-term-assets?portfolioId=" + portfolioId;
  }

  @PostMapping("/long-term-assets/bond")
  public String createBond(
      @RequestParam Long portfolioId,
      @RequestParam String name,
      @RequestParam com.smartbox.investory.infrastructure.CurrencyType currency,
      @RequestParam BigDecimal value,
      @RequestParam LocalDate acquisitionDate,
      @RequestParam LocalDate maturityDate,
      @RequestParam InterestTreatment interestTreatment,
      @RequestParam BigDecimal annualRatePercent,
      @RequestParam(required = false) String notes) {
    LongTermAsset asset = new LongTermAsset();
    asset.setPortfolioId(portfolioId);
    asset.setName(name);
    asset.setType(com.smartbox.investory.infrastructure.longterm.LongTermAssetType.BOND);
    asset.setCurrency(currency);
    asset.setAcquisitionValue(value);
    asset.setCurrentValue(value);
    asset.setAcquisitionDate(acquisitionDate);
    asset.setNotes(notes);
    asset.setActive(true);
    service.save(asset);
    service.saveSimpleBond(
        portfolioId,
        asset.getId(),
        percentInputToRate(annualRatePercent),
        maturityDate,
        interestTreatment);
    return redirect(asset.getId(), portfolioId);
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
      @RequestParam(name = "expectedAnnualGrowthRatePercent", required = false)
          BigDecimal expectedAnnualGrowthRatePercent) {
    entry =
        withExpectedAnnualGrowthRate(entry, percentInputToRate(expectedAnnualGrowthRatePercent));
    LongTermAsset asset = service.saveRealEstateEntry(portfolioId, null, entry);
    return redirect(asset.getId(), portfolioId);
  }

  @GetMapping("/long-term-assets/{id}")
  public String detail(
      @PathVariable Long id, @RequestParam(defaultValue = "1") Long portfolioId, Model model) {
    LongTermAsset asset = service.get(portfolioId, id).orElseThrow();
    model.addAttribute("asset", asset);
    model.addAttribute("portfolioId", portfolioId);
    model.addAttribute("summary", service.summary(asset, LocalDate.now(clock)));
    model.addAttribute("cashFlows", service.cashFlows(portfolioId, id));
    model.addAttribute("bondDetails", service.bondDetails(portfolioId, id).orElse(null));
    model.addAttribute("depositDetails", service.depositDetails(portfolioId, id).orElse(null));
    model.addAttribute("valuationPeriods", service.valuationPeriods(portfolioId, id));
    model.addAttribute("bondRatePeriods", service.bondRatePeriods(portfolioId, id));
    if (asset.getType() == com.smartbox.investory.infrastructure.longterm.LongTermAssetType.BOND) {
      model.addAttribute(
          "legacyBondValueDifference",
          asset.getAcquisitionValue() != null
              && asset.getCurrentValue() != null
              && asset.getAcquisitionValue().compareTo(asset.getCurrentValue()) != 0);
      return "bond-detail";
    }
    if (asset.getType()
        == com.smartbox.investory.infrastructure.longterm.LongTermAssetType.REAL_ESTATE) {
      LocalDate today = LocalDate.now(clock);
      model.addAttribute("currentCashFlows", service.currentCashFlows(portfolioId, id, today));
      model.addAttribute("rentalPeriod", service.rentalPeriod(portfolioId, id, today));
      model.addAttribute(
          "availableCashFlowTypes", service.availableCashFlowTypes(portfolioId, id, today));
      model.addAttribute(
          "expectedPropertyGrowth",
          service.valuationPeriods(portfolioId, id).stream()
              .filter(p -> p.getValidTo() == null || !p.getValidTo().isBefore(LocalDate.now(clock)))
              .reduce((first, second) -> second)
              .map(
                  com.smartbox.investory.infrastructure.longterm.LongTermAssetValuationPeriod
                      ::getExpectedAnnualGrowthRate)
              .orElse(null));
      return "real-estate-detail";
    }
    if (asset.getType()
        == com.smartbox.investory.infrastructure.longterm.LongTermAssetType.CASH_RESERVE)
      return "cash-reserve-detail";
    return "long-term-asset-detail";
  }

  @PostMapping("/long-term-assets/{id}")
  public String update(
      @PathVariable Long id,
      @ModelAttribute LongTermAsset form,
      @RequestParam Long portfolioId,
      @RequestParam(required = false) LocalDate effectiveFrom,
      @RequestParam(required = false) LocalDate endDate,
      @RequestParam(required = false) BigDecimal taxBase) {
    LongTermAsset asset = service.get(portfolioId, id).orElseThrow();
    asset.setName(form.getName());
    asset.setType(form.getType());
    asset.setCurrency(form.getCurrency());
    asset.setAcquisitionDate(form.getAcquisitionDate());
    asset.setAcquisitionValue(form.getAcquisitionValue());
    asset.setCurrentValue(form.getCurrentValue());
    // Bind this property explicitly.  It is optional, so a cleared form field must also clear
    // the persisted value rather than relying on entity binding to populate it.
    asset.setTaxBase(taxBase);
    asset.setNotes(form.getNotes());
    service.save(asset);
    if (asset.getType()
            == com.smartbox.investory.infrastructure.longterm.LongTermAssetType.REAL_ESTATE
        && effectiveFrom != null)
      service.saveRentalPeriod(portfolioId, id, effectiveFrom, endDate, LocalDate.now(clock));
    return "redirect:/long-term-assets/" + id + "?portfolioId=" + portfolioId;
  }

  /** Compatibility overload for callers that still pass the bound entity tax base. */
  String update(
      Long id, LongTermAsset form, Long portfolioId, LocalDate effectiveFrom, LocalDate endDate) {
    return update(id, form, portfolioId, effectiveFrom, endDate, form.getTaxBase());
  }

  @PostMapping("/long-term-assets/{id}/archive")
  public String archive(@PathVariable Long id, @RequestParam Long portfolioId) {
    service.archive(portfolioId, id);
    return "redirect:/long-term-assets?portfolioId=" + portfolioId;
  }

  @PostMapping("/long-term-assets/{id}/tax-base")
  public String updateTaxBase(
      @PathVariable Long id, @RequestParam Long portfolioId, @RequestParam BigDecimal taxBase) {
    service.updateTaxBase(portfolioId, id, taxBase);
    return "redirect:/long-term-assets?portfolioId=" + portfolioId;
  }

  @PostMapping("/long-term-assets/{id}/reactivate")
  public String reactivate(@PathVariable Long id, @RequestParam Long portfolioId) {
    service.reactivate(portfolioId, id);
    return "redirect:/long-term-assets/" + id + "?portfolioId=" + portfolioId;
  }

  @PostMapping("/long-term-assets/{id}/cash-flows")
  public String addCashFlow(
      @PathVariable Long id,
      @RequestParam Long portfolioId,
      @RequestParam CashFlowType type,
      @RequestParam BigDecimal amount,
      @RequestParam Frequency frequency,
      @RequestParam(required = false) LocalDate validFrom,
      @RequestParam(required = false) LocalDate validTo) {
    LongTermAssetCashFlow flow = new LongTermAssetCashFlow();
    flow.setType(type);
    flow.setAmount(amount);
    flow.setFrequency(frequency);
    if (validFrom != null) {
      flow.setValidFrom(validFrom);
      flow.setValidTo(validTo);
      service.addCashFlow(portfolioId, id, flow);
    } else {
      service.addCashFlow(portfolioId, id, flow, LocalDate.now(clock));
    }
    return "redirect:/long-term-assets/" + id + "?portfolioId=" + portfolioId;
  }

  @PostMapping("/long-term-assets/{id}/cash-flows/{flowId}")
  public String changeCashFlow(
      @PathVariable Long id,
      @PathVariable Long flowId,
      @RequestParam Long portfolioId,
      @RequestParam BigDecimal amount,
      @RequestParam Frequency frequency,
      @RequestParam(required = false) LocalDate effectiveFrom,
      @RequestParam(required = false) LocalDate validTo) {
    if (effectiveFrom == null)
      service.changeCurrentCashFlow(portfolioId, id, flowId, amount, frequency);
    else service.changeCashFlow(portfolioId, id, flowId, amount, frequency, effectiveFrom, validTo);
    return redirect(id, portfolioId);
  }

  /** Compatibility overload for callers using the pre-date form contract. */
  String addCashFlow(
      Long id,
      Long portfolioId,
      CashFlowType type,
      BigDecimal amount,
      Frequency frequency,
      LocalDate validFrom) {
    return addCashFlow(id, portfolioId, type, amount, frequency, validFrom, null);
  }

  @PostMapping("/long-term-assets/{id}/property-growth")
  public String savePropertyGrowth(
      @PathVariable Long id,
      @RequestParam Long portfolioId,
      @RequestParam(required = false) BigDecimal growthRatePercent,
      @RequestParam LocalDate effectiveFrom) {
    service.saveExpectedPropertyGrowth(
        portfolioId, id, percentInputToRate(growthRatePercent), effectiveFrom);
    return redirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/bond-details")
  public String saveBondDetails(
      @PathVariable Long id,
      @RequestParam Long portfolioId,
      @ModelAttribute
          com.smartbox.investory.infrastructure.longterm.LongTermAssetBondDetails details) {
    service.saveBondDetails(portfolioId, id, details);
    return redirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/bond")
  public String updateBond(
      @PathVariable Long id,
      @RequestParam Long portfolioId,
      @RequestParam String name,
      @RequestParam com.smartbox.investory.infrastructure.CurrencyType currency,
      @RequestParam BigDecimal value,
      @RequestParam LocalDate acquisitionDate,
      @RequestParam LocalDate maturityDate,
      @RequestParam InterestTreatment interestTreatment,
      @RequestParam BigDecimal annualRatePercent,
      @RequestParam(required = false) String notes) {
    LongTermAsset asset = service.get(portfolioId, id).orElseThrow();
    boolean legacyValueDifference =
        asset.getAcquisitionValue() != null
            && asset.getCurrentValue() != null
            && asset.getAcquisitionValue().compareTo(asset.getCurrentValue()) != 0;
    asset.setName(name);
    asset.setCurrency(currency);
    if (!legacyValueDifference) asset.setAcquisitionValue(value);
    asset.setCurrentValue(value);
    asset.setAcquisitionDate(acquisitionDate);
    asset.setNotes(notes);
    service.save(asset);
    service.saveSimpleBond(
        portfolioId, id, percentInputToRate(annualRatePercent), maturityDate, interestTreatment);
    return redirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/deposit-details")
  public String saveDepositDetails(
      @PathVariable Long id,
      @RequestParam Long portfolioId,
      @ModelAttribute
          com.smartbox.investory.infrastructure.longterm.LongTermAssetDepositDetails details) {
    service.saveDepositDetails(portfolioId, id, details);
    return redirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/valuation-periods")
  public String addValuationPeriod(
      @PathVariable Long id,
      @RequestParam Long portfolioId,
      @RequestParam LocalDate validFrom,
      @RequestParam(required = false) LocalDate validTo,
      @RequestParam BigDecimal expectedAnnualGrowthRatePercent) {
    var period = new com.smartbox.investory.infrastructure.longterm.LongTermAssetValuationPeriod();
    period.setValidFrom(validFrom);
    period.setValidTo(validTo);
    period.setExpectedAnnualGrowthRate(percentInputToRate(expectedAnnualGrowthRatePercent));
    service.addValuationPeriod(portfolioId, id, period);
    return redirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/{id}/bond-rate-periods")
  public String addBondRatePeriod(
      @PathVariable Long id,
      @RequestParam Long portfolioId,
      @ModelAttribute
          com.smartbox.investory.infrastructure.longterm.LongTermAssetBondRatePeriod period) {
    service.addBondRatePeriod(portfolioId, id, period);
    return redirect(id, portfolioId);
  }

  @PostMapping("/long-term-assets/rental-tax-policy")
  public String saveRentalTaxPolicy(
      @RequestParam Long portfolioId,
      @ModelAttribute com.smartbox.investory.infrastructure.longterm.RentalTaxPolicy policy,
      @RequestParam(required = false) BigDecimal ratePercent,
      @RequestParam(required = false) BigDecimal rate) {
    policy.setRate(ratePercent == null ? rate : percentInputToRate(ratePercent));
    service.saveRentalTaxPolicy(portfolioId, policy);
    return "redirect:/long-term-assets?portfolioId=" + portfolioId;
  }

  private static String redirect(Long id, Long portfolioId) {
    return "redirect:/long-term-assets/" + id + "?portfolioId=" + portfolioId;
  }

  /** Converts a value entered in a UI field labelled '%' to an internal decimal rate. */
  static BigDecimal percentInputToRate(BigDecimal value) {
    return value == null ? null : value.movePointLeft(2);
  }

  private static RealEstateEntry withExpectedAnnualGrowthRate(
      RealEstateEntry entry, BigDecimal expectedAnnualGrowthRate) {
    return new RealEstateEntry(
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
        expectedAnnualGrowthRate,
        entry.notes());
  }
}
