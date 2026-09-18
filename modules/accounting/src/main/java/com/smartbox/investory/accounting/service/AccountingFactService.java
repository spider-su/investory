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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

@Service
public class AccountingFactService {
  private static final BigDecimal HALF = new BigDecimal("0.50");
  private static final String REQUEST_YEAR_INVOICES_CACHE =
      AccountingFactService.class.getName() + ".yearInvoices";
  private static final String REQUEST_FACT_CACHE =
      AccountingFactService.class.getName() + ".facts";

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

  public List<LocalDate> availablePeriods(long profileId) {
    return pocRepository.availablePeriods(profileId);
  }

  public AccountingProfile accountingProfile(long profileId) {
    return requestCached(
        "profile:" + profileId,
        () -> {
          AccountingProfile profile = pocRepository.accountingProfile(profileId);
          return profile == null ? AccountingProfile.defaultProfile() : profile;
        });
  }

  public void updateHasUop(long profileId, boolean hasUop) {
    pocRepository.updateHasUop(profileId, hasUop);
  }

  public AccountingMonthSnapshot snapshot(long profileId, LocalDate period) {
    List<InvoiceRow> invoices = pocRepository.invoicesForPeriod(profileId, period);
    // Legacy rows keep a correction amount on the original invoice. Apply it to the
    // following tax period, without naming a specific month.
    List<InvoiceRow> correctionSources = correctionSourcesForPeriod(profileId, period);
    List<ExpenseRow> expenses = pocRepository.expensesForPeriod(profileId, period);
    List<BankRow> bankTransactions = pocRepository.bankTransactionsForPeriod(profileId, period);
    List<ObligationRow> obligations = pocRepository.obligationsForPeriod(profileId, period);
    List<TaxInputRow> taxInputs = pocRepository.taxInputsForPeriod(profileId, period);
    List<AccountingVatAdjustment> vatAdjustments =
        pocRepository.vatAdjustmentsForPeriod(profileId, period);
    AccountingProfile profile = accountingProfile(profileId);

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

    List<AccountingTaxProfilePeriod> taxProfilePeriods = taxProfilePeriods(profileId);
    AccountingCalculationMode calculationMode =
        period.getYear() != 2025
                && taxProfilePeriods.stream()
                    .anyMatch(profilePeriod -> profilePeriod.activeOn(period))
            ? AccountingCalculationMode.CURRENT_CALCULATION
            : AccountingCalculationMode.HISTORICAL_RECONSTRUCTION;
    Map<LocalDate, List<InvoiceRow>> yearInvoices =
        calculationMode == AccountingCalculationMode.HISTORICAL_RECONSTRUCTION
            ? invoicesForYear(profileId, period)
            : Map.of();
    FxCalculation fx = calculateFx(invoices, foreignBookedRevenue, foreignSourceEur);
    if (calculationMode == AccountingCalculationMode.HISTORICAL_RECONSTRUCTION) {
      foreignBookedRevenue = reconstructedForeignRevenue(invoices);
    }
    RyczaltCalculation ryczalt =
        calculateRyczalt(
            period,
            calculationMode,
            invoices,
            correctionSources,
            domesticRevenue,
            foreignBookedRevenue,
            fx,
            obligations,
            taxInputs);
    VatCalculation vat =
        calculateVat(period, invoices, correctionSources, expenses, obligations, vatAdjustments);
    var activityPeriods = businessActivityPeriods(profileId);
    var employmentPeriods = employmentPeriods(profileId);
    var resolved =
        profileResolver.resolve(period, activityPeriods, employmentPeriods, taxProfilePeriods);
    var vatTransactions = pocRepository.vatTransactionsForPeriod(profileId, period);
    var yearToDate =
        new AccountingYearToDateContext(
            calculationMode == AccountingCalculationMode.HISTORICAL_RECONSTRUCTION
                ? historicalYearToDateRevenue(profileId, period, yearInvoices)
                : yearToDateRevenue(profileId, period),
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
    boolean uses2025ZusRules = period.getYear() == 2025;
    ZusCalculator.Input zusInput =
        new ZusCalculator.Input(
            resolved.jdgActive(),
            resolved.qualifyingUop(),
            resolved.zusRegime(),
            resolved.voluntarySickness(),
            yearToDate.taxableRyczaltRevenue(),
            uses2025ZusRules ? ZusRules2025.FULL_JDG_SOCIAL : ZusRules2026.FULL_JDG_SOCIAL,
            uses2025ZusRules
                    || calculationMode == AccountingCalculationMode.HISTORICAL_RECONSTRUCTION
                ? ZusRules2026.HealthBand.HIGH
                : null);
    var zusCalculation =
        useCalculatedZus
            ? uses2025ZusRules
                ? new ZusCalculator()
                    .calculate(
                        zusInput,
                        ZusRules2025.LABOUR_FUND,
                        ZusRules2025.VOLUNTARY_SICKNESS,
                        ZusRules2025.HEALTH_HIGH,
                        ZusRules2025.VERSION)
                : new ZusCalculator().calculate(zusInput)
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
        && !uses2025ZusRules
        && zusCalculation != null) {
      // The health contribution band for a calendar year is based on the prior
      // calendar year's completed revenue, not current-year YTD revenue or paid social.
      LocalDate priorYearEnd = LocalDate.of(period.getYear() - 1, 12, 1);
      BigDecimal priorYearRevenue =
          java.util.Objects.requireNonNullElse(
              yearToDateRevenue(profileId, priorYearEnd), BigDecimal.ZERO);
      if (priorYearRevenue.signum() == 0) {
        priorYearRevenue =
            java.util.Objects.requireNonNullElse(
                pocRepository.oldestAvailableYearRevenue(profileId), BigDecimal.ZERO);
      }
      var healthBand = ZusRules2026.healthBand(priorYearRevenue);
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
                    correctionSources.stream()
                        .map(InvoiceRow::correctionNetAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                    correctionSources.stream()
                        .map(InvoiceRow::correctionVatAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)),
                context,
                vatTransactions,
                calculationMode,
                vatAdjustments));
    // Historical ryczałt must apply the paid-health deduction. Prefer the recorded wFirma input;
    // use calculated ZUS only when the historical input row is absent.
    if (calculationMode == AccountingCalculationMode.HISTORICAL_RECONSTRUCTION) {
      ryczalt =
          calculatedHistoricalRyczalt(
              profileId,
              period,
              calculated,
              ryczalt,
              zus.healthZus(),
              taxInputs,
              yearInvoices);
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
    List<ReconciliationRow> reconciliations = reconcile(invoices, bankTransactions, obligations);
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
            obligations,
            profileId);
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
    return pocRepository.paidContributionsUpTo(profileId, period, obligations);
  }

  private Map<LocalDate, AccountingPocRepository.ZusAmounts> paidContributionObligations(
      long profileId,
      LocalDate period,
      AccountingProfileResolver.ResolvedProfile resolved,
      ZusCalculator.ZusCalculation currentZus) {
    var activityPeriods = businessActivityPeriods(profileId);
    var employmentPeriods = employmentPeriods(profileId);
    var taxPeriods = taxProfilePeriods(profileId);
    var obligations = new java.util.LinkedHashMap<LocalDate, AccountingPocRepository.ZusAmounts>();
    for (LocalDate contributionPeriod : pocRepository.zusPaymentPeriodsUpTo(profileId, period)) {
      var effective =
          profileResolver.resolve(
              contributionPeriod, activityPeriods, employmentPeriods, taxPeriods);
      if (effective.zusRegime() == null) continue;
      boolean rules2025 = contributionPeriod.getYear() == 2025;
      var input =
          new ZusCalculator.Input(
              effective.jdgActive(),
              effective.qualifyingUop(),
              effective.zusRegime(),
              effective.voluntarySickness(),
              yearToDateRevenue(profileId, contributionPeriod),
              rules2025 ? ZusRules2025.FULL_JDG_SOCIAL : ZusRules2026.FULL_JDG_SOCIAL,
              ZusRules2026.HealthBand.HIGH);
      var calculated =
          rules2025
              ? new ZusCalculator()
                  .calculate(
                      input,
                      ZusRules2025.LABOUR_FUND,
                      ZusRules2025.VOLUNTARY_SICKNESS,
                      ZusRules2025.HEALTH_HIGH,
                      ZusRules2025.VERSION)
              : new ZusCalculator().calculate(input);
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
        result.ryczalt().socialContributionDeduction(),
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
      long profileId,
      LocalDate period,
      AccountingCalculationResult result,
      RyczaltCalculation comparison,
      BigDecimal calculatedHealthContribution,
      List<TaxInputRow> taxInputs,
      Map<LocalDate, List<InvoiceRow>> yearInvoices) {
    BigDecimal health = taxInput(taxInputs, "HEALTH_CONTRIBUTION_PAID");
    if (health.signum() == 0) health = calculatedHealthContribution;
    BigDecimal healthDeduction = health.multiply(HALF).setScale(2, RoundingMode.HALF_UP);
    BigDecimal socialDeduction = taxInput(taxInputs, "SOCIAL_CONTRIBUTION_PAID");
    BigDecimal monthlyTaxableBase =
        comparison
            .revenueBeforeDeductions()
            .subtract(healthDeduction)
            .subtract(socialDeduction)
            .max(BigDecimal.ZERO)
            .setScale(0, RoundingMode.HALF_UP);
    List<TaxInputRow> cumulativeInputs =
        java.util.Objects.requireNonNullElse(
            pocRepository.taxInputsUpTo(profileId, period), List.of());
    BigDecimal cumulativeRevenue =
        java.util.Objects.requireNonNullElse(
                historicalYearToDateRevenue(profileId, period, yearInvoices), BigDecimal.ZERO)
            .add(comparison.julyOnlyCorrectionNetAdjustment());
    BigDecimal cumulativeTax =
        cumulativeTax(
            cumulativeRevenue, cumulativeInputs, comparison.rate(), calculatedHealthContribution);
    BigDecimal previousTax = BigDecimal.ZERO;
    if (!period.equals(period.withDayOfYear(1))) {
      LocalDate previousPeriod = period.minusMonths(1);
      previousTax =
          cumulativeTax(
              java.util.Objects.requireNonNullElse(
                  historicalYearToDateRevenue(profileId, previousPeriod, yearInvoices),
                  BigDecimal.ZERO),
              java.util.Objects.requireNonNullElse(
                  pocRepository.taxInputsUpTo(profileId, previousPeriod), List.of()),
              comparison.rate(),
              BigDecimal.ZERO);
    }
    BigDecimal calculatedTax = cumulativeTax.subtract(previousTax);
    BigDecimal difference = calculatedTax.subtract(comparison.expectedTax());
    return new RyczaltCalculation(
        comparison.revenueBeforeDeductions(),
        socialDeduction,
        comparison.julyOnlyCorrectionNetAdjustment(),
        health,
        healthDeduction,
        monthlyTaxableBase,
        comparison.rate(),
        calculatedTax,
        comparison.expectedTax(),
        difference,
        comparison.status());
  }

  private BigDecimal cumulativeTax(
      BigDecimal revenue,
      List<TaxInputRow> inputs,
      BigDecimal rate,
      BigDecimal fallbackHealthContribution) {
    BigDecimal health = taxInputTotal(inputs, "HEALTH_CONTRIBUTION_PAID");
    if (health.signum() == 0) health = fallbackHealthContribution;
    BigDecimal social = taxInputTotal(inputs, "SOCIAL_CONTRIBUTION_PAID");
    BigDecimal base =
        revenue
            .subtract(health.multiply(HALF).setScale(2, RoundingMode.HALF_UP))
            .subtract(social)
            .max(BigDecimal.ZERO)
            .setScale(0, RoundingMode.HALF_UP);
    return base.multiply(rate).setScale(0, RoundingMode.HALF_UP);
  }

  private List<AccountingTaxProfilePeriod> taxProfilePeriods(long profileId) {
    return requestCached(
        "tax-profile-periods:" + profileId,
        () -> pocRepository.taxProfilePeriods(profileId));
  }

  private List<BusinessActivityPeriod> businessActivityPeriods(long profileId) {
    return requestCached(
        "business-activity-periods:" + profileId,
        () -> pocRepository.businessActivityPeriods(profileId));
  }

  private List<EmploymentInsurancePeriod> employmentPeriods(long profileId) {
    return requestCached(
        "employment-periods:" + profileId,
        () -> pocRepository.employmentPeriods(profileId));
  }

  private BigDecimal yearToDateRevenue(long profileId, LocalDate period) {
    return requestCached(
        "ytd-revenue:" + profileId + ":" + period,
        () -> pocRepository.yearToDateRevenue(profileId, period));
  }

  @SuppressWarnings("unchecked")
  private <T> T requestCached(String key, Supplier<T> loader) {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes == null) return loader.get();
    Map<String, Object> cache =
        (Map<String, Object>) attributes.getAttribute(REQUEST_FACT_CACHE, RequestAttributes.SCOPE_REQUEST);
    if (cache == null) {
      cache = new java.util.HashMap<>();
      attributes.setAttribute(REQUEST_FACT_CACHE, cache, RequestAttributes.SCOPE_REQUEST);
    }
    return (T) cache.computeIfAbsent(key, ignored -> loader.get());
  }

  /**
   * Rebuild historical year-to-date revenue from normalized invoices when FX was acquired during
   * the current import flow. The legacy aggregate falls back to the source net amount for a foreign
   * invoice whose booked PLN amount is not persisted by staging, which undercounts the historical
   * ryczałt base after promotion.
   */
  private BigDecimal historicalYearToDateRevenue(
      long profileId, LocalDate period, List<InvoiceRow> invoices) {
    return historicalYearToDateRevenue(
        profileId, period, invoices, invoicesByMonth(profileId, period, invoices));
  }

  private BigDecimal historicalYearToDateRevenue(
      long profileId, LocalDate period, Map<LocalDate, List<InvoiceRow>> yearInvoices) {
    return historicalYearToDateRevenue(
        profileId,
        period,
        yearInvoices.getOrDefault(period, List.of()),
        yearInvoices);
  }

  private BigDecimal historicalYearToDateRevenue(
      long profileId,
      LocalDate period,
      List<InvoiceRow> invoices,
      Map<LocalDate, List<InvoiceRow>> yearInvoices) {
    BigDecimal raw =
        java.util.Objects.requireNonNullElse(
            yearToDateRevenue(profileId, period), BigDecimal.ZERO);
    if (invoices.isEmpty()) return raw;

    BigDecimal reconstructed = BigDecimal.ZERO;
    boolean requiresFxReconstruction = false;
    for (LocalDate month = period.withDayOfYear(1);
        !month.isAfter(period);
        month = month.plusMonths(1)) {
      List<InvoiceRow> monthInvoices =
          month.equals(period) ? invoices : yearInvoices.getOrDefault(month, List.of());
      requiresFxReconstruction |=
          monthInvoices.stream()
              .anyMatch(
                  invoice -> !"PLN".equals(invoice.currency()) && invoice.bookedNetPln() == null);
      reconstructed = reconstructed.add(historicalMonthRevenue(monthInvoices));
    }
    return (requiresFxReconstruction ? reconstructed : raw).setScale(2, RoundingMode.HALF_UP);
  }

  private Map<LocalDate, List<InvoiceRow>> invoicesForYear(long profileId, LocalDate period) {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes == null) {
      return invoicesByMonth(profileId, period, pocRepository.invoicesForYear(profileId, period));
    }
    @SuppressWarnings("unchecked")
    Map<String, Map<LocalDate, List<InvoiceRow>>> cache =
        (Map<String, Map<LocalDate, List<InvoiceRow>>>)
            attributes.getAttribute(REQUEST_YEAR_INVOICES_CACHE, RequestAttributes.SCOPE_REQUEST);
    if (cache == null) {
      cache = new java.util.HashMap<>();
      attributes.setAttribute(REQUEST_YEAR_INVOICES_CACHE, cache, RequestAttributes.SCOPE_REQUEST);
    }
    String key = profileId + ":" + period.getYear();
    return cache.computeIfAbsent(
        key, ignored -> invoicesByMonth(profileId, period, pocRepository.invoicesForYear(profileId, period)));
  }

  private Map<LocalDate, List<InvoiceRow>> invoicesByMonth(
      long profileId, LocalDate period, List<InvoiceRow> invoices) {
    Map<LocalDate, List<InvoiceRow>> byMonth = new java.util.HashMap<>();
    for (InvoiceRow invoice : invoices) {
      byMonth.computeIfAbsent(invoice.taxPeriod(), ignored -> new ArrayList<>()).add(invoice);
    }
    return byMonth;
  }

  private BigDecimal historicalMonthRevenue(List<InvoiceRow> invoices) {
    BigDecimal reconstructedMonth =
        invoices.stream()
            .filter(invoice -> invoice.netAmount() != null)
            .map(
                invoice ->
                    "PLN".equals(invoice.currency()) && invoice.bookedNetPln() != null
                        ? invoice.bookedNetPln()
                        : "PLN".equals(invoice.currency())
                            ? invoice.netAmount()
                            : invoice.bookedNetPln() != null
                                ? invoice.bookedNetPln()
                                : reconstructedForeignRevenue(List.of(invoice)))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return reconstructedMonth.setScale(2, RoundingMode.HALF_UP);
  }

  private VatCalculation currentVat(AccountingCalculationResult result) {
    return new VatCalculation(
        result.vat().outputVatBeforeCorrection(),
        result.vat().salesCorrectionVat(),
        result.vat().outputVat(),
        result.vat().deductibleInputVat(),
        BigDecimal.ZERO,
        result.vat().explicitVatAdjustments(),
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
      List<ObligationRow> obligations,
      long profileId) {
    List<AccountingIssue> issues = new ArrayList<>();
    List<AccountingIssue> sourceIssues = pocRepository.sourceIssuesForPeriod(profileId, period);
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
                : zusDifference.signum() == 0 ? "MATCH" : "DIFF";
    String zusComparisonNote =
        !hasZusGolden
            ? "No captured historical ZUS obligation is available for this month."
            : "Captured ZUS obligation is comparison evidence; calculation uses the effective-dated UoP profile and year-specific contribution rules.";

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

  private List<InvoiceRow> correctionSourcesForPeriod(long profileId, LocalDate period) {
    LocalDate sourcePeriod = period.minusMonths(1);
    List<InvoiceRow> candidates = pocRepository.invoicesForPeriod(profileId, sourcePeriod);
    return candidates.stream()
        .filter(
            invoice ->
                nonZero(invoice.correctionNetAmount())
                    || nonZero(invoice.correctionVatAmount())
                    || nonZero(invoice.correctionGrossAmount()))
        .toList();
  }

  private boolean nonZero(BigDecimal value) {
    return value != null && value.signum() != 0;
  }

  private RyczaltCalculation calculateRyczalt(
      LocalDate period,
      AccountingCalculationMode calculationMode,
      List<InvoiceRow> invoices,
      List<InvoiceRow> correctionSources,
      BigDecimal domesticRevenue,
      BigDecimal foreignRevenuePln,
      FxCalculation fx,
      List<ObligationRow> obligations,
      List<TaxInputRow> taxInputs) {
    BigDecimal correctionNet =
        correctionSources.stream()
            .map(InvoiceRow::correctionNetAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal foreignRevenueForTax =
        calculationMode == AccountingCalculationMode.HISTORICAL_RECONSTRUCTION
            ? foreignRevenuePln
            : fx.calculatedPln();
    BigDecimal revenueBeforeDeductions =
        domesticRevenue.add(foreignRevenueForTax).add(correctionNet);

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
        BigDecimal.ZERO,
        correctionNet,
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
      List<InvoiceRow> correctionSources,
      List<ExpenseRow> expenses,
      List<ObligationRow> obligations,
      List<AccountingVatAdjustment> vatAdjustments) {
    BigDecimal outputBeforeCorrection =
        invoices.stream()
            .filter(invoice -> "PLN".equals(invoice.currency()))
            .map(InvoiceRow::vatAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal correctionVat =
        correctionSources.stream()
            .map(InvoiceRow::correctionVatAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal outputVat = outputBeforeCorrection.add(correctionVat);

    BigDecimal deductibleInputVat =
        expenses.stream()
            .map(ExpenseRow::deductibleVat)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    BigDecimal explicitVatAdjustments =
        vatAdjustments.stream()
            .filter(adjustment -> period.equals(adjustment.taxPeriod()))
            .map(AccountingVatAdjustment::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);

    BigDecimal calculatedVat =
        outputVat
            .setScale(0, RoundingMode.HALF_UP)
            .add(explicitVatAdjustments.setScale(0, RoundingMode.HALF_UP))
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
        correctionVat,
        outputVat,
        deductibleInputVat,
        BigDecimal.ZERO,
        explicitVatAdjustments,
        calculatedVat,
        expectedVat,
        difference,
        status);
  }

  private BigDecimal taxInput(List<TaxInputRow> inputs, String inputType) {
    TaxInputRow input = taxInputOrNull(inputs, inputType);
    return input == null || input.amount() == null ? BigDecimal.ZERO : input.amount();
  }

  private BigDecimal taxInputTotal(List<TaxInputRow> inputs, String inputType) {
    return inputs.stream()
        .filter(input -> inputType.equals(input.inputType()))
        .map(TaxInputRow::amount)
        .filter(java.util.Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private boolean sameWholeUnit(BigDecimal left, BigDecimal right) {
    return wholeUnit(left).compareTo(wholeUnit(right)) == 0;
  }

  private BigDecimal wholeUnit(BigDecimal value) {
    return value.setScale(0, RoundingMode.HALF_UP);
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

  private boolean isBusinessBankRow(BankRow row) {
    if (!"BUSINESS".equals(row.scope())
        && !("REVIEW_REQUIRED".equals(row.scope()) && isKnownCustomerCounterparty(row)))
      return false;
    if ("RENTAL_TAX_PAYMENT".equals(row.transactionType())) return false;
    String text =
        (row.reference() + " " + row.counterpartyAlias() + " " + row.note()).toUpperCase();
    return !containsAny(text, "PERSONAL", "PRIVATE", "RENTAL TAX")
        && !(text.contains("PPE") && text.contains("RENTAL"));
  }

  private boolean isCustomerReceiptCandidate(BankRow row) {
    if ("CUSTOMER_RECEIPT".equals(row.transactionType())) return row.amount().signum() > 0;
    if (row.amount().signum() <= 0 || !"REVIEW_REQUIRED".equals(row.scope())) return false;
    return isKnownCustomerCounterparty(row);
  }

  private boolean isKnownCustomerCounterparty(BankRow row) {
    String text = normalized(row.counterpartyAlias() + " " + row.note());
    return text.contains("EMAGINE")
        || text.contains("PLATFORM")
        || text.contains("RITS")
        || text.contains("FAKTORING");
  }

  private boolean exactReceipt(InvoiceRow invoice, BankRow row) {
    return invoice.expectedReceivable().compareTo(row.amount()) == 0
        && (invoice.reference().equalsIgnoreCase(row.reference())
            || aliasesMatch(invoice.customerAlias(), row.counterpartyAlias())
            || "CUSTOMER_RECEIPT".equals(row.transactionType()));
  }

  private boolean explicitInvoicePayment(InvoiceRow invoice, BankRow row) {
    if (row.amount().signum() <= 0) return false;
    String invoiceReference = normalized(invoice.reference());
    if (invoiceReference.isBlank()) return false;
    String bankText =
        normalized(row.reference() + " " + row.counterpartyAlias() + " " + row.note());
    return bankText.contains(invoiceReference);
  }

  private boolean crossPeriodExactReceipt(InvoiceRow invoice, BankRow row) {
    return "CUSTOMER_RECEIPT".equals(row.transactionType())
        && exactReceipt(invoice, row)
        && row.bookingDate() != null
        && invoice.saleDate() != null
        && !row.bookingDate().isBefore(invoice.saleDate());
  }

  private boolean approximateReceipt(InvoiceRow invoice, BankRow row) {
    if (row.amount().signum() <= 0
        || row.bookingDate() == null
        || invoice.saleDate() == null
        || !sameCounterparty(invoice, row)) return false;
    long days =
        Math.abs(java.time.temporal.ChronoUnit.DAYS.between(invoice.saleDate(), row.bookingDate()));
    if (days > 60) return false;
    BigDecimal tolerance = invoice.expectedReceivable().abs().multiply(new BigDecimal("0.03"));
    tolerance = tolerance.max(new BigDecimal("100.00"));
    return invoice.expectedReceivable().subtract(row.amount()).abs().compareTo(tolerance) <= 0;
  }

  private boolean sameCounterparty(InvoiceRow invoice, BankRow row) {
    String invoiceParty = normalized(invoice.customerAlias());
    String rowParty = normalized(row.counterpartyAlias());
    String bankParty = normalized(row.counterpartyAlias() + " " + row.note());
    return !invoiceParty.isBlank()
        && (aliasesMatch(invoice.customerAlias(), row.counterpartyAlias())
            || bankParty.contains(invoiceParty)
            || (!rowParty.isBlank() && invoiceParty.contains(rowParty)));
  }

  private boolean factoringReceipt(InvoiceRow invoice, BankRow row) {
    boolean sameRitsCustomer =
        normalized(invoice.customerAlias()).contains("RITS") && isFactoringCounterparty(row);
    if (!sameRitsCustomer) return false;
    BigDecimal fee = invoice.expectedReceivable().subtract(row.amount());
    return fee.signum() > 0 && fee.compareTo(new BigDecimal("1000.00")) <= 0;
  }

  private int receiptMatchRank(InvoiceRow invoice, BankRow row) {
    if (exactReceipt(invoice, row) && invoice.reference().equalsIgnoreCase(row.reference()))
      return 0;
    if (exactReceipt(invoice, row) && "CUSTOMER_RECEIPT".equals(row.transactionType())) return 1;
    if (exactReceipt(invoice, row)) return 2;
    return 3;
  }

  private boolean isFactoringCounterparty(BankRow row) {
    String text = normalized(row.counterpartyAlias() + " " + row.note());
    return text.contains("FAKTORING") || text.contains("RITS");
  }

  private String normalized(String value) {
    return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
  }

  private boolean containsAny(String text, String... values) {
    for (String value : values) if (text.contains(value)) return true;
    return false;
  }

  private List<ReconciliationRow> reconcile(
      List<InvoiceRow> invoices, List<BankRow> bankTransactions, List<ObligationRow> obligations) {
    List<ReconciliationRow> result = new ArrayList<>();
    Set<Long> usedBankTransactionIds = new HashSet<>();

    for (InvoiceRow invoice : invoices) {
      BankRow match =
          bankTransactions.stream()
              .filter(
                  row ->
                      isCustomerReceiptCandidate(row)
                          || explicitInvoicePayment(invoice, row)
                          || approximateReceipt(invoice, row))
              .filter(
                  row ->
                      isBusinessBankRow(row)
                          || explicitInvoicePayment(invoice, row)
                          || approximateReceipt(invoice, row))
              .filter(row -> invoice.currency().equals(row.currency()))
              .filter(
                  row ->
                      row.relatedPeriod() == null
                          || invoice.taxPeriod().equals(row.relatedPeriod())
                          || explicitInvoicePayment(invoice, row)
                          || crossPeriodExactReceipt(invoice, row)
                          || approximateReceipt(invoice, row))
              .filter(
                  row ->
                      explicitInvoicePayment(invoice, row)
                          || exactReceipt(invoice, row)
                          || factoringReceipt(invoice, row)
                          || approximateReceipt(invoice, row))
              .sorted(
                  Comparator.comparingInt((BankRow row) -> receiptMatchRank(invoice, row))
                      .thenComparing(
                          BankRow::bookingDate, Comparator.nullsLast(Comparator.naturalOrder()))
                      .thenComparingLong(BankRow::id))
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
              match == null
                  ? "No business receipt matched."
                  : factoringReceipt(invoice, match)
                      ? "Customer receipt matched net of documented factoring deduction."
                      : explanation));
    }

    for (ObligationRow obligation : obligations) {
      List<BankRow> payments =
          bankTransactions.stream()
              .filter(this::isBusinessBankRow)
              .filter(
                  row ->
                      obligationTransactionType(obligation.obligationType())
                          .equals(row.transactionType()))
              .filter(row -> isValidObligationPayment(obligation, row))
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
                : sameWholeUnit(obligation.expectedAmount(), paidAmount) ? "MATCHED" : "DIFF";
        if (!payments.isEmpty() && !"MATCHED".equals(status)) {
          BigDecimal cashDifference =
              wholeUnit(paidAmount).subtract(wholeUnit(obligation.expectedAmount()));
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

  private boolean isValidObligationPayment(ObligationRow obligation, BankRow row) {
    if (!"VAT".equals(obligation.obligationType())) return true;
    return row.amount().signum() < 0
        && row.counterpartyAlias() != null
        && containsAny(row.counterpartyAlias(), "URZĄD SKARBOWY", "URZAD SKARBOWY");
  }

  private String obligationTransactionType(String obligationType) {
    return switch (obligationType) {
      case "RYCZALT" -> "RYCZALT_PAYMENT";
      case "VAT" -> "VAT_PAYMENT";
      case "ZUS" -> "ZUS_PAYMENT";
      default -> "__NO_ACCOUNTING_PAYMENT__";
    };
  }

  private boolean aliasesMatch(String left, String right) {
    if (left == null || right == null) return false;
    String normalizedLeft = left.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    String normalizedRight = right.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    return !normalizedLeft.isBlank()
        && !normalizedRight.isBlank()
        && (normalizedLeft.equals(normalizedRight)
            || normalizedLeft.contains(normalizedRight)
            || normalizedRight.contains(normalizedLeft));
  }
}
