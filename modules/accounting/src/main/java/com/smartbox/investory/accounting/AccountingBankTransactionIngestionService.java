package com.smartbox.investory.accounting;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountingBankTransactionIngestionService {
  private final AccountingPocRepository repository;

  public Result ingest(
      AccountingBankFileParser.ParsedBankTransaction row, long sourceId, String sourceRowIdentity) {
    Classification classification = classify(row);
    String note = row.note() == null ? "" : row.note() + " ";
    note += "Bank source row " + sourceRowIdentity + ".";
    boolean inserted =
        repository.insertBankTransaction(
            row.bookingDate(),
            row.relatedPeriod(),
            row.reference(),
            row.counterparty(),
            row.currency(),
            row.amount(),
            classification.transactionType(),
            classification.scope(),
            note,
            sourceId,
            sourceRowIdentity);
    return new Result(inserted, classification.reviewRequired(), classification.transactionType());
  }

  private Classification classify(AccountingBankFileParser.ParsedBankTransaction row) {
    String text =
        (row.reference() + " " + row.counterparty() + " " + (row.note() == null ? "" : row.note()))
            .toUpperCase();
    if (contains(text, "OWN_ACCOUNT", "INTERNAL", "TRANSFER", "TRANSFER OF FUNDS"))
      return new Classification("INTERNAL_TRANSFER", "EXCLUDED_INTERNAL", false);
    if (contains(text, "VAT", "VAT-7")) return new Classification("VAT_PAYMENT", "BUSINESS", false);
    if (contains(text, "RYCZALT", "RYCZAŁT", "PPE"))
      return new Classification("RYCZALT_PAYMENT", "BUSINESS", false);
    if (contains(text, "ZUS")) return new Classification("ZUS_PAYMENT", "BUSINESS", false);
    if (row.amount().signum() > 0 && contains(text, "CUSTOMER", "CLIENT", "RECEIPT", "RECEIVED"))
      return new Classification("CUSTOMER_RECEIPT", "BUSINESS", false);
    if (row.amount().signum() < 0 && contains(text, "SUPPLIER", "VENDOR", "PAYMENT", "PURCHASE"))
      return new Classification("SUPPLIER_PAYMENT", "BUSINESS", false);
    return new Classification("UNKNOWN", "REVIEW_REQUIRED", true);
  }

  private boolean contains(String text, String... terms) {
    for (String term : terms) if (text.contains(term)) return true;
    return false;
  }

  public record Result(boolean inserted, boolean reviewRequired, String transactionType) {}

  private record Classification(String transactionType, String scope, boolean reviewRequired) {}
}
