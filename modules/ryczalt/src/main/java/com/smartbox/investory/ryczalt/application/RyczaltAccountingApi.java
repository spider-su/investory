package com.smartbox.investory.ryczalt.application;

import com.smartbox.investory.ryczalt.application.query.RyczaltInvoiceReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltIssueReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltObligationReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltPaymentHistoryReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltPeriodListItem;
import com.smartbox.investory.ryczalt.application.query.RyczaltPeriodReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltTransactionReadModel;
import java.time.YearMonth;
import java.util.List;

/** Native Ryczalt application boundary used by native REST and compatibility adapters. */
public interface RyczaltAccountingApi {
  boolean hasPeriod(long profileId, YearMonth month);

  List<RyczaltPeriodListItem> periods(long profileId);

  RyczaltPeriodReadModel period(long profileId, YearMonth month);

  List<RyczaltInvoiceReadModel> invoices(long profileId, YearMonth month);

  List<RyczaltInvoiceReadModel> invoices(long profileId, YearMonth month, long counterpartyId);

  List<RyczaltTransactionReadModel> transactions(long profileId, YearMonth month);

  List<RyczaltObligationReadModel> obligations(long profileId, YearMonth month);

  List<RyczaltIssueReadModel> issues(long profileId, YearMonth month);

  List<RyczaltPaymentHistoryReadModel> paymentHistory(
      long profileId, YearMonth from, YearMonth to, String type);

  void settle(long profileId, YearMonth month);

  void freeze(long profileId, YearMonth month, String actor, String reason);

  void reopen(long profileId, YearMonth month, String actor, String reason);
}
