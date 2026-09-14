package com.smartbox.investory.accounting.service;

import com.smartbox.investory.accounting.*;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.BankRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.ComparisonRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.ExpenseRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.FxCalculation;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.InvoiceRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.ObligationRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.ReconciliationRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.RyczaltCalculation;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.TaxInputRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.VatCalculation;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.ZusCalculation;
import com.smartbox.investory.accounting.infrastructure.persistence.*;
import com.smartbox.investory.accounting.infrastructure.persistence.AccountingFactRepository;
import com.smartbox.investory.accounting.infrastructure.persistence.AccountingPocRepository;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyConversionUnavailableException;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AccountingFactService {
  private static final LocalDate JULY_2026 = LocalDate.of(2026, 7, 1);
  private static final LocalDate OPERATIONAL_MONTH = LocalDate.of(2026, 9, 1);
  private static final BigDecimal HALF = new BigDecimal("0.50");

  private final AccountingFactRepository factRepository;
  private final AccountingPocRepository pocRepository;
  private final CurrencyConversion currencyConversion;
  private final AccountingMonthCalculator calculator;
  private final AccountingProfileResolver profileResolver;

  public AccountingFactService(
      AccountingFactRepository factRepository,
      AccountingPocRepository pocRepository,
      CurrencyConversion currencyConversion) {
    this(
        factRepository,
        pocRepository,
        currencyConversion,
        new DefaultAccountingMonthCalculator(currencyConversion),
        new AccountingProfileResolver());
  }

  @Autowired
  public AccountingFactService(
      AccountingFactRepository factRepository,
      AccountingPocRepository pocRepository,
      CurrencyConversion currencyConversion,
      AccountingMonthCalculator calculator,
      AccountingProfileResolver profileResolver) {
    this.factRepository = factRepository;
    this.pocRepository = pocRepository;
    this.currencyConversion = currencyConversion;
    this.calculator = calculator;
    this.profileResolver = profileResolver;
  }

  public List<AccountingFact> facts() {
    return factRepository.findAll();
  }

  public List<LocalDate> availablePeriods() {
    return pocRepository.availablePeriods();
  }

  public List<LocalDate> availablePeriods(long profileId) {
    return pocRepository.availablePeriods(profileId);
  }

  public AccountingProfile accountingProfile() {
    AccountingProfile profile = pocRepository.accountingProfile();
    return profile == null ? AccountingProfile.defaultProfile() : profile;
  }

  public void updateHasUop(boolean hasUop) {
    pocRepository.updateHasUop(hasUop);
  }

  public AccountingMonthSnapshot snapshot(LocalDate period) {
    return snapshot(1L, period);
  }

  public AccountingMonthSnapshot snapshot(long profileId, LocalDate period) {
    List<InvoiceRow> invoices =
        profileId == 1
            ? pocRepository.invoicesForPeriod(period)
            : pocRepository.invoicesForPeriod(profileId, period);
    // The immutable pre-KSeF fixture stores FK1 as correction_* fields on FV4. Keep this
    // compatibility path explicit and isolated. New KSeF CREDIT_NOTE rows are signed rows in
    // their own sale-date tax period and must not be moved to July by this legacy adjustment.
    List<InvoiceRow> legacyJulyCorrectionSources =
        JULY_2026.equals(period)
            ? (profileId == 1
                ? pocRepository.invoicesForPeriod(period.minusMonths(1))
                : pocRepository.invoicesForPeriod(profileId, period.minusMonths(1)))
            : List.of();
    List<ExpenseRow> expenses =
        profileId == 1
            ? pocRepository.expensesForPeriod(period)
            : pocRepository.expensesForPeriod(profileId, period);
    List<BankRow> bankTransactions =
        profileId == 1
            ? pocRepository.bankTransactionsForPeriod(period)
            : pocRepository.bankTransactionsForPeriod(profileId, period);
    List<ObligationRow> obligations =
        profileId == 1
            ? pocRepository.obligationsForPeriod(period)
            : pocRepository.obligationsForPeriod(profileId, period);
    List<TaxInputRow> taxInputs =
        profileId == 1
            ? pocRepository.taxInputsForPeriod(period)
            : pocRepository.taxInputsForPeriod(profileId, period);
    AccountingProfile profile =
        profileId == 1
            ? accountingProfile()
            : java.util.Objects.requireNonNullElse(
                pocRepository.accountingProfile(profileId), AccountingProfile.defaultProfile());

    BigDecimal domesticRevenue =
        invoices.stream()
            .filter(invoice -> "PLN".equals(invoice.currency()))
            .map(InvoiceRow::bookedNetPln)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal foreignBookedRevenue =
        invoices.stream()
            .filter(invoice -> !"PLN".equals(invoice.currency()))
            .map(InvoiceRow::bookedNetPln)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal foreignSourceEur =
        invoices.stream()
            .filter(invoice -> "EUR".equals(invoice.currency()))
            .map(InvoiceRow::netAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    AccountingCalculationMode calculationMode =
        period.isBefore(OPERATIONAL_MONTH)
            ? AccountingCalculationMode.HISTORICAL_RECONSTRUCTION
            : AccountingCalculationMode.CURRENT_CALCULATION;
    FxCalculation fx = calculateFx(invoices, foreignBookedRevenue, foreignSourceEur);
    if (calculationMode == AccountingCalculationMode.HISTORICAL_RECONSTRUCTION) {
      foreignBookedRevenue = reconstructedForeignRevenue(invoices);
    }
    RyczaltCalculation ryczalt =
        calculateRyczalt(
            period,
            calculationMode,
            invoices,
            legacyJulyCorrectionSources,
            domesticRevenue,
            foreignBookedRevenue,
            fx,
            obligations,
            taxInputs);
    VatCalculation vat =
        calculateVat(period, invoices, legacyJulyCorrectionSources, expenses, obligations);
    var activityPeriods = pocRepository.businessActivityPeriods(profileId);
    var employmentPeriods = pocRepository.employmentPeriods(profileId);
    var taxProfilePeriods = pocRepository.taxProfilePeriods(profileId);
    var resolved =
        profileResolver.resolve(period, activityPeriods, employmentPeriods, taxProfilePeriods);
    var vatTransactions =
        profileId == 1
            ? pocRepository.vatTransactionsForPeriod(period)
            : pocRepository.vatTransactionsForPeriod(profileId, period);
    var yearToDate =
        new AccountingYearToDateContext(
            profileId == 1
                ? pocRepository.yearToDateRevenue(period)
                : pocRepository.yearToDateRevenue(profileId, period),
            null,
            null,
            null,
            List.of());
    boolean hasAccountingRecord =
        !invoices.isEmpty()
            || !expenses.isEmpty()
            || !bankTransactions.isEmpty()
            || !obligations.isEmpty();
    if (hasAccountingRecord
        && (activityPeriods.isEmpty()
            || employmentPeriods.isEmpty()
            || taxProfilePeriods.isEmpty())) {
      // Legacy POC profiles may have only a subset of effective-dated rows. Fill only the missing
      // dimensions from the saved profile-level assumptions; never let a missing UoP row imply JDG
      // social insurance when the profile says the qualifying UoP flag is enabled.
      resolved =
          new AccountingProfileResolver.ResolvedProfile(
              activityPeriods.isEmpty() || resolved.jdgActive(),
              employmentPeriods.isEmpty() ? profile.hasUop() : resolved.qualifyingUop(),
              taxProfilePeriods.isEmpty() ? new BigDecimal("0.12") : resolved.ryczaltRate(),
              taxProfilePeriods.isEmpty() || resolved.vatRegistered(),
              taxProfilePeriods.isEmpty() || resolved.vatEuRegistered(),
              taxProfilePeriods.isEmpty() ? "JDG" : resolved.zusRegime(),
              taxProfilePeriods.isEmpty() ? false : resolved.voluntarySickness());
    }
    if (calculationMode == AccountingCalculationMode.HISTORICAL_RECONSTRUCTION) {
      // Historical reconstruction keeps the legacy profile assumptions where no dated history
      // exists. When a UoP timeline is present, use it for the requested month so starting UoP
      // does not waive JDG social ZUS in earlier historical months. A historical sales or expense
      // document is evidence that the JDG was active for its tax period.
      resolved =
          new AccountingProfileResolver.ResolvedProfile(
              resolved.jdgActive() || !invoices.isEmpty() || !expenses.isEmpty(),
              employmentPeriods.isEmpty() ? profile.hasUop() : resolved.qualifyingUop(),
              resolved.ryczaltRate() == null ? new BigDecimal("0.12") : resolved.ryczaltRate(),
              resolved.vatRegistered(),
              resolved.vatEuRegistered(),
              resolved.zusRegime() == null ? "JDG" : resolved.zusRegime(),
              resolved.voluntarySickness());
    }
    boolean useCalculatedZus =
        hasAccountingRecord
            && (calculationMode == AccountingCalculationMode.CURRENT_CALCULATION
                || (resolved.zusRegime() != null && resolved.ryczaltRate() != null));
    var zusCalculation =
        useCalculatedZus
            ? new ZusCalculator()
                .calculate(
                    new ZusCalculator.Input(
                        resolved.jdgActive(),
                        resolved.qualifyingUop(),
                        resolved.zusRegime(),
                        resolved.voluntarySickness(),
                        yearToDate.taxableRyczaltRevenue(),
                        ZusRules2026.FULL_JDG_SOCIAL,
                        calculationMode == AccountingCalculationMode.HISTORICAL_RECONSTRUCTION
                            ? ZusRules2026.HealthBand.HIGH
                            : null))
            : null;
    var paidContributionProjection =
        calculationMode == AccountingCalculationMode.CURRENT_CALCULATION
            ? projectPaidContributions(profileId, period, resolved, zusCalculation)
            : new AccountingPocRepository.PaidContributionProjection(List.of(), List.of());
    if (paidContributionProjection == null) {
      paidContributionProjection =
          new AccountingPocRepository.PaidContributionProjection(List.of(), List.of());
    }
    if (calculationMode == AccountingCalculationMode.CURRENT_CALCULATION
        && zusCalculation != null) {
      BigDecimal paidSocialThisYear =
          paidContributionProjection.contributions().stream()
              .filter(contribution -> "SOCIAL".equals(contribution.contributionType()))
              .filter(contribution -> contribution.paymentDate().getYear() == period.getYear())
              .map(PaidContribution::deductibleAmount)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      // A band reached in a month applies from the following month.
      LocalDate previousPeriod = period.minusMonths(1);
      BigDecimal previousYearToDateRevenue =
          profileId == 1
              ? pocRepository.yearToDateRevenue(previousPeriod)
              : pocRepository.yearToDateRevenue(profileId, previousPeriod);
      paidSocialThisYear =
          paidContributionProjection.contributions().stream()
              .filter(contribution -> "SOCIAL".equals(contribution.contributionType()))
              .filter(contribution -> contribution.paymentDate().getYear() == period.getYear())
              .filter(
                  contribution ->
                      !contribution
                          .paymentDate()
                          .isAfter(previousPeriod.withDayOfMonth(previousPeriod.lengthOfMonth())))
              .map(PaidContribution::deductibleAmount)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      var healthBand =
          ZusRules2026.healthBandAfterPaidSocial(previousYearToDateRevenue, paidSocialThisYear);
      zusCalculation =
          new ZusCalculator()
              .calculate(
                  new ZusCalculator.Input(
                      resolved.jdgActive(),
                      resolved.qualifyingUop(),
                      resolved.zusRegime(),
                      resolved.voluntarySickness(),
                      yearToDate.taxableRyczaltRevenue(),
                      ZusRules2026.FULL_JDG_SOCIAL,
                      healthBand));
      paidContributionProjection =
          projectPaidContributions(profileId, period, resolved, zusCalculation);
      if (paidContributionProjection == null) {
        paidContributionProjection =
            new AccountingPocRepository.PaidContributionProjection(List.of(), List.of());
      }
    }
    ZusCalculation zus =
        zusCalculation == null
            ? calculateZus(profile, taxInputs)
            : new ZusCalculation(
                zusCalculation.socialContribution(),
                zusCalculation.healthContribution(),
                zusCalculation.totalObligation(),
                resolved.qualifyingUop(),
                zusCalculation.reason());
    var context =
        calculationMode == AccountingCalculationMode.HISTORICAL_RECONSTRUCTION
                && activityPeriods.isEmpty()
                && employmentPeriods.isEmpty()
                && !hasAccountingRecord
            ? AccountingPeriodContext.compatibility(period, profile)
            : new AccountingPeriodContext(
                period,
                resolved.jdgActive(),
                resolved.qualifyingUop(),
                resolved.zusRegime(),
                resolved.voluntarySickness(),
                resolved.ryczaltRate(),
                resolved.vatRegistered(),
                resolved.vatEuRegistered(),
                new AccountingYearToDateContext(
                    yearToDate.taxableRyczaltRevenue(),
                    null,
                    null,
                    null,
                    calculationMode == AccountingCalculationMode.CURRENT_CALCULATION
                        ? paidContributionProjection.contributions()
                        : List.of()),
                zusCalculation == null
                    ? null
                    : new ZusCalculationInput(
                        zusCalculation.socialContribution(),
                        zusCalculation.healthContribution(),
                        BigDecimal.ZERO,
                        zusCalculation.healthBand().name(),
                        zusCalculation.reason()));
    AccountingCalculationResult calculated =
        calculator.calculate(
            new AccountingCalculationInput(
                period,
                invoices,
                expenses,
                taxInputs,
                profile,
                new AccountingCalculationInput.CalculationAdjustments(
                    JULY_2026.equals(period)
                        ? legacyJulyCorrectionSources.stream()
                            .map(InvoiceRow::correctionNetAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                        : BigDecimal.ZERO,
                    JULY_2026.equals(period)
                        ? legacyJulyCorrectionSources.stream()
                            .map(InvoiceRow::correctionVatAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                        : BigDecimal.ZERO),
                context,
                vatTransactions,
                calculationMode));
    // The tax calculation must consume the calculated ZUS health amount. Historical wFirma/ZUS
    // rows are comparison evidence only and may be absent after operational-data cleanup.
    if (calculationMode == AccountingCalculationMode.HISTORICAL_RECONSTRUCTION
        && zusCalculation != null) {
      ryczalt = calculatedHistoricalRyczalt(calculated, ryczalt, zusCalculation);
    }
    if (calculationMode == AccountingCalculationMode.CURRENT_CALCULATION) {
      domesticRevenue = calculated.revenue().domesticPln();
      foreignBookedRevenue = calculated.revenue().convertedForeignPln();
      foreignSourceEur =
          invoices.stream()
              .filter(invoice -> "EUR".equals(invoice.currency()))
              .map(InvoiceRow::netAmount)
              .filter(value -> value != null)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      fx = currentFx(calculated);
      ryczalt = currentRyczalt(calculated);
      vat = currentVat(calculated);
      zus = currentZus(calculated);
    }
    List<ComparisonRow> comparisons =
        calculationMode == AccountingCalculationMode.HISTORICAL_RECONSTRUCTION
            ? buildComparisons(
                period,
                domesticRevenue,
                foreignBookedRevenue,
                fx,
                ryczalt,
                vat,
                zus,
                obligations,
                taxInputs)
            : List.of();
    List<ReconciliationRow> reconciliations =
        reconcile(invoices, expenses, bankTransactions, obligations);
    List<AccountingIssue> issues =
        issuesFor(
            calculationMode,
            invoices,
            expenses,
            fx,
            taxInputs,
            profile,
            period,
            bankTransactions,
            reconciliations,
            obligations);
    if (calculationMode == AccountingCalculationMode.CURRENT_CALCULATION) {
      issues.addAll(paidContributionProjection.issues());
      issues.addAll(calculated.issues());
    }

    return new AccountingMonthSnapshot(
        period,
        domesticRevenue,
        foreignBookedRevenue,
        foreignSourceEur,
        domesticRevenue.add(foreignBookedRevenue),
        fx,
        ryczalt,
        vat,
        zus,
        comparisons,
        invoices,
        expenses,
        reconciliations,
        obligations,
        bankTransactions,
        calculationMode,
        readiness(issues),
        issues);
  }

  private AccountingPocRepository.PaidContributionProjection projectPaidContributions(
      long profileId,
      LocalDate period,
      AccountingProfileResolver.ResolvedProfile resolved,
      ZusCalculator.ZusCalculation currentZus) {
    var obligations = paidContributionObligations(profileId, period, resolved, currentZus);
    return profileId == 1
        ? pocRepository.paidContributionsUpTo(period, obligations)
        : pocRepository.paidContributionsUpTo(profileId, period, obligations);
  }

  private Map<LocalDate, AccountingPocRepository.ZusAmounts> paidContributionObligations(
      long profileId,
      LocalDate period,
      AccountingProfileResolver.ResolvedProfile resolved,
      ZusCalculator.ZusCalculation currentZus) {
    var activityPeriods = pocRepository.businessActivityPeriods(profileId);
    var employmentPeriods = pocRepository.employmentPeriods(profileId);
    var taxPeriods = pocRepository.taxProfilePeriods(profileId);
    var obligations = new java.util.LinkedHashMap<LocalDate, AccountingPocRepository.ZusAmounts>();
    for (LocalDate contributionPeriod :
        (profileId == 1
            ? pocRepository.zusPaymentPeriodsUpTo(period)
            : pocRepository.zusPaymentPeriodsUpTo(profileId, period))) {
      var effective =
          profileResolver.resolve(
              contributionPeriod, activityPeriods, employmentPeriods, taxPeriods);
      if (effective.zusRegime() == null) continue;
      var calculated =
          new ZusCalculator()
              .calculate(
                  new ZusCalculator.Input(
                      effective.jdgActive(),
                      effective.qualifyingUop(),
                      effective.zusRegime(),
                      effective.voluntarySickness(),
                      profileId == 1
                          ? pocRepository.yearToDateRevenue(contributionPeriod)
                          : pocRepository.yearToDateRevenue(profileId, contributionPeriod),
                      ZusRules2026.FULL_JDG_SOCIAL));
      obligations.put(
          contributionPeriod,
          new AccountingPocRepository.ZusAmounts(
              calculated.socialContribution(),
              calculated.deductibleSocialContribution(),
              calculated.healthContribution()));
    }
    if (currentZus != null) {
      obligations.put(
          period,
          new AccountingPocRepository.ZusAmounts(
              currentZus.socialContribution(),
              currentZus.deductibleSocialContribution(),
              currentZus.healthContribution()));
    }
    return obligations;
  }

  private FxCalculation currentFx(AccountingCalculationResult result) {
    String status = result.fx().complete() ? "CALCULATED" : "FX_UNAVAILABLE";
    return new FxCalculation(
        null,
        result.fx().entries().stream()
            .filter(entry -> "EUR".equals(entry.currency()))
            .map(AccountingCalculationResult.FxCalculation.Conversion::sourceAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add),
        result.fx().convertedRevenuePln(),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        status,
        result.fx().unavailableReferences());
  }

  private RyczaltCalculation currentRyczalt(AccountingCalculationResult result) {
    BigDecimal rate =
        result.ryczalt().revenueByRate().size() == 1
            ? result.ryczalt().revenueByRate().keySet().iterator().next()
            : BigDecimal.ZERO;
    return new RyczaltCalculation(
        result.ryczalt().revenueBeforeDeductions(),
        BigDecimal.ZERO,
        result.ryczalt().healthContributionPaid(),
        result.ryczalt().healthDeduction(),
        result.ryczalt().taxableBase(),
        rate,
        result.ryczalt().calculatedTax(),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        result.complete() ? "CALCULATED" : "INPUTS_INCOMPLETE");
  }

  private RyczaltCalculation calculatedHistoricalRyczalt(
      AccountingCalculationResult result,
      RyczaltCalculation comparison,
      com.smartbox.investory.accounting.ZusCalculator.ZusCalculation zusCalculation) {
    BigDecimal health = ZusRules2026.HEALTH;
    BigDecimal healthDeduction = health.multiply(HALF).setScale(2, RoundingMode.HALF_UP);
    BigDecimal taxableBase =
        comparison
            .revenueBeforeDeductions()
            .subtract(healthDeduction)
            .setScale(0, RoundingMode.HALF_UP);
    BigDecimal calculatedTax =
        taxableBase.multiply(comparison.rate()).setScale(0, RoundingMode.HALF_UP);
    BigDecimal difference = calculatedTax.subtract(comparison.expectedTax());
    return new RyczaltCalculation(
        comparison.revenueBeforeDeductions(),
        comparison.julyOnlyCorrectionNetAdjustment(),
        health,
        healthDeduction,
        taxableBase,
        comparison.rate(),
        calculatedTax,
        comparison.expectedTax(),
        difference,
        comparison.status());
  }

  private VatCalculation currentVat(AccountingCalculationResult result) {
    return new VatCalculation(
        result.vat().outputVatBeforeCorrection(),
        result.vat().salesCorrectionVat(),
        result.vat().outputVat(),
        result.vat().deductibleInputVat(),
        BigDecimal.ZERO,
        result.vat().calculatedVat(),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        result.complete() ? "CALCULATED" : "INPUTS_INCOMPLETE");
  }

  private ZusCalculation currentZus(AccountingCalculationResult result) {
    var zus = result.zus();
    return new ZusCalculation(
        zus.socialZus(), zus.healthZus(), zus.totalZus(), zus.hasUop(), zus.socialZusReasonCode());
  }

  private List<AccountingIssue> issuesFor(
      AccountingCalculationMode mode,
      List<InvoiceRow> invoices,
      List<ExpenseRow> expenses,
      FxCalculation fx,
      List<TaxInputRow> taxInputs,
      AccountingProfile profile,
      LocalDate period,
      List<BankRow> bankTransactions,
      List<ReconciliationRow> reconciliations,
      List<ObligationRow> obligations) {
    List<AccountingIssue> issues = new ArrayList<>();
    List<AccountingIssue> sourceIssues = pocRepository.sourceIssuesForPeriod(period);
    if (sourceIssues != null) issues.addAll(sourceIssues);
    if (mode != AccountingCalculationMode.CURRENT_CALCULATION) return issues;
    if (invoices.isEmpty()) {
      issues.add(
          new AccountingIssue(
              "MISSING_REQUIRED_INPUT",
              "INCOMPLETE",
              null,
              "No normalized sales invoices are available for this month."));
    }
    if (expenses.isEmpty()) {
      issues.add(
          new AccountingIssue(
              "MISSING_REQUIRED_INPUT",
              "INCOMPLETE",
              null,
              "No normalized purchase invoices are available for this month."));
    }
    if ("FX_UNAVAILABLE".equals(fx.status())) {
      issues.add(
          new AccountingIssue(
              "MISSING_FX",
              "BLOCKING",
              String.join(", ", fx.unavailableInvoiceReferences()),
              "EUR revenue cannot be converted because the required FX rate is unavailable."));
    }
    if (!obligations.isEmpty() && bankTransactions.isEmpty()) {
      issues.add(
          new AccountingIssue(
              "MISSING_BANK_INPUT",
              "INCOMPLETE",
              null,
              "No normalized bank transactions are available to reconcile this month."));
    }
    if (!bankTransactions.isEmpty()) {
      reconciliations.stream()
          .filter(row -> "UNMATCHED".equals(row.status()) || "DIFF".equals(row.status()))
          .forEach(
              row ->
                  issues.add(
                      new AccountingIssue(
                          "RECONCILIATION_REVIEW",
                          "REVIEW_REQUIRED",
                          row.reference(),
                          row.explanation())));
    }
    return issues;
  }

  private AccountingReadiness readiness(List<AccountingIssue> issues) {
    if (issues.stream().anyMatch(issue -> "REVIEW_REQUIRED".equals(issue.severity()))) {
      return AccountingReadiness.REVIEW_REQUIRED;
    }
    if (issues.stream().anyMatch(issue -> "INCOMPLETE".equals(issue.severity()))) {
      return AccountingReadiness.INCOMPLETE;
    }
    return AccountingReadiness.READY;
  }

  private ZusCalculation calculateZus(AccountingProfile profile, List<TaxInputRow> taxInputs) {
    BigDecimal healthZus = taxInput(taxInputs, "HEALTH_CONTRIBUTION_PAID");
    BigDecimal compulsorySocialZus = taxInput(taxInputs, "JDG_COMPULSORY_SOCIAL_ZUS");
    BigDecimal socialZus = profile.hasUop() ? BigDecimal.ZERO : compulsorySocialZus;
    String socialZusReasonCode =
        profile.hasUop()
            ? ZusCalculation.UOP_PRIMARY_INSURANCE
            : ZusCalculation.JDG_PRIMARY_INSURANCE;
    return new ZusCalculation(
        socialZus.setScale(2, RoundingMode.HALF_UP),
        healthZus.setScale(2, RoundingMode.HALF_UP),
        socialZus.add(healthZus).setScale(2, RoundingMode.HALF_UP),
        profile.hasUop(),
        socialZusReasonCode);
  }

  private List<ComparisonRow> buildComparisons(
      LocalDate period,
      BigDecimal domesticRevenue,
      BigDecimal foreignBookedRevenue,
      FxCalculation fx,
      RyczaltCalculation ryczalt,
      VatCalculation vat,
      ZusCalculation zus,
      List<ObligationRow> obligations,
      List<TaxInputRow> taxInputs) {
    BigDecimal sourceDerivedExpectedRevenue =
        domesticRevenue
            .add(foreignBookedRevenue)
            .add(ryczalt.julyOnlyCorrectionNetAdjustment())
            .setScale(2, RoundingMode.HALF_UP);
    BigDecimal explicitRevenueGolden = taxInput(taxInputs, "EXPECTED_REVENUE_PLN");
    BigDecimal expectedRevenue =
        explicitRevenueGolden.signum() == 0
            ? sourceDerivedExpectedRevenue
            : explicitRevenueGolden.setScale(2, RoundingMode.HALF_UP);
    BigDecimal calculatedRevenue =
        ryczalt.revenueBeforeDeductions().setScale(2, RoundingMode.HALF_UP);
    BigDecimal revenueDifference =
        calculatedRevenue.subtract(expectedRevenue).setScale(2, RoundingMode.HALF_UP);
    String revenueStatus =
        revenueDifference.signum() == 0
            ? "MATCH"
            : fx.status().startsWith("FX_UNAVAILABLE") || "NO_FX_SOURCE".equals(fx.status())
                ? "INPUTS_INCOMPLETE"
                : "DIFF";

    BigDecimal zusCalculated = zus.totalZus().setScale(2, RoundingMode.HALF_UP);
    BigDecimal zusExpected = obligationAmount(obligations, "ZUS").setScale(2, RoundingMode.HALF_UP);
    BigDecimal zusDifference =
        zusCalculated.subtract(zusExpected).setScale(2, RoundingMode.HALF_UP);
    boolean hasZusGolden = hasObligation(obligations, "ZUS");
    String zusStatus =
        !hasZusGolden
            ? "NO_GOLDEN"
            : zusCalculated.signum() == 0
                ? "INPUTS_INCOMPLETE"
                : zusDifference.signum() == 0
                    ? "MATCH"
                    : zus.hasUop() ? "DIFF" : "HISTORICAL_PROFILE_DIFF";
    String zusComparisonNote =
        !hasZusGolden
            ? "No captured historical ZUS obligation is available for this month."
            : zus.hasUop()
                ? "Captured 2026 ZUS golden is the historical health-only obligation under the qualifying-UoP profile; cash reconciliation is checked separately."
                : "Captured 2026 ZUS golden is health-only under the historical qualifying-UoP profile. The current profile disables UoP, so calculated total JDG ZUS also includes compulsory social ZUS; this difference is expected and is not a reconstruction failure.";

    return List.of(
        new ComparisonRow(
            "REVENUE",
            calculatedRevenue,
            expectedRevenue,
            revenueDifference,
            "PLN",
            revenueStatus,
            "Calculated from accounting-period sales and Investory FX; expected value comes from wFirma monthly revenue when captured."),
        new ComparisonRow(
            "RYCZALT",
            ryczalt.calculatedTax(),
            ryczalt.expectedTax(),
            ryczalt.difference(),
            "PLN",
            ryczalt.status(),
            "12% ryczałt compared with captured wFirma/bank golden output."),
        new ComparisonRow(
            "VAT",
            vat.calculatedVat(),
            vat.expectedVat(),
            vat.difference(),
            "PLN",
            vat.status(),
            "Declaration semantics: sales VAT and deductible purchase VAT are rounded independently to whole PLN before subtraction; July sales correction is represented separately."),
        new ComparisonRow(
            "ZUS", zusCalculated, zusExpected, zusDifference, "PLN", zusStatus, zusComparisonNote),
        new ComparisonRow(
            "FX",
            fx.calculatedPln(),
            fx.expectedPln(),
            fx.difference(),
            "PLN",
            fx.status(),
            fxNote(fx)));
  }

  private String fxNote(FxCalculation fx) {
    String note =
        "Foreign revenue converted through Investory CurrencyConversion; booked PLN is comparison evidence when present.";
    if (fx.unavailableInvoiceReferences().isEmpty()) return note;
    return note
        + " Unavailable invoice FX source(s): "
        + String.join(", ", fx.unavailableInvoiceReferences())
        + ". Booked PLN values are used only as an explicit fallback.";
  }

  private FxCalculation calculateFx(
      List<InvoiceRow> invoices, BigDecimal expectedForeignPln, BigDecimal foreignSourceEur) {
    List<InvoiceRow> eurInvoices =
        invoices.stream().filter(invoice -> "EUR".equals(invoice.currency())).toList();
    if (eurInvoices.isEmpty()) {
      return new FxCalculation(
          null,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          "NO_FX_SOURCE",
          List.of());
    }

    BigDecimal calculated = BigDecimal.ZERO;
    boolean bookedFallback = false;
    List<String> unavailableReferences = new ArrayList<>();
    for (InvoiceRow invoice : eurInvoices) {
      try {
        calculated =
            calculated.add(
                currencyConversion.convertToBaseCurrency(
                    invoice.netAmount(),
                    CurrencyType.PLN,
                    CurrencyType.EUR,
                    invoice.fxRateDate() != null
                        ? invoice.fxRateDate()
                        : firstNonNull(invoice.saleDate(), invoice.issueDate())));
      } catch (CurrencyConversionUnavailableException ex) {
        unavailableReferences.add(invoice.reference());
        if (invoice.bookedNetPln() != null) {
          calculated = calculated.add(invoice.bookedNetPln());
          bookedFallback = true;
        }
      }
    }
    calculated = calculated.setScale(2, RoundingMode.HALF_UP);

    boolean completeBookedComparison =
        eurInvoices.stream().allMatch(invoice -> invoice.bookedNetPln() != null);
    BigDecimal expected =
        completeBookedComparison
            ? expectedForeignPln.setScale(2, RoundingMode.HALF_UP)
            : calculated;
    String status =
        !unavailableReferences.isEmpty()
            ? bookedFallback ? "FX_UNAVAILABLE_USING_BOOKED_FALLBACK" : "FX_UNAVAILABLE"
            : !completeBookedComparison
                ? "CALCULATED"
                : calculated.compareTo(expected) == 0 ? "MATCH" : "DIFF";
    return new FxCalculation(
        eurInvoices.getFirst().fxRateDate(),
        foreignSourceEur,
        calculated,
        expected,
        calculated.subtract(expected).setScale(2, RoundingMode.HALF_UP),
        status,
        List.copyOf(unavailableReferences));
  }

  private LocalDate firstNonNull(LocalDate preferred, LocalDate fallback) {
    return preferred != null ? preferred : fallback;
  }

  /**
   * Reconstruct historical foreign revenue from booked PLN where available and the configured
   * historical FX resolver where the source record has no booked PLN value. wFirma values remain
   * comparison data; they are never required to calculate the result.
   */
  private BigDecimal reconstructedForeignRevenue(List<InvoiceRow> invoices) {
    BigDecimal total = BigDecimal.ZERO;
    for (InvoiceRow invoice : invoices) {
      if ("PLN".equals(invoice.currency())) continue;
      if (invoice.bookedNetPln() != null) {
        total = total.add(invoice.bookedNetPln());
        continue;
      }
      try {
        BigDecimal converted =
            currencyConversion.convertToBaseCurrency(
                invoice.netAmount(),
                CurrencyType.PLN,
                CurrencyType.valueOf(invoice.currency()),
                invoice.fxRateDate() != null
                    ? invoice.fxRateDate()
                    : firstNonNull(invoice.saleDate(), invoice.issueDate()));
        if (converted != null) total = total.add(converted);
      } catch (CurrencyConversionUnavailableException | IllegalArgumentException ignored) {
        // calculateFx records the missing source as a blocking diagnostic.
      }
    }
    return total.setScale(2, RoundingMode.HALF_UP);
  }

  private RyczaltCalculation calculateRyczalt(
      LocalDate period,
      AccountingCalculationMode calculationMode,
      List<InvoiceRow> invoices,
      List<InvoiceRow> legacyJulyCorrectionSources,
      BigDecimal domesticRevenue,
      BigDecimal foreignRevenuePln,
      FxCalculation fx,
      List<ObligationRow> obligations,
      List<TaxInputRow> taxInputs) {
    BigDecimal julyOnlyCorrectionNet =
        JULY_2026.equals(period)
            ? legacyJulyCorrectionSources.stream()
                .map(InvoiceRow::correctionNetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
            : BigDecimal.ZERO;
    BigDecimal foreignRevenueForTax =
        calculationMode == AccountingCalculationMode.HISTORICAL_RECONSTRUCTION
            ? foreignRevenuePln
            : fx.calculatedPln();
    BigDecimal revenueBeforeDeductions =
        domesticRevenue.add(foreignRevenueForTax).add(julyOnlyCorrectionNet);

    BigDecimal healthPaid = taxInput(taxInputs, "HEALTH_CONTRIBUTION_PAID");
    BigDecimal healthDeduction = healthPaid.multiply(HALF).setScale(2, RoundingMode.HALF_UP);
    BigDecimal taxableBase =
        revenueBeforeDeductions.subtract(healthDeduction).setScale(0, RoundingMode.HALF_UP);

    BigDecimal rate =
        invoices.stream()
            .map(InvoiceRow::ryczaltRate)
            .filter(value -> value != null)
            .findFirst()
            .orElse(new BigDecimal("0.12"));

    BigDecimal calculatedTax = taxableBase.multiply(rate).setScale(0, RoundingMode.HALF_UP);
    BigDecimal expectedTax = obligationAmount(obligations, "RYCZALT");
    BigDecimal difference = calculatedTax.subtract(expectedTax);
    boolean hasGolden = hasObligation(obligations, "RYCZALT");
    BigDecimal revenueGolden = taxInput(taxInputs, "EXPECTED_REVENUE_PLN");
    boolean missingForeignSource =
        fx.status().equals("NO_FX_SOURCE")
            && revenueGolden.signum() != 0
            && revenueGolden.compareTo(domesticRevenue) != 0;
    String status =
        !hasGolden
            ? "NO_GOLDEN"
            : missingForeignSource
                ? "INPUTS_INCOMPLETE"
                : difference.signum() == 0 ? "MATCH" : "DIFF";

    return new RyczaltCalculation(
        revenueBeforeDeductions,
        julyOnlyCorrectionNet,
        healthPaid,
        healthDeduction,
        taxableBase,
        rate,
        calculatedTax,
        expectedTax,
        difference,
        status);
  }

  private VatCalculation calculateVat(
      LocalDate period,
      List<InvoiceRow> invoices,
      List<InvoiceRow> legacyJulyCorrectionSources,
      List<ExpenseRow> expenses,
      List<ObligationRow> obligations) {
    BigDecimal outputBeforeCorrection =
        invoices.stream()
            .filter(invoice -> "PLN".equals(invoice.currency()))
            .map(InvoiceRow::vatAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal julyOnlySalesCorrectionVat =
        JULY_2026.equals(period)
            ? legacyJulyCorrectionSources.stream()
                .map(InvoiceRow::correctionVatAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
            : BigDecimal.ZERO;
    BigDecimal outputVat = outputBeforeCorrection.add(julyOnlySalesCorrectionVat);

    BigDecimal deductibleInputVat =
        expenses.stream()
            .map(ExpenseRow::deductibleVat)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);

    BigDecimal calculatedVat =
        outputVat
            .setScale(0, RoundingMode.HALF_UP)
            .subtract(deductibleInputVat.setScale(0, RoundingMode.HALF_UP))
            .max(BigDecimal.ZERO);
    BigDecimal expectedVat = obligationAmount(obligations, "VAT");
    BigDecimal difference = calculatedVat.subtract(expectedVat);
    boolean hasGolden = hasObligation(obligations, "VAT");
    boolean expenseDocumentsMissing = expenses.isEmpty() && hasGolden;
    String status =
        !hasGolden
            ? "NO_GOLDEN"
            : expenseDocumentsMissing
                ? "EXPENSES_MISSING"
                : difference.signum() == 0 ? "MATCH" : "DIFF";

    return new VatCalculation(
        outputBeforeCorrection,
        julyOnlySalesCorrectionVat,
        outputVat,
        deductibleInputVat,
        BigDecimal.ZERO,
        calculatedVat,
        expectedVat,
        difference,
        status);
  }

  private BigDecimal taxInput(List<TaxInputRow> inputs, String inputType) {
    TaxInputRow input = taxInputOrNull(inputs, inputType);
    return input == null || input.amount() == null ? BigDecimal.ZERO : input.amount();
  }

  private TaxInputRow taxInputOrNull(List<TaxInputRow> inputs, String inputType) {
    return inputs.stream()
        .filter(input -> inputType.equals(input.inputType()))
        .findFirst()
        .orElse(null);
  }

  private boolean hasObligation(List<ObligationRow> obligations, String type) {
    return obligations.stream().anyMatch(obligation -> type.equals(obligation.obligationType()));
  }

  private BigDecimal obligationAmount(List<ObligationRow> obligations, String type) {
    return obligations.stream()
        .filter(obligation -> type.equals(obligation.obligationType()))
        .map(ObligationRow::expectedAmount)
        .findFirst()
        .orElse(BigDecimal.ZERO);
  }

  private List<ReconciliationRow> reconcile(
      List<InvoiceRow> invoices,
      List<ExpenseRow> expenses,
      List<BankRow> bankTransactions,
      List<ObligationRow> obligations) {
    List<ReconciliationRow> result = new ArrayList<>();
    Set<Long> usedBankTransactionIds = new HashSet<>();

    for (InvoiceRow invoice : invoices) {
      BankRow match =
          bankTransactions.stream()
              .filter(row -> "BUSINESS".equals(row.scope()))
              .filter(row -> "CUSTOMER_RECEIPT".equals(row.transactionType()))
              .filter(row -> invoice.currency().equals(row.currency()))
              .filter(row -> invoice.expectedReceivable().compareTo(row.amount()) == 0)
              .filter(
                  row ->
                      row.relatedPeriod() == null
                          || invoice.taxPeriod().equals(row.relatedPeriod()))
              .filter(
                  row ->
                      invoice.reference().equalsIgnoreCase(row.reference())
                          || invoice.customerAlias().equals(row.counterpartyAlias()))
              .filter(row -> usedBankTransactionIds.add(row.id()))
              .findFirst()
              .orElse(null);

      String explanation =
          invoice.correctionGrossAmount().signum() == 0
              ? "Exact expected receivable matched to a business customer receipt."
              : "Corrected receivable matched; the correction remains a July-specific historical fixture.";

      result.add(
          new ReconciliationRow(
              invoice.reference(),
              "INVOICE_PAYMENT",
              invoice.expectedReceivable(),
              invoice.currency(),
              match == null ? BigDecimal.ZERO : match.amount(),
              match == null ? null : match.bookingDate(),
              match == null ? "UNMATCHED" : "MATCHED",
              match == null ? "No exact business receipt found." : explanation));
    }

    for (ExpenseRow expense : expenses) {
      BankRow match =
          bankTransactions.stream()
              .filter(row -> "BUSINESS".equals(row.scope()))
              .filter(row -> "SUPPLIER_PAYMENT".equals(row.transactionType()))
              .filter(row -> expense.currency().equals(row.currency()))
              .filter(row -> expense.grossAmount().compareTo(row.amount().abs()) == 0)
              .filter(
                  row ->
                      row.relatedPeriod() == null
                          || expense.taxPeriod().equals(row.relatedPeriod()))
              .filter(
                  row ->
                      expense.reference().equalsIgnoreCase(row.reference())
                          || expense.supplierAlias().equals(row.counterpartyAlias()))
              .filter(row -> usedBankTransactionIds.add(row.id()))
              .findFirst()
              .orElse(null);
      result.add(
          new ReconciliationRow(
              expense.reference(),
              "EXPENSE_PAYMENT",
              expense.grossAmount(),
              expense.currency(),
              match == null ? BigDecimal.ZERO : match.amount().abs(),
              match == null ? null : match.bookingDate(),
              match == null ? "UNMATCHED" : "MATCHED",
              match == null
                  ? "No exact business supplier payment found."
                  : "Exact supplier payment matched."));
    }

    for (ObligationRow obligation : obligations) {
      List<BankRow> payments =
          bankTransactions.stream()
              .filter(row -> "BUSINESS".equals(row.scope()))
              .filter(
                  row ->
                      obligationTransactionType(obligation.obligationType())
                          .equals(row.transactionType()))
              .filter(
                  row ->
                      row.relatedPeriod() == null
                          || obligation.taxPeriod() == null
                          || obligation.taxPeriod().equals(row.relatedPeriod()))
              .toList();
      BigDecimal paidAmount =
          payments.stream().map(row -> row.amount().abs()).reduce(BigDecimal.ZERO, BigDecimal::add);
      LocalDate paymentDate =
          payments.stream().map(BankRow::bookingDate).max(LocalDate::compareTo).orElse(null);
      String status = obligation.status();
      String explanation = obligation.note();
      if (!"REPORTING_ONLY".equals(status)) {
        status =
            payments.isEmpty()
                ? "UNMATCHED"
                : obligation.expectedAmount().compareTo(paidAmount) == 0 ? "MATCHED" : "DIFF";
        if (!payments.isEmpty() && !"MATCHED".equals(status)) {
          BigDecimal cashDifference =
              paidAmount.subtract(obligation.expectedAmount()).setScale(2, RoundingMode.HALF_UP);
          explanation =
              obligation.note() + " Cash difference: " + cashDifference.toPlainString() + " PLN.";
        }
      }
      if ("REPORTING_ONLY".equals(status)) {
        paidAmount = BigDecimal.ZERO;
        paymentDate = null;
      }
      result.add(
          new ReconciliationRow(
              obligation.obligationType(),
              "OBLIGATION_PAYMENT",
              obligation.expectedAmount(),
              "PLN",
              paidAmount,
              paymentDate,
              status,
              explanation));
    }

    return List.copyOf(result);
  }

  private String obligationTransactionType(String obligationType) {
    return switch (obligationType) {
      case "RYCZALT" -> "RYCZALT_PAYMENT";
      case "VAT" -> "VAT_PAYMENT";
      case "ZUS" -> "ZUS_PAYMENT";
      default -> "__NO_ACCOUNTING_PAYMENT__";
    };
  }
}
