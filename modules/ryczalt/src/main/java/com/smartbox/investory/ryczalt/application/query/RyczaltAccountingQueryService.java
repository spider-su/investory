package com.smartbox.investory.ryczalt.application.query;

import com.smartbox.investory.ryczalt.checker.CheckSeverity;
import com.smartbox.investory.ryczalt.domain.ObligationStatus;
import com.smartbox.investory.ryczalt.domain.ObligationType;
import com.smartbox.investory.ryczalt.domain.PeriodStatus;
import com.smartbox.investory.ryczalt.persistence.CalculationStatus;
import com.smartbox.investory.ryczalt.persistence.CalculationType;
import com.smartbox.investory.ryczalt.persistence.RyczaltCalculationEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltCalculationJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltInvoiceEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltInvoiceJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltObligationEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltObligationJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltPaymentMatchEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltPaymentMatchJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltTransactionEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltTransactionJpaRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Side-effect-free native read boundary for the future Ryczalt REST adapter. */
@Service
public class RyczaltAccountingQueryService {
  private final RyczaltPeriodJpaRepository periods;
  private final RyczaltInvoiceJpaRepository invoices;
  private final RyczaltTransactionJpaRepository transactions;
  private final RyczaltObligationJpaRepository obligations;
  private final RyczaltPaymentMatchJpaRepository matches;
  private final RyczaltCalculationJpaRepository calculations;

  public RyczaltAccountingQueryService(
      RyczaltPeriodJpaRepository periods,
      RyczaltInvoiceJpaRepository invoices,
      RyczaltTransactionJpaRepository transactions,
      RyczaltObligationJpaRepository obligations,
      RyczaltPaymentMatchJpaRepository matches,
      RyczaltCalculationJpaRepository calculations) {
    this.periods = periods;
    this.invoices = invoices;
    this.transactions = transactions;
    this.obligations = obligations;
    this.matches = matches;
    this.calculations = calculations;
  }

  @Transactional(readOnly = true)
  public List<RyczaltPeriodListItem> listPeriods(long profileId) {
    return periods.findByProfileIdOrderByYearDescMonthDesc(profileId).stream()
        .map(period -> new RyczaltPeriodListItem(month(period), period.getStatus()))
        .toList();
  }

