package com.smartbox.investory.ryczalt.application;

import com.smartbox.investory.ryczalt.application.query.RyczaltAccountingQueryService;
import com.smartbox.investory.ryczalt.application.query.RyczaltInvoiceReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltIssueReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltObligationReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltPaymentHistoryReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltPeriodListItem;
import com.smartbox.investory.ryczalt.application.query.RyczaltPeriodReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltTransactionReadModel;
import com.smartbox.investory.ryczalt.calculation.application.NativeMonthCalculationInput;
import com.smartbox.investory.ryczalt.calculation.application.NativeMonthCalculationResult;
import com.smartbox.investory.ryczalt.calculation.application.NativeMonthCalculationService;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodLifecycleService;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RyczaltAccountingFacade implements RyczaltAccountingApi {
  private final RyczaltAccountingQueryService queries;
  private final RyczaltPeriodLifecycleService lifecycle;
  private final NativeMonthCalculationService calculations;

  public RyczaltAccountingFacade(
      RyczaltAccountingQueryService queries,
      RyczaltPeriodLifecycleService lifecycle,
      NativeMonthCalculationService calculations) {
    this.queries = queries;
    this.lifecycle = lifecycle;
    this.calculations = calculations;
  }

  @Override
  public boolean hasPeriod(long profileId, YearMonth month) {
    return queries.hasPeriod(profileId, month);
  }

  @Override
  public List<RyczaltPeriodListItem> periods(long profileId) {
    return queries.listPeriods(profileId);
  }

  @Override
  public RyczaltPeriodReadModel period(long profileId, YearMonth month) {
    return queries.getPeriod(profileId, month);
  }

  @Override
  public List<RyczaltInvoiceReadModel> invoices(long profileId, YearMonth month) {
    return queries.getInvoices(profileId, month);
  }

  @Override
  public List<RyczaltInvoiceReadModel> invoices(
      long profileId, YearMonth month, long counterpartyId) {
    return queries.getInvoices(profileId, month, counterpartyId);
  }

  @Override
  public List<RyczaltInvoiceReadModel> invoices(
      long profileId, YearMonth month, Long counterpartyId) {
    return queries.getInvoices(profileId, month, counterpartyId);
  }

  @Override
  public List<RyczaltTransactionReadModel> transactions(long profileId, YearMonth month) {
    return queries.getTransactions(profileId, month);
  }

  @Override
  public List<RyczaltObligationReadModel> obligations(long profileId, YearMonth month) {
    return queries.getObligations(profileId, month);
  }

  @Override
  public List<RyczaltIssueReadModel> issues(long profileId, YearMonth month) {
    return queries.getIssues(profileId, month);
  }

  @Override
  public List<RyczaltPaymentHistoryReadModel> paymentHistory(
      long profileId, YearMonth from, YearMonth to, String type) {
    return queries.getPaymentHistory(profileId, from, to, type);
  }

  @Override
  public void freeze(long profileId, YearMonth month, String actor, String reason) {
    lifecycle.freeze(profileId, month, actor, reason);
  }

  @Override
  public void reopen(long profileId, YearMonth month, String actor, String reason) {
    lifecycle.reopen(profileId, month, actor, reason);
  }

  @Override
  public NativeMonthCalculationResult calculate(
      long profileId, YearMonth month, NativeMonthCalculationInput input) {
    return calculations.calculate(profileId, month, input);
  }

  @Override
  public NativeMonthCalculationResult calculateFromPersistedFacts(long profileId, YearMonth month) {
    return calculations.calculateFromPersistedInvoices(profileId, month);
  }
}
