package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.accounting.service.AccountingBankImportService;
import com.smartbox.investory.accounting.service.AccountingBankTransactionIngestionService;
import com.smartbox.investory.accounting.service.AccountingSourceEvidenceService;
import com.smartbox.investory.integrations.bank.ExternalBankTransaction;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class AccountingBankImportServiceTest {
  private final AccountingSourceEvidenceService sources =
      mock(AccountingSourceEvidenceService.class);
  private final AccountingBankTransactionIngestionService ingestion =
      mock(AccountingBankTransactionIngestionService.class);
  private final AccountingBankImportService service =
      new AccountingBankImportService(sources, ingestion);
  private final byte[] validFile =
      ("booking_date;related_period;reference;counterparty;currency;amount;note\n"
              + "2026-09-15;2026-09-01;CUSTOMER RECEIPT;CUSTOMER A;PLN;123.00;invoice receipt\n")
          .getBytes();

  @Test
  void persistsBankSourceBeforeParsingAndMarksSuccessfulImport() {
    when(sources.receiveBank("bank.csv", "text/csv", validFile, LocalDate.of(2026, 9, 1)))
        .thenReturn(7L);
    when(ingestion.ingest(
            any(ExternalBankTransaction.class), org.mockito.ArgumentMatchers.anyLong()))
        .thenReturn(
            new AccountingBankTransactionIngestionService.Result(true, false, "CUSTOMER_RECEIPT"));

    AccountingBankImportService.Result result =
        service.importFile("bank.csv", "text/csv", validFile, LocalDate.of(2026, 9, 1));

    InOrder order = inOrder(sources, ingestion);
    order.verify(sources).receiveBank("bank.csv", "text/csv", validFile, LocalDate.of(2026, 9, 1));
    order
        .verify(ingestion)
        .ingest(any(ExternalBankTransaction.class), org.mockito.ArgumentMatchers.anyLong());
    verify(sources).status(7L, AccountingSourceStatus.PARSED, null);
    verify(sources).status(7L, AccountingSourceStatus.IMPORTED, null);
    org.assertj.core.api.Assertions.assertThat(result)
        .extracting(
            AccountingBankImportService.Result::processedRows,
            AccountingBankImportService.Result::importedRows,
            AccountingBankImportService.Result::reviewRequiredRows)
        .containsExactly(1, 1, 0);
  }

  @Test
  void preservesBankSourceWhenParsingFails() {
    when(sources.receiveBank(anyString(), anyString(), any(), any(LocalDate.class))).thenReturn(8L);

    assertThatThrownBy(
            () ->
                service.importFile(
                    "broken.csv", "text/csv", "bad".getBytes(), LocalDate.of(2026, 9, 1)))
        .isInstanceOf(IllegalArgumentException.class);

    verify(sources).status(eq(8L), eq(AccountingSourceStatus.FAILED), anyString());
  }

  @Test
  void keepsAmbiguousRowsPersistedAndMarksSourceForReview() {
    when(sources.receiveBank(anyString(), anyString(), any(), any(LocalDate.class))).thenReturn(9L);
    when(ingestion.ingest(
            any(ExternalBankTransaction.class), org.mockito.ArgumentMatchers.anyLong()))
        .thenReturn(new AccountingBankTransactionIngestionService.Result(true, true, "UNKNOWN"));

    service.importFile("bank.csv", "text/csv", validFile, LocalDate.of(2026, 9, 1));

    verify(ingestion)
        .ingest(any(ExternalBankTransaction.class), org.mockito.ArgumentMatchers.anyLong());
    verify(sources)
        .status(
            9L,
            AccountingSourceStatus.REVIEW_REQUIRED,
            "1 bank row(s) require classification review");
  }

  @Test
  void skipsAlreadyImportedBankSourceWithoutParsingAgain() {
    when(sources.receiveBank(anyString(), anyString(), any(), any(LocalDate.class)))
        .thenReturn(10L);
    when(sources.status(10L)).thenReturn(AccountingSourceStatus.IMPORTED);

    AccountingBankImportService.Result result =
        service.importFile("bank.csv", "text/csv", validFile, LocalDate.of(2026, 9, 1));

    org.assertj.core.api.Assertions.assertThat(result.processedRows()).isZero();
    org.mockito.Mockito.verifyNoInteractions(ingestion);
  }
}
