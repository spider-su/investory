package com.smartbox.investory.accounting;

import com.smartbox.investory.accounting.AccountingMonthSnapshot.ExpenseRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.InvoiceRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;

/** Calculates VAT from explicit VAT classifications and normalized document facts. */
final class VatCalculator {
  AccountingCalculationResult.VatCalculation calculate(
      AccountingCalculationInput input, List<AccountingIssue> issues) {
    BigDecimal outputVat;
    BigDecimal deductible;
    if (input.calculationMode() == AccountingCalculationMode.CURRENT_CALCULATION) {
      var required = new HashSet<String>();
      input.invoices().stream()
          .map(InvoiceRow::reference)
          .filter(this::present)
          .forEach(required::add);
      input.expenses().stream()
          .map(ExpenseRow::reference)
          .filter(this::present)
          .forEach(required::add);
      var classified = new HashSet<String>();
      var duplicate = new HashSet<String>();
      input.vatTransactions().stream()
          .map(AccountingVatTransaction::reference)
          .filter(this::present)
          .forEach(
              reference -> {
                if (!classified.add(reference)) duplicate.add(reference);
              });
      required.removeAll(classified);
      required.forEach(
          reference ->
              issues.add(
                  issue(
                      "MISSING_VAT_CLASSIFICATION",
                      reference,
                      "Current calculation requires an explicit VAT treatment for this document.")));
      duplicate.forEach(
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
                i ->
                    i.type().equals("MISSING_VAT_CLASSIFICATION")
                        || i.type().equals("DUPLICATE_VAT_CLASSIFICATION"))) {
      outputVat = BigDecimal.ZERO;
      deductible = BigDecimal.ZERO;
    } else {
      AccountingVatClassifier classifier = new AccountingVatClassifier();
      input
          .vatTransactions()
          .forEach(
              t ->
                  classifier
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
    BigDecimal calculated =
        outputVat
            .setScale(0, RoundingMode.HALF_UP)
            .subtract(deductible.setScale(0, RoundingMode.HALF_UP))
            .max(BigDecimal.ZERO);
    return new AccountingCalculationResult.VatCalculation(
        outputVat.subtract(input.adjustments().salesVat()),
        input.adjustments().salesVat(),
        outputVat,
        deductible,
        calculated);
  }

  private boolean present(String value) {
    return value != null && !value.isBlank();
  }

  private AccountingIssue issue(String type, String reference, String message) {
    return new AccountingIssue(type, "BLOCKING", reference, message);
  }
}
