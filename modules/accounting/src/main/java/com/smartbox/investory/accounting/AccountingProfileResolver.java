package com.smartbox.investory.accounting;

import java.time.LocalDate;
import java.util.List;

/** Resolves effective accounting state for one requested accounting period. */
public final class AccountingProfileResolver {
  public ResolvedProfile resolve(
      LocalDate period,
      List<BusinessActivityPeriod> activityPeriods,
      List<EmploymentInsurancePeriod> employmentPeriods,
      List<AccountingTaxProfilePeriod> taxPeriods) {
    BusinessActivityPeriod activity =
        activityPeriods.stream().filter(p -> p.activeOn(period)).findFirst().orElse(null);
    boolean qualifyingUop =
        employmentPeriods.stream()
            .anyMatch(p -> p.activeOn(period) && p.qualifiesAsPrimarySocialInsuranceTitle());
    AccountingTaxProfilePeriod tax =
        taxPeriods.stream().filter(p -> p.activeOn(period)).findFirst().orElse(null);
    return new ResolvedProfile(
        activity != null && activity.activeOn(period),
        qualifyingUop,
        tax == null ? null : tax.ryczaltRate(),
        tax != null && tax.vatRegistered(),
        tax != null && tax.vatEuRegistered(),
        tax == null ? null : tax.zusRegime(),
        tax != null && tax.voluntarySickness());
  }

  public record ResolvedProfile(
      boolean jdgActive,
      boolean qualifyingUop,
      java.math.BigDecimal ryczaltRate,
      boolean vatRegistered,
      boolean vatEuRegistered,
      String zusRegime,
      boolean voluntarySickness) {}
}
