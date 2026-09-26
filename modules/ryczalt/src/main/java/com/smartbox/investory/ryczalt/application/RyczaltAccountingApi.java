package com.smartbox.investory.ryczalt.application;

import com.smartbox.investory.ryczalt.application.query.RyczaltInvoiceReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltIssueReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltObligationReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltPaymentHistoryReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltPeriodListItem;
import com.smartbox.investory.ryczalt.application.query.RyczaltPeriodReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltTransactionReadModel;
import com.smartbox.investory.ryczalt.calculation.application.NativeMonthCalculationInput;
import com.smartbox.investory.ryczalt.calculation.application.NativeMonthCalculationResult;
import com.smartbox.investory.ryczalt.pit28.Pit28MonthlyZusFacts;
import java.time.YearMonth;
import java.util.List;

/** Native Ryczalt application boundary used by native REST and compatibility adapters. */
public interface RyczaltAccountingApi {
  boolean hasPeriod(long profileId, YearMonth month);

  List<RyczaltPeriodListItem> periods(long profileId);

  RyczaltPeriodReadModel period(long profileId, YearMonth month);

  List<RyczaltInvoiceReadModel> invoices(long profileId, YearMonth month);

  List<RyczaltInvoiceReadModel> invoices(long profileId, YearMonth month, long counterpartyId);

  List<RyczaltInvoiceReadModel> invoices(long profileId, YearMonth month, Long counterpartyId);

  List<RyczaltTransactionReadModel> transactions(long profileId, YearMonth month);

  List<RyczaltObligationReadModel> obligations(long profileId, YearMonth month);

  List<RyczaltIssueReadModel> issues(long profileId, YearMonth month);

  List<RyczaltPaymentHistoryReadModel> paymentHistory(
      long profileId, YearMonth from, YearMonth to, String type);

  Pit28MonthlyZusFacts zusFacts(long profileId, YearMonth month);

  void freeze(long profileId, YearMonth month, String actor, String reason);

  void reopen(long profileId, YearMonth month, String actor, String reason);

  void markObligationPaid(
      long profileId, long obligationId, java.time.LocalDate paidDate, String note);

  void markObligationUnpaid(long profileId, long obligationId);

  NativeMonthCalculationResult calculate(
      long profileId, YearMonth month, NativeMonthCalculationInput input);

  NativeMonthCalculationResult calculateFromPersistedFacts(long profileId, YearMonth month);
}
