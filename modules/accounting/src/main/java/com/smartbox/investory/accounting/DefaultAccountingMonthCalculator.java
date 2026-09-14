package com.smartbox.investory.accounting;

import com.smartbox.investory.accounting.AccountingCalculationResult.CalculatedObligation;
import com.smartbox.investory.accounting.AccountingCalculationResult.FxCalculation.Conversion;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.ExpenseRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.InvoiceRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.TaxInputRow;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyConversionUnavailableException;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Deterministic calculation engine. This class never loads repositories or compares goldens. */
@Service
public class DefaultAccountingMonthCalculator implements AccountingMonthCalculator {
  private static final BigDecimal HALF = new BigDecimal("0.50");
  private final CurrencyConversion currencyConversion;

  public DefaultAccountingMonthCalculator(CurrencyConversion currencyConversion) {
    this.currencyConversion = currencyConversion;
  }

  @Override
  public AccountingCalculationResult calculate(AccountingCalculationInput input) {
    List<AccountingIssue> issues = new ArrayList<>();
    validateVatInputs(input, issues);
    AccountingCalculationResult.FxCalculation fx = calculateFx(input, issues);
    BigDecimal domestic =
        input.invoices().stream()
            .filter(i -> "PLN".equals(i.currency()))
            .map(InvoiceRow::netAmount)
            .filter(v -> v != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal revenue =
        domestic.add(fx.convertedRevenuePln()).add(input.adjustments().revenueNetPln());
    var paidContributions = input.periodContext().yearToDate().paidContributions();
    var periodEnd = input.period().withDayOfMonth(input.period().lengthOfMonth());
    var periodStart = input.period().withDayOfMonth(1);
    BigDecimal paidSocial =
        paidContributions.stream()
            .filter(p -> "SOCIAL".equals(p.contributionType()))
            .filter(
                p -> !p.paymentDate().isBefore(periodStart) && !p.paymentDate().isAfter(periodEnd))
            .map(PaidContribution::deductibleAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    ZusCalculationInput zusInput = input.periodContext().zusCalculationInput();
    BigDecimal health =
        input.calculationMode() == AccountingCalculationMode.CURRENT_CALCULATION
            ? zusInput != null && zusInput.healthAmount() != null
                ? zusInput.healthAmount()
                : missingCurrentZusAmount("health", issues)
            : input.periodContext().jdgActive()
                    && zusInput != null
                    && zusInput.healthAmount() != null
                ? zusInput.healthAmount()
                : !input.periodContext().jdgActive()
                    ? BigDecimal.ZERO
                    : paidContributions.stream()
                        .filter(p -> "HEALTH".equals(p.contributionType()))
                        .filter(
                            p ->
                                !p.paymentDate().isBefore(periodStart)
                                    && !p.paymentDate().isAfter(periodEnd))
                        .map(PaidContribution::deductibleAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (input.calculationMode() != AccountingCalculationMode.CURRENT_CALCULATION
        && health.signum() == 0) {
      health = required(input.taxInputs(), "HEALTH_CONTRIBUTION_PAID", issues);
    }
    BigDecimal socialDeduction = paidSocial;
    BigDecimal healthDeduction =
        paidContributions.stream()
                    .filter(p -> "HEALTH".equals(p.contributionType()))
                    .filter(
                        p ->
                            !p.paymentDate().isBefore(periodStart)
                                && !p.paymentDate().isAfter(periodEnd))
                    .map(PaidContribution::deductibleAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .signum()
                == 0
            ? BigDecimal.ZERO
            : paidContributions.stream()
                .filter(p -> "HEALTH".equals(p.contributionType()))
                .filter(
                    p ->
                        !p.paymentDate().isBefore(periodStart)
                            && !p.paymentDate().isAfter(periodEnd))
                .map(PaidContribution::deductibleAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .multiply(HALF)
                .setScale(2, RoundingMode.HALF_UP);
    BigDecimal totalDeductions = socialDeduction.add(healthDeduction);
    Map<BigDecimal, BigDecimal> buckets = new LinkedHashMap<>();
    BigDecimal effectiveRate = input.periodContext().ryczaltRate();
    if (input.calculationMode() == AccountingCalculationMode.CURRENT_CALCULATION
        && (input.periodContext().zusRegime() == null || effectiveRate == null)) {
      issues.add(
          issue("MISSING_EFFECTIVE_TAX_PROFILE", null, "No tax profile is active for the period."));
    }
    for (InvoiceRow invoice : input.invoices()) {
      if (invoice.ryczaltRate() == null) {
        issues.add(
            issue("UNSUPPORTED_RYCZALT_RATE", invoice.reference(), "Ryczalt rate is missing."));
      } else if (invoice.netAmount() != null) {
        BigDecimal amount =
            "PLN".equals(invoice.currency())
                ? invoice.netAmount()
                : fx.entries().stream()
                    .filter(e -> e.reference().equals(invoice.reference()))
                    .map(Conversion::convertedPln)
                    .findFirst()
                    .orElse(null);
        if (amount != null) buckets.merge(invoice.ryczaltRate(), amount, BigDecimal::add);
      }
    }
    if (input.adjustments().revenueNetPln().signum() != 0) {
      buckets.merge(new BigDecimal("0.12"), input.adjustments().revenueNetPln(), BigDecimal::add);
    }
    BigDecimal taxable = revenue.subtract(totalDeductions).setScale(0, RoundingMode.HALF_UP);
    Map<BigDecimal, BigDecimal> taxableByRate = allocateDeductions(buckets, totalDeductions);
    BigDecimal tax =
        taxableByRate.entrySet().stream()
            .map(
                entry ->
                    entry
                        .getValue()
                        .setScale(0, RoundingMode.HALF_UP)
                        .multiply(entry.getKey())
                        .setScale(0, RoundingMode.HALF_UP))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal outputVat;
    BigDecimal deductible;
    if (input.calculationMode() == AccountingCalculationMode.CURRENT_CALCULATION) {
      var requiredVatReferences = new HashSet<String>();
      input.invoices().stream()
          .map(InvoiceRow::reference)
          .filter(reference -> reference != null && !reference.isBlank())
          .forEach(requiredVatReferences::add);
      input.expenses().stream()
          .map(ExpenseRow::reference)
          .filter(reference -> reference != null && !reference.isBlank())
          .forEach(requiredVatReferences::add);
      var classifiedReferences = new HashSet<String>();
      var duplicateReferences = new HashSet<String>();
      input.vatTransactions().stream()
          .map(AccountingVatTransaction::reference)
          .filter(reference -> reference != null && !reference.isBlank())
          .forEach(
              reference -> {
                if (!classifiedReferences.add(reference)) duplicateReferences.add(reference);
              });
      requiredVatReferences.removeAll(classifiedReferences);
      requiredVatReferences.forEach(
          reference ->
              issues.add(
                  issue(
                      "MISSING_VAT_CLASSIFICATION",
                      reference,
                      "Current calculation requires an explicit VAT treatment for this document.")));
      duplicateReferences.forEach(
          reference ->
              issues.add(
                  issue(
                      "DUPLICATE_VAT_CLASSIFICATION",
                      reference,
                      "Only one VAT treatment may be recorded for a document.")));
    }
    if (input.vatTransactions().isEmpty()
        && input.calculationMode() == AccountingCalculationMode.CURRENT_CALCULATION
        && (!input.invoices().isEmpty() || !input.expenses().isEmpty())) {
      issues.add(
          issue(
              "MISSING_VAT_CLASSIFICATION",
              null,
              "Current calculation requires explicit VAT transaction treatment."));
      outputVat = BigDecimal.ZERO;
      deductible = BigDecimal.ZERO;
    } else if (input.vatTransactions().isEmpty()) {
      outputVat =
          input.invoices().stream()
              .filter(i -> "PLN".equals(i.currency()))
              .map(InvoiceRow::vatAmount)
              .filter(v -> v != null)
              .reduce(BigDecimal.ZERO, BigDecimal::add)
              .add(input.adjustments().salesVat());
      deductible =
          input.expenses().stream()
              .map(ExpenseRow::deductibleVat)
              .filter(v -> v != null)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
    } else if (input.calculationMode() == AccountingCalculationMode.CURRENT_CALCULATION
        && issues.stream()
            .anyMatch(
                issue ->
                    issue.type().equals("MISSING_VAT_CLASSIFICATION")
                        || issue.type().equals("DUPLICATE_VAT_CLASSIFICATION"))) {
      outputVat = BigDecimal.ZERO;
      deductible = BigDecimal.ZERO;
    } else {
      AccountingVatClassifier vatClassifier = new AccountingVatClassifier();
      input
          .vatTransactions()
          .forEach(
              t ->
                  vatClassifier
                      .issues(t)
                      .forEach(
                          message ->
                              issues.add(
                                  issue(
                                      "VAT_CLASSIFICATION",
                                      t == null ? null : t.reference(),
                                      message))));
      outputVat =
          input.vatTransactions().stream()
              .filter(
                  t ->
                      t.direction() == AccountingVatTransaction.Direction.SALE
                              && t.treatment() == VatTreatment.DOMESTIC_VAT
                          || t.direction() == AccountingVatTransaction.Direction.PURCHASE
                              && (t.treatment() == VatTreatment.IMPORT_OF_SERVICES_EU
                                  || t.treatment() == VatTreatment.IMPORT_OF_SERVICES_NON_EU))
              .map(AccountingVatTransaction::vatAmount)
              .filter(v -> v != null)
              .reduce(BigDecimal.ZERO, BigDecimal::add)
              .add(input.adjustments().salesVat());
      deductible =
          input.vatTransactions().stream()
              .filter(t -> t.direction() == AccountingVatTransaction.Direction.PURCHASE)
              .map(AccountingVatTransaction::deductibleVat)
              .filter(v -> v != null)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    deductible = deductible.setScale(2, RoundingMode.HALF_UP);
    BigDecimal calculatedVat =
        outputVat
            .setScale(0, RoundingMode.HALF_UP)
            .subtract(deductible.setScale(0, RoundingMode.HALF_UP));
    boolean qualifyingUop = input.periodContext().qualifyingUop();
    BigDecimal social =
        input.calculationMode() == AccountingCalculationMode.CURRENT_CALCULATION
            ? zusInput != null && zusInput.socialAmount() != null
                ? zusInput.socialAmount()
                : missingCurrentZusAmount("social", issues)
            : input.periodContext().jdgActive()
                    && zusInput != null
                    && zusInput.socialAmount() != null
                ? zusInput.socialAmount()
                : !input.periodContext().jdgActive() || qualifyingUop
                    ? BigDecimal.ZERO
                    : required(input.taxInputs(), "JDG_COMPULSORY_SOCIAL_ZUS", issues);
    BigDecimal totalZus = social.add(health).setScale(2, RoundingMode.HALF_UP);
    var zus =
        new AccountingCalculationResult.ZusCalculation(
            social.setScale(2, RoundingMode.HALF_UP),
            health.setScale(2, RoundingMode.HALF_UP),
            totalZus,
            qualifyingUop,
            zusInput != null && zusInput.socialReasonCode() != null
                ? zusInput.socialReasonCode()
                : qualifyingUop ? "UOP_PRIMARY_INSURANCE" : "JDG_PRIMARY_INSURANCE");
    var ryczalt =
        new AccountingCalculationResult.RyczaltCalculation(
            revenue, socialDeduction, health, healthDeduction, taxable, buckets, tax);
    var vat =
        new AccountingCalculationResult.VatCalculation(
            outputVat.subtract(input.adjustments().salesVat()),
            input.adjustments().salesVat(),
            outputVat,
            deductible,
            calculatedVat);
    return new AccountingCalculationResult(
        input.period(),
        new AccountingCalculationResult.RevenueCalculation(domestic, fx.convertedRevenuePln()),
        fx,
        ryczalt,
        vat,
        zus,
        List.of(
            new CalculatedObligation("RYCZALT", tax, input.period()),
            new CalculatedObligation("VAT", calculatedVat, input.period()),
            new CalculatedObligation("ZUS", totalZus, input.period())),
        List.copyOf(issues));
  }

  private Map<BigDecimal, BigDecimal> allocateDeductions(
      Map<BigDecimal, BigDecimal> revenueByRate, BigDecimal deductions) {
    Map<BigDecimal, BigDecimal> result = new LinkedHashMap<>();
    BigDecimal totalRevenue =
        revenueByRate.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal remaining = deductions;
    int index = 0;
    for (var entry : revenueByRate.entrySet()) {
      BigDecimal allocation;
      if (++index == revenueByRate.size()) {
        allocation = remaining;
      } else if (totalRevenue.signum() == 0) {
        allocation = BigDecimal.ZERO;
      } else {
        allocation =
            deductions.multiply(entry.getValue()).divide(totalRevenue, 2, RoundingMode.HALF_UP);
        remaining = remaining.subtract(allocation);
      }
      result.put(entry.getKey(), entry.getValue().subtract(allocation));
    }
    return result;
  }

  private BigDecimal missingCurrentZusAmount(String contribution, List<AccountingIssue> issues) {
    issues.add(
        issue(
            "MISSING_ZUS_RULE_INPUT",
            null,
            "Current calculation requires the effective 2026 ZUS rule input for "
                + contribution
                + "."));
    return BigDecimal.ZERO;
  }

  private void validateVatInputs(AccountingCalculationInput input, List<AccountingIssue> issues) {
    input
        .vatTransactions()
        .forEach(
            transaction -> {
              if (transaction == null) {
                issues.add(issue("INVALID_VAT_TRANSACTION", null, "VAT transaction is missing."));
                return;
              }
              if (transaction.netAmount() == null || transaction.vatAmount() == null) return;
              if (transaction.direction() == AccountingVatTransaction.Direction.PURCHASE
                  && transaction.deductibleVat() != null
                  && transaction.deductibleVat().compareTo(transaction.vatAmount()) > 0) {
                issues.add(
                    issue(
                        "INVALID_DEDUCTIBLE_VAT",
                        transaction.reference(),
                        "Deductible VAT cannot exceed document VAT."));
              }
              if (transaction.netAmount().signum() != transaction.vatAmount().signum()
                  && transaction.vatAmount().signum() != 0) {
                issues.add(
                    issue(
                        "INVALID_VAT_SIGN",
                        transaction.reference(),
                        "Net and VAT amounts must have consistent direction."));
              }
            });
  }

  private AccountingCalculationResult.FxCalculation calculateFx(
      AccountingCalculationInput input, List<AccountingIssue> issues) {
    List<Conversion> entries = new ArrayList<>();
    List<String> unavailable = new ArrayList<>();
    for (InvoiceRow invoice : input.invoices()) {
      if ("PLN".equals(invoice.currency())) continue;
      try {
        CurrencyType source = CurrencyType.valueOf(invoice.currency());
        BigDecimal converted =
            currencyConversion.convertToBaseCurrency(
                invoice.netAmount(), CurrencyType.PLN, source, invoice.fxRateDate());
        if (converted == null) throw new CurrencyConversionUnavailableException("No FX result");
        converted = converted.setScale(2, RoundingMode.HALF_UP);
        entries.add(
            new Conversion(
                invoice.reference(), invoice.currency(), invoice.netAmount(), converted));
      } catch (IllegalArgumentException | CurrencyConversionUnavailableException ex) {
        unavailable.add(invoice.reference());
        issues.add(
            issue(
                "MISSING_FX",
                invoice.reference(),
                "FX conversion is unavailable for " + invoice.currency() + " revenue."));
      }
    }
    BigDecimal total =
        entries.stream().map(Conversion::convertedPln).reduce(BigDecimal.ZERO, BigDecimal::add);
    return new AccountingCalculationResult.FxCalculation(
        List.copyOf(entries), total, List.copyOf(unavailable));
  }

  private BigDecimal required(List<TaxInputRow> inputs, String type, List<AccountingIssue> issues) {
    return inputs.stream()
        .filter(i -> type.equals(i.inputType()))
        .map(TaxInputRow::amount)
        .findFirst()
        .orElseGet(
            () -> {
              issues.add(issue("MISSING_" + type, null, type + " input is required."));
              return BigDecimal.ZERO;
            });
  }

  private AccountingIssue issue(String type, String reference, String message) {
    String severity =
        type.startsWith("MISSING_FX")
                || type.startsWith("INVALID_VAT")
                || type.startsWith("VAT_CLASSIFICATION")
                || type.startsWith("MISSING_EFFECTIVE_TAX_PROFILE")
                || type.startsWith("AMBIGUOUS_VAT_RATE")
            ? "BLOCKING"
            : "INCOMPLETE";
    return new AccountingIssue(type, severity, reference, message);
  }
}
