package com.smartbox.investory.accounting;

import com.smartbox.investory.integrations.bank.ExternalBankTransaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountingBankTransactionIngestionService {
  private final AccountingPocRepository repository;

  public Result ingest(ExternalBankTransaction row, long sourceId) {
    return ingest(row, sourceId, null);
  }

  public Result ingest(ExternalBankTransaction row, long sourceId, Long profileId) {
    Classification classification = classify(row);
    boolean inserted =
        repository.insertBankTransaction(
            row.bookingDate(),
            row.relatedPeriod(),
            row.rawReference(),
            row.counterpartyName(),
            row.currency(),
            row.amount(),
            classification.transactionType(),
            classification.scope(),
            row.remittanceInformation(),
            sourceId,
            profileId,
            row.provider().name(),
            row.externalAccountId(),
            row.externalTransactionId(),
            row.sourcePayloadHash());
    return new Result(inserted, classification.reviewRequired(), classification.transactionType());
  }

  /** Compatibility adapter for callers compiled against the original POC parser boundary. */
  public Result ingest(
      AccountingBankFileParser.ParsedBankTransaction row, long sourceId, String sourceRowIdentity) {
    Classification classification = classifyLegacy(row);
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

  private Classification classifyLegacy(AccountingBankFileParser.ParsedBankTransaction row) {
    return classify(
        new ExternalBankTransaction(
            com.smartbox.investory.integrations.bank.BankDataProvider.CSV,
            "LEGACY_SOURCE",
            row.reference(),
            row.bookingDate(),
            row.bookingDate(),
            row.relatedPeriod(),
            row.amount(),
            row.currency(),
            row.counterparty(),
            null,
            row.note(),
            row.reference(),
            null));
  }

  private Classification classify(ExternalBankTransaction row) {
    String text =
        (row.rawReference()
                + " "
                + row.counterpartyName()
                + " "
                + (row.remittanceInformation() == null ? "" : row.remittanceInformation()))
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
