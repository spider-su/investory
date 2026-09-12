package com.smartbox.investory.accounting;

import java.time.LocalDate;
import java.util.List;

/** Projects accepted EU B2B facts; VAT calculation remains outside this exporter. */
public final class AccountingVatEuExporter {
  private final AccountingVatClassifier classifier = new AccountingVatClassifier();

  public VatEuResult export(LocalDate period, List<AccountingVatTransaction> transactions) {
    List<AccountingVatTransaction> euRows =
        transactions.stream()
            .filter(t -> t.treatment() == VatTreatment.EU_B2B_REVERSE_CHARGE)
            .toList();
    List<String> issues = euRows.stream().flatMap(t -> classifier.issues(t).stream()).distinct().toList();
    return new VatEuResult(period, euRows, issues, euRows.isEmpty() ? Status.NOT_REQUIRED : Status.READY);
  }

  public enum Status {
    NOT_REQUIRED,
    READY,
    REVIEW_REQUIRED
  }

  public record VatEuResult(
      LocalDate period, List<AccountingVatTransaction> rows, List<String> issues, Status status) {
    public VatEuResult {
      rows = List.copyOf(rows);
      issues = List.copyOf(issues);
      if (!issues.isEmpty()) status = Status.REVIEW_REQUIRED;
    }
  }
}
