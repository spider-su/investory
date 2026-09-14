package com.smartbox.investory.accounting.api;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public interface AccountingStagingApi {
  Summary summary(long profileId, YearMonth month);

  List<Row> rows(long profileId, YearMonth month);

  Summary reconcile(long profileId, YearMonth month);

  Promotion promote(long profileId, YearMonth month);

  record Summary(
      int invoiceMatched,
      int invoiceNew,
      int invoiceMismatch,
      int invoiceAmbiguous,
      int bankMatched,
      int bankNew,
      int bankMismatch,
      int bankAmbiguous,
      int readyToPromote,
      int blockingCount) {}

  record Row(
      String type,
      long id,
      String reference,
      String source,
      String status,
      List<String> reasonCodes,
      Long canonicalMatchId,
      BigDecimal amount,
      String currency,
      boolean promoted,
      String sourceType,
      String documentKind,
      java.time.LocalDate documentDate,
      String counterparty,
      String category) {
    public Row(
        String type,
        long id,
        String reference,
        String source,
        String status,
        List<String> reasonCodes,
        Long canonicalMatchId,
        BigDecimal amount,
        String currency,
        boolean promoted) {
      this(
          type,
          id,
          reference,
          source,
          status,
          reasonCodes,
          canonicalMatchId,
          amount,
          currency,
          promoted,
          null,
          null,
          null,
          null,
          null);
    }
  }

  record Promotion(int invoices, int bankTransactions) {}
}
