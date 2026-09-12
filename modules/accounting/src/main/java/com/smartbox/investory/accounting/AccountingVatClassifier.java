package com.smartbox.investory.accounting;

import java.util.List;

/** Validates explicit VAT treatment; it never infers treatment from currency. */
public final class AccountingVatClassifier {
  public List<String> issues(AccountingVatTransaction transaction) {
    if (transaction == null || transaction.treatment() == null)
      return List.of("VAT treatment is required");
    if (transaction.treatment() == VatTreatment.EU_B2B_REVERSE_CHARGE
        && blank(transaction.vatEuNumber()))
      return List.of("EU VAT-UE identity evidence is required");
    if (transaction.treatment() == VatTreatment.EU_B2B_REVERSE_CHARGE
        && !"VERIFIED".equals(transaction.viesStatus()))
      return List.of("EU VAT-UE verification evidence is required");
    return List.of();
  }

  public boolean accepted(AccountingVatTransaction transaction) {
    return issues(transaction).isEmpty();
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
