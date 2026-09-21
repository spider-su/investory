package com.smartbox.investory.ryczalt.application.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.beans.factory.annotation.Autowired;
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
  private final ObjectMapper json;

  public RyczaltAccountingQueryService(
      RyczaltPeriodJpaRepository periods,
      RyczaltInvoiceJpaRepository invoices,
      RyczaltTransactionJpaRepository transactions,
      RyczaltObligationJpaRepository obligations,
      RyczaltPaymentMatchJpaRepository matches,
      RyczaltCalculationJpaRepository calculations,
      ObjectMapper json) {
    this.periods = periods;
    this.invoices = invoices;
    this.transactions = transactions;
    this.obligations = obligations;
    this.matches = matches;
    this.calculations = calculations;
    this.json = json;
  }

  @Autowired
  public RyczaltAccountingQueryService(
      RyczaltPeriodJpaRepository periods,
      RyczaltInvoiceJpaRepository invoices,
      RyczaltTransactionJpaRepository transactions,
      RyczaltObligationJpaRepository obligations,
      RyczaltPaymentMatchJpaRepository matches,
      RyczaltCalculationJpaRepository calculations) {
    this(periods, invoices, transactions, obligations, matches, calculations, new ObjectMapper());
  }

  @Transactional(readOnly = true)
  public List<RyczaltPeriodListItem> listPeriods(long profileId) {
    return periods.findByProfileIdOrderByYearDescMonthDesc(profileId).stream()
        .map(period -> new RyczaltPeriodListItem(month(period), period.getStatus()))
        .toList();
  }

  @Transactional(readOnly = true)
  public boolean hasPeriod(long profileId, YearMonth month) {
    return listPeriods(profileId).stream().anyMatch(period -> period.month().equals(month));
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
    BigDecimal outstanding =
        obligationModels.stream()
            .map(RyczaltObligationReadModel::outstandingAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    boolean calculationsCurrent = calculationsCurrent(loaded.calculations);
    long blockingIssues =
        issues.stream().filter(issue -> issue.kind() == IssueKind.BLOCKED).count();
    boolean complete = calculationsCurrent && blockingIssues == 0;
    BigDecimal totalPaid =
        obligationModels.stream()
            .map(RyczaltObligationReadModel::paidAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    int paidCount = (int) obligationModels.stream().filter(this::paid).count();
    RyczaltPeriodReadModel.Summary summary =
        new RyczaltPeriodReadModel.Summary(
            invoiceRows.stream()
                .filter(invoice -> invoice.getDirection().name().equals("INCOME"))
                .map(
                    invoice ->
                        invoice.getBookedNetPln() == null
                            ? invoice.getNetAmount()
                            : invoice.getBookedNetPln())
                .reduce(BigDecimal.ZERO, BigDecimal::add),
            validAmount(loaded.calculations, CalculationType.RYCZALT, obligationRows),
            validAmount(loaded.calculations, CalculationType.VAT, obligationRows),
            validAmount(loaded.calculations, CalculationType.ZUS, obligationRows));
    return new RyczaltPeriodReadModel(
        month,
        loaded.period.getStatus(),
        calculationStates(loaded.calculations, obligationRows),
        summary,
        audit(profileId, month, summary, loaded.calculations),
        new RyczaltPeriodReadModel.Documents(invoiceRows.size(), transactionRows.size()),
        new RyczaltPeriodReadModel.SettlementSummary(
            obligationRows.size(),
            paidCount,
            obligationRows.size() - paidCount,
            totalObligations,
            totalPaid,
            outstanding,
            outstanding.signum() == 0),
        reconciliation(obligationModels),
        new RyczaltPeriodReadModel.Completeness(
            complete ? "COMPLETE" : "INCOMPLETE", (int) blockingIssues),
        allowedActions(loaded.period, complete, obligationRows.isEmpty()));
  }

  private RyczaltPeriodReadModel.Audit audit(
      long profileId,
      YearMonth month,
      RyczaltPeriodReadModel.Summary summary,
      List<RyczaltCalculationEntity> current) {
    JsonNode ryczalt = result(current, CalculationType.RYCZALT);
    JsonNode vat = result(current, CalculationType.VAT);
    BigDecimal monthlyAdvance = number(ryczalt, "monthlyAdvance", number(ryczalt, "calculatedTax"));
    BigDecimal cumulativeTax = number(ryczalt, "cumulativeTax", yearToDateTax(profileId, month));
    return new RyczaltPeriodReadModel.Audit(
        number(ryczalt, "revenueBeforeDeductions", summary.revenue()),
        number(ryczalt, "socialContributionDeduction"),
        number(ryczalt, "healthDeduction"),
        number(ryczalt, "otherDeduction"),
        number(ryczalt, "taxableBase"),
        cumulativeTax,
        monthlyAdvance,
        number(vat, "outputVatAfterSalesCorrection", number(vat, "outputVat")),
        number(vat, "deductibleInputVat", number(vat, "inputVat")),
        number(vat, "explicitVatAdjustments", number(vat, "vatAdjustments")),
        number(vat, "calculatedVat", summary.vat()));
  }

  private JsonNode result(List<RyczaltCalculationEntity> rows, CalculationType type) {
    return rows.stream()
        .filter(row -> row.getType() == type && validCalculation(row))
        .findFirst()
        .map(row -> readJson(row.getResultJson()))
        .orElse(null);
  }

  private JsonNode readJson(String value) {
    try {
      return value == null ? null : json.readTree(value);
    } catch (Exception exception) {
      return null;
    }
  }

  private BigDecimal number(JsonNode node, String name) {
    return number(node, name, BigDecimal.ZERO);
  }

  private BigDecimal number(JsonNode node, String name, BigDecimal fallback) {
    if (node == null || node.get(name) == null || node.get(name).isNull()) return fallback;
    try {
      JsonNode value = node.get(name);
      return value.isNumber() ? value.decimalValue() : new BigDecimal(value.asText());
    } catch (RuntimeException exception) {
      return fallback;
    }
  }

  private BigDecimal yearToDateTax(long profileId, YearMonth month) {
    return periods.findByProfileIdOrderByYearDescMonthDesc(profileId).stream()
        .filter(period -> period.getYear() == month.getYear())
        .filter(period -> period.getMonth() <= month.getMonthValue())
        .flatMap(period -> calculations.findByProfileIdAndPeriodId(profileId, period.id()).stream())
        .filter(row -> row.getType() == CalculationType.RYCZALT && validCalculation(row))
        .map(row -> number(readJson(row.getResultJson()), "calculatedTax"))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  @Transactional(readOnly = true)
  public List<RyczaltInvoiceReadModel> getInvoices(long profileId, YearMonth month) {
    return invoices(load(profileId, month).period).stream().map(this::invoice).toList();
  }

  @Transactional(readOnly = true)
  public List<RyczaltInvoiceReadModel> getInvoices(
      long profileId, YearMonth month, long counterpartyId) {
    Loaded loaded = load(profileId, month);
    return invoices
        .findByProfileIdAndPeriodIdAndCounterparty_IdOrderByAccountingDateAscIdAsc(
            profileId, loaded.period.id(), counterpartyId)
        .stream()
        .map(this::invoice)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<RyczaltInvoiceReadModel> getInvoices(
      long profileId, YearMonth month, Long counterpartyId) {
    if (month != null) {
      return counterpartyId == null
          ? getInvoices(profileId, month)
          : getInvoices(profileId, month, counterpartyId.longValue());
    }
    var rows =
        counterpartyId == null
            ? invoices.findByProfileIdOrderByAccountingDateAscIdAsc(profileId)
            : invoices.findByProfileIdAndCounterparty_IdOrderByAccountingDateAscIdAsc(
                profileId, counterpartyId);
    return rows.stream().map(this::invoice).toList();
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
            new RyczaltIssueReadModel(
                "calculation:" + type.name(),
                "MISSING_CALCULATION",
                CheckSeverity.ERROR,
                IssueKind.BLOCKED,
                "Missing calculation",
                "No calculation is available for " + type.name(),
                type.name()));
      } else if (calculation.getStatus() == CalculationStatus.DIRTY
          || calculation.getStatus() == CalculationStatus.STALE) {
        result.add(
            new RyczaltIssueReadModel(
                "calculation:" + type.name(),
                "DIRTY_CALCULATION",
                CheckSeverity.ERROR,
                IssueKind.BLOCKED,
                "Calculation needs refresh",
                "The " + type.name() + " calculation is not current",
                type.name()));
      }
    }
    for (RyczaltObligationReadModel obligation : obligationRows) {
      if (!paid(obligation)) {
        result.add(
            new RyczaltIssueReadModel(
                "obligation:" + obligation.id(),
                "UNSETTLED_OBLIGATION",
                CheckSeverity.INFO,
                IssueKind.SETTLEMENT,
                "Payment outstanding",
                "The " + obligation.type().name() + " obligation is not fully paid",
                String.valueOf(obligation.id())));
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
        row.getDeductibleVat(),
        row.getClassification(),
        row.getCounterparty() == null
            ? null
            : new RyczaltInvoiceReadModel.CounterpartyView(
                row.getCounterparty().id(),
                row.getCounterparty().getLegalName(),
                row.getCounterparty().getAlias()),
        row.getApprovalStatus(),
        row.getApprovalMethod(),
        row.getPaymentVerificationPolicy(),
        row.getPaymentVerificationPolicy()
                == com.smartbox.investory.ryczalt.domain.PaymentVerificationPolicy.NOT_REQUIRED
            ? com.smartbox.investory.ryczalt.domain.InvoicePaymentStatus.NOT_REQUIRED
            : com.smartbox.investory.ryczalt.domain.InvoicePaymentStatus.fromPersisted(
                row.getPaymentStatus()));
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

  private BigDecimal validAmount(
      List<RyczaltCalculationEntity> rows,
      CalculationType type,
      List<RyczaltObligationEntity> obligations) {
    return rows.stream()
        .filter(row -> row.getType() == type && validCalculation(row))
        .findFirst()
        .map(row -> amountFor(obligations, obligationType(type)))
        .orElse(null);
  }

  private RyczaltPeriodReadModel.ReconciliationSummary reconciliation(
      List<RyczaltObligationReadModel> obligations) {
    int settled = (int) obligations.stream().filter(this::paid).count();
    int mismatches =
        (int)
            obligations.stream()
                .filter(obligation -> obligation.paidAmount().signum() > 0 && !paid(obligation))
                .count();
    int missingEvidence =
        (int)
            obligations.stream()
                .filter(obligation -> obligation.paidAmount().signum() == 0)
                .count();
    return new RyczaltPeriodReadModel.ReconciliationSummary(
        obligations.size(), settled, mismatches, missingEvidence);
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
