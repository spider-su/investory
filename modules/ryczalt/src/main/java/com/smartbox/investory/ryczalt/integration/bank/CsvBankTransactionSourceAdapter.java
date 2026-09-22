package com.smartbox.investory.ryczalt.integration.bank;

import com.smartbox.investory.integrations.bank.BankTransactionQuery;
import com.smartbox.investory.integrations.bank.CsvBankTransactionSource;
import com.smartbox.investory.integrations.bank.ExternalBankTransaction;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adapts the generic CSV bank transport to the native Ryczalt bank source port. Reuses the shared
 * parser; no accounting normalization or persistence is involved.
 */
@Component
public class CsvBankTransactionSourceAdapter implements BankTransactionSourcePort {
  public static final String SOURCE = "BANK_CSV";

  private final String externalAccountId;

  public CsvBankTransactionSourceAdapter(
      @Value("${ryczalt.sources.bank.external-account-id:JDG_MAIN_ACCOUNT}")
          String externalAccountId) {
    this.externalAccountId = externalAccountId;
  }

  @Override
  public List<BankTransactionSourceRecord> fetch(BankImportSource source) {
    List<ExternalBankTransaction> rows =
        new CsvBankTransactionSource(source.content(), externalAccountId)
            .transactions(new BankTransactionQuery(externalAccountId, null, null, null))
            .transactions();
    return rows.stream().map(this::toRecord).toList();
  }

  private BankTransactionSourceRecord toRecord(ExternalBankTransaction row) {
    String identity =
        String.join(
            ":", row.provider().name(), row.externalAccountId(), row.externalTransactionId());
    return new BankTransactionSourceRecord(
        identity,
        SOURCE,
        row.bookingDate(),
        row.relatedPeriod(),
        row.amount(),
        row.currency(),
        row.counterpartyName(),
        row.counterpartyAccount(),
        row.rawReference(),
        row.remittanceInformation());
  }
}