  @Transactional(readOnly = true)
  public RyczaltPeriodReadModel getPeriod(long profileId, YearMonth month) {
    Loaded loaded = load(profileId, month);
    List<RyczaltInvoiceEntity> invoiceRows = invoices(loaded.period);
    List<RyczaltTransactionEntity> transactionRows = transactions(loaded);
    List<RyczaltObligationEntity> obligationRows = obligations(loaded.period);
    List<RyczaltObligationReadModel> obligationModels = obligationModels(profileId, obligationRows);
    List<RyczaltIssueReadModel> issues = issues(loaded, obligationModels);
    BigDecimal totalObligations =
        obligationRows.stream()
            .map(RyczaltObligationEntity::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal paid =
        obligationModels.stream()
            .map(RyczaltObligationReadModel::paidAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal outstanding =
        obligationModels.stream()
            .map(RyczaltObligationReadModel::outstandingAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    int paidCount = (int) obligationModels.stream().filter(this::paid).count();
    boolean calculationsCurrent = calculationsCurrent(loaded.calculations);
    boolean complete = calculationsCurrent && issues.isEmpty();
    return new RyczaltPeriodReadModel(
        month,
        loaded.period.getStatus(),
        calculationStates(loaded.calculations, obligationRows),
        invoiceRows.stream()
            .filter(invoice -> invoice.getDirection().name().equals("INCOME"))
            .map(
                invoice ->
                    invoice.getBookedNetPln() == null
                        ? invoice.getNetAmount()
                        : invoice.getBookedNetPln())
            .reduce(BigDecimal.ZERO, BigDecimal::add),
        amountFor(obligationRows, ObligationType.RYCZALT),
        amountFor(obligationRows, ObligationType.VAT),
        amountFor(obligationRows, ObligationType.ZUS),
        totalObligations,
        invoiceRows.size(),
        transactionRows.size(),
        new RyczaltPeriodReadModel.ObligationTotals(
            obligationRows.size(), paidCount, paid, outstanding),
        new RyczaltPeriodReadModel.Completeness(
            complete ? "COMPLETE" : "INCOMPLETE", issues.size()),
        allowedActions(loaded.period, complete, obligationRows.isEmpty()));
  }

  @Transactional(readOnly = true)
  public List<RyczaltInvoiceReadModel> getInvoices(long profileId, YearMonth month) {
    return invoices(load(profileId, month).period).stream().map(this::invoice).toList();
  }

  @Transactional(readOnly = true)
  public List<RyczaltTransactionReadModel> getTransactions(long profileId, YearMonth month) {
    return transactions(load(profileId, month)).stream()
        .map(row -> transaction(profileId, row))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<RyczaltObligationReadModel> getObligations(long profileId, YearMonth month) {
    Loaded loaded = load(profileId, month);
    return obligationModels(profileId, obligations(loaded.period));
  }

  @Transactional(readOnly = true)
  public List<RyczaltIssueReadModel> getIssues(long profileId, YearMonth month) {
    Loaded loaded = load(profileId, month);
    return issues(loaded, obligationModels(profileId, obligations(loaded.period)));
  }

  @Transactional(readOnly = true)
  public List<RyczaltPaymentHistoryReadModel> getPaymentHistory(
      long profileId, YearMonth from, YearMonth to, String obligationType) {
    if (from == null || to == null || from.isAfter(to)) {
      throw new IllegalArgumentException("Invalid payment history range");
    }
    ObligationType type = parseType(obligationType);
    return periods.findByProfileIdOrderByYearDescMonthDesc(profileId).stream()
        .filter(period -> !month(period).isBefore(from) && !month(period).isAfter(to))
        .flatMap(
            period ->
                obligationModels(profileId, obligations(period)).stream()
                    .filter(obligation -> type == null || obligation.type() == type)
                    .map(obligation -> history(profileId, month(period), obligation)))
        .toList();
  }

  private RyczaltPaymentHistoryReadModel history(
      long profileId, YearMonth month, RyczaltObligationReadModel obligation) {
    LocalDate paymentDate =
        matches.findByProfileIdAndObligationId(profileId, obligation.id()).stream()
            .map(RyczaltPaymentMatchEntity::getCreatedAt)
            .max(Comparator.naturalOrder())
            .map(value -> value.atZone(java.time.ZoneOffset.UTC).toLocalDate())
            .orElse(null);
    return new RyczaltPaymentHistoryReadModel(
        obligation.type(),
        month,
        obligation.expectedAmount(),
        obligation.paidAmount(),
        obligation.outstandingAmount(),
        obligation.dueDate(),
        paymentDate,
        obligation.status());
  }

  private List<RyczaltIssueReadModel> issues(
      Loaded loaded, List<RyczaltObligationReadModel> obligationRows) {
    List<RyczaltIssueReadModel> result = new ArrayList<>();
    for (CalculationType type : CalculationType.values()) {
      RyczaltCalculationEntity calculation =
          loaded.calculations.stream()
              .filter(row -> row.getType() == type)
              .findFirst()
              .orElse(null);
      if (calculation == null) {
        result.add(
            new RyczaltIssueReadModel("MISSING_CALCULATION", CheckSeverity.ERROR, type.name()));
      } else if (calculation.getStatus() == CalculationStatus.DIRTY
          || calculation.getStatus() == CalculationStatus.STALE) {
        result.add(
            new RyczaltIssueReadModel("DIRTY_CALCULATION", CheckSeverity.ERROR, type.name()));
      }
    }
    for (RyczaltObligationReadModel obligation : obligationRows) {
      if (!paid(obligation)) {
        result.add(
            new RyczaltIssueReadModel(
                "UNSETTLED_OBLIGATION", CheckSeverity.ERROR, obligation.type().name()));
      }
    }
    return result;
  }

  private List<RyczaltObligationReadModel> obligationModels(
      long profileId, List<RyczaltObligationEntity> rows) {
    return rows.stream().map(row -> obligation(profileId, row)).toList();
  }

  private RyczaltObligationReadModel obligation(long profileId, RyczaltObligationEntity row) {
    BigDecimal paid = matches.allocatedForObligation(profileId, row.id());
    BigDecimal outstanding = row.getAmount().subtract(paid).max(BigDecimal.ZERO);
    ObligationStatus status =
        paid.signum() == 0
            ? ObligationStatus.OPEN
            : paid.compareTo(row.getAmount()) >= 0
                ? (paid.compareTo(row.getAmount()) == 0
                    ? ObligationStatus.PAID
                    : ObligationStatus.OVERPAID)
                : ObligationStatus.PARTIALLY_PAID;
    return new RyczaltObligationReadModel(
        row.id(),
        row.getType(),
        row.getAmount(),
        paid,
        outstanding,
        row.getCurrency(),
        row.getDueDate(),
        status);
  }

  private RyczaltTransactionReadModel transaction(long profileId, RyczaltTransactionEntity row) {
    return new RyczaltTransactionReadModel(
        row.id(),
        row.getBookingDate(),
        row.getAmount(),
        row.getCurrency(),
        row.getReference(),
        row.getCounterparty(),
        row.getDescription(),
        matches.allocatedForTransaction(profileId, row.id()));
  }

  private RyczaltInvoiceReadModel invoice(RyczaltInvoiceEntity row) {
    return new RyczaltInvoiceReadModel(
        row.getId(),
        row.getDirection(),
        row.getReference(),
        row.getIssueDate(),
        row.getAccountingDate(),
        row.getNetAmount(),
        row.getVatAmount(),
        row.getGrossAmount(),
        row.getCurrency(),
        row.getBookedNetPln(),
        row.getRyczaltRate(),
        row.getDeductibleVat());
  }

  private Loaded load(long profileId, YearMonth month) {
    RyczaltPeriodEntity period =
        periods
            .findByProfileIdAndYearAndMonth(profileId, month.getYear(), month.getMonthValue())
            .orElseThrow(() -> new RyczaltPeriodNotFoundException(profileId, month));
    return new Loaded(period, calculations.findByProfileIdAndPeriodId(profileId, period.id()));
  }

  private List<RyczaltInvoiceEntity> invoices(RyczaltPeriodEntity period) {
    return invoices.findByProfileIdAndPeriodIdOrderByAccountingDateAscIdAsc(
        period.getProfileId(), period.id());
  }

  private List<RyczaltTransactionEntity> transactions(Loaded loaded) {
    return transactions.findByProfileIdAndPeriodIdOrderByBookingDateAscIdAsc(
        loaded.period.getProfileId(), loaded.period.id());
  }

  private List<RyczaltObligationEntity> obligations(RyczaltPeriodEntity period) {
    return obligations.findByProfileIdAndPeriodIdOrderByTypeAsc(period.getProfileId(), period.id());
  }

  private List<RyczaltPeriodReadModel.CalculationState> calculationStates(
      List<RyczaltCalculationEntity> rows, List<RyczaltObligationEntity> obligationRows) {
    return rows.stream()
        .sorted(Comparator.comparing(row -> row.getType().name()))
        .map(
            row ->
                new RyczaltPeriodReadModel.CalculationState(
                    row.getType().name(),
                    row.getStatus(),
                    validCalculation(row)
                        ? amountFor(obligationRows, obligationType(row.getType()))
                        : null,
                    row.getRuleVersion(),
                    row.getCalculatorVersion(),
                    row.getCalculatedAt()))
        .toList();
  }

  private boolean validCalculation(RyczaltCalculationEntity row) {
    return row.isCurrent()
        && row.getStatus() != CalculationStatus.DIRTY
        && row.getStatus() != CalculationStatus.STALE;
  }

  private ObligationType obligationType(CalculationType type) {
    return ObligationType.valueOf(type.name());
  }

  private boolean calculationsCurrent(List<RyczaltCalculationEntity> rows) {
    return EnumSet.allOf(CalculationType.class).stream()
        .allMatch(
            type ->
                rows.stream()
                    .anyMatch(
                        row ->
                            row.getType() == type
                                && row.isCurrent()
                                && row.getStatus() != CalculationStatus.DIRTY
                                && row.getStatus() != CalculationStatus.STALE));
  }

  private Set<RyczaltPeriodReadModel.PeriodAction> allowedActions(
      RyczaltPeriodEntity period, boolean complete, boolean noObligations) {
    if (period.getStatus() == PeriodStatus.FROZEN) {
      return Set.of(RyczaltPeriodReadModel.PeriodAction.REOPEN);
    }
    EnumSet<RyczaltPeriodReadModel.PeriodAction> actions =
        EnumSet.of(RyczaltPeriodReadModel.PeriodAction.SETTLE);
    if ((period.getStatus() == PeriodStatus.CALCULATED || period.getStatus() == PeriodStatus.PAID)
        && complete
        && !noObligations) {
      actions.add(RyczaltPeriodReadModel.PeriodAction.FREEZE);
    }
    return actions;
  }

  private BigDecimal amountFor(List<RyczaltObligationEntity> rows, ObligationType type) {
    return rows.stream()
        .filter(row -> row.getType() == type)
        .map(RyczaltObligationEntity::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private boolean paid(RyczaltObligationReadModel obligation) {
    return obligation.paidAmount().compareTo(obligation.expectedAmount()) >= 0;
  }

  private ObligationType parseType(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return ObligationType.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Unknown obligation type: " + value, exception);
    }
  }

  private YearMonth month(RyczaltPeriodEntity period) {
    return YearMonth.of(period.getYear(), period.getMonth());
  }

  private record Loaded(RyczaltPeriodEntity period, List<RyczaltCalculationEntity> calculations) {}
}
