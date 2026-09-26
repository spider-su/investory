package com.smartbox.investory.ryczalt.application.query;

import com.smartbox.investory.ryczalt.domain.ObligationStatus;
import com.smartbox.investory.ryczalt.domain.ObligationType;
import com.smartbox.investory.ryczalt.persistence.RyczaltObligationEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltObligationJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltPaymentMatchJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodJpaRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Native obligation, payment evidence and settlement-history read queries. */
@Service
public class RyczaltPaymentQueryService {
  private final RyczaltPeriodJpaRepository periods;
  private final RyczaltObligationJpaRepository obligations;
  private final RyczaltPaymentMatchJpaRepository matches;
  private final BigDecimal paymentTolerance;

  public RyczaltPaymentQueryService(
      RyczaltPeriodJpaRepository periods,
      RyczaltObligationJpaRepository obligations,
      RyczaltPaymentMatchJpaRepository matches,
      @Value("${app.ryczalt.payment.tolerance-pln:0}") BigDecimal paymentTolerance) {
    this.periods = periods;
    this.obligations = obligations;
    this.matches = matches;
    this.paymentTolerance = paymentTolerance == null ? BigDecimal.ZERO : paymentTolerance;
  }

  @Transactional(readOnly = true)
  public List<RyczaltObligationReadModel> getObligations(long profileId, YearMonth month) {
    return obligationModels(profileId, obligations(period(profileId, month)));
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

  public List<RyczaltObligationReadModel> obligationModels(
      long profileId, List<RyczaltObligationEntity> rows) {
    return rows.stream().map(row -> obligation(profileId, row)).toList();
  }

  public RyczaltPeriodReadModel.ReconciliationSummary reconciliation(
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

  private RyczaltPeriodEntity period(long profileId, YearMonth month) {
    return periods
        .findByProfileIdAndYearAndMonth(profileId, month.getYear(), month.getMonthValue())
        .orElseThrow(() -> new RyczaltPeriodNotFoundException(profileId, month));
  }

  private List<RyczaltObligationEntity> obligations(RyczaltPeriodEntity period) {
    return obligations.findByProfileIdAndPeriodIdOrderByTypeAsc(period.getProfileId(), period.id());
  }

  private RyczaltObligationReadModel obligation(long profileId, RyczaltObligationEntity row) {
    BigDecimal paid = matches.allocatedForObligation(profileId, row.id());
    if (row.isManuallyPaid()) paid = paid.add(row.getAmount());
    BigDecimal rawOutstanding = row.getAmount().subtract(paid).max(BigDecimal.ZERO);
    BigDecimal outstanding = withinTolerance(rawOutstanding) ? BigDecimal.ZERO : rawOutstanding;
    ObligationStatus status =
        paid.signum() == 0
            ? ObligationStatus.OPEN
            : outstanding.signum() == 0
                ? (paid.compareTo(row.getAmount()) <= 0
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
        status,
        row.isManuallyPaid(),
        row.getManualPaidDate());
  }

  private RyczaltPaymentHistoryReadModel history(
      long profileId, YearMonth month, RyczaltObligationReadModel obligation) {
    LocalDate paymentDate =
        matches.findByProfileIdAndObligationId(profileId, obligation.id()).stream()
            .map(match -> match.getTransaction().getBookingDate())
            .max(Comparator.naturalOrder())
            .orElse(obligation.manualPaidDate());
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

  private boolean paid(RyczaltObligationReadModel obligation) {
    return obligation.outstandingAmount().signum() == 0;
  }

  private boolean withinTolerance(BigDecimal value) {
    return value != null && value.abs().compareTo(paymentTolerance) <= 0;
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
}
