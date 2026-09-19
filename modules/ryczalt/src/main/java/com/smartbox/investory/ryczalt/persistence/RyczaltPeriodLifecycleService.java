package com.smartbox.investory.ryczalt.persistence;

import com.smartbox.investory.ryczalt.calculation.CalculationInvalidationPolicy;
import com.smartbox.investory.ryczalt.calculation.InputChange;
import com.smartbox.investory.ryczalt.domain.PeriodStatus;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns period transitions and writes a small audit trail for material transitions. */
@Service
public class RyczaltPeriodLifecycleService {
  private final RyczaltPeriodJpaRepository periods;
  private final RyczaltCalculationJpaRepository calculations;
  private final RyczaltObligationJpaRepository obligations;
  private final JdbcTemplate jdbc;

  public RyczaltPeriodLifecycleService(
      RyczaltPeriodJpaRepository periods,
      RyczaltCalculationJpaRepository calculations,
      RyczaltObligationJpaRepository obligations,
      JdbcTemplate jdbc) {
    this.periods = periods;
    this.calculations = calculations;
    this.obligations = obligations;
    this.jdbc = jdbc;
  }

  @Transactional
  public void freeze(long profileId, YearMonth month, String actor, String reason) {
    requireReason(reason);
    RyczaltPeriodEntity period = find(profileId, month);
    if (period.getStatus() != PeriodStatus.CALCULATED && period.getStatus() != PeriodStatus.PAID) {
      throw new IllegalStateException("Only calculated or paid periods can be frozen");
    }
    boolean unsettled =
        obligations.findByProfileIdAndPeriodIdOrderByTypeAsc(profileId, period.id()).stream()
            .anyMatch(
                obligation ->
                    obligation.getStatus()
                            != com.smartbox.investory.ryczalt.domain.ObligationStatus.PAID
                        && obligation.getStatus()
                            != com.smartbox.investory.ryczalt.domain.ObligationStatus.OVERPAID);
    if (unsettled)
      throw new IllegalStateException("All obligations must be settled before freezing");
    period.markFrozen(Instant.now());
    periods.save(period);
    calculations
        .findByProfileIdAndPeriodIdAndCurrentTrue(profileId, period.id())
        .forEach(
            calculation -> {
              calculation.markFrozen();
              calculations.save(calculation);
            });
    audit(profileId, period.id(), "PERIOD_FROZEN", reason, actor);
  }

  @Transactional
  public void reopen(long profileId, YearMonth month, String actor, String reason) {
    requireReason(reason);
    RyczaltPeriodEntity period = find(profileId, month);
    if (period.getStatus() != PeriodStatus.FROZEN)
      throw new IllegalStateException("Period is not frozen");
    period.markReopened(reason, Instant.now());
    periods.save(period);
    audit(profileId, period.id(), "PERIOD_REOPENED", reason, actor);
  }

  @Transactional
  public void invalidate(long profileId, YearMonth month, InputChange change, String actor) {
    RyczaltPeriodEntity period = find(profileId, month);
    if (period.getStatus() == PeriodStatus.FROZEN) {
      throw new FrozenPeriodMutationException(profileId, month.getYear(), month.getMonthValue());
    }
    Set<CalculationType> affected = CalculationInvalidationPolicy.affectedBy(change);
    affected.forEach(
        type ->
            calculations
                .findByProfileIdAndPeriodIdAndTypeAndCurrentTrue(profileId, period.id(), type)
                .ifPresent(
                    calculation -> {
                      calculation.markStale();
                      calculations.save(calculation);
                    }));
    // A change that no calculation depends on (for example a bank transaction) must not force a
    // recalculation of already-current tax figures. It still records provenance for reconciliation.
    if (!affected.isEmpty() && period.getStatus() != PeriodStatus.OPEN) {
      period.setStatus(PeriodStatus.DIRTY);
      periods.save(period);
    }
    audit(profileId, period.id(), "CALCULATION_INVALIDATED", change.name(), actor);
  }

  private RyczaltPeriodEntity find(long profileId, YearMonth month) {
    return periods
        .findByProfileIdAndYearAndMonth(profileId, month.getYear(), month.getMonthValue())
        .orElseThrow(() -> new IllegalArgumentException("Period does not exist: " + month));
  }

  private void audit(long profileId, Long periodId, String event, String reason, String actor) {
    jdbc.update(
        "INSERT INTO investory.ryczalt_audit_event (profile_id, period_id, event_type, reason,"
            + " actor, occurred_at) VALUES (?, ?, ?, ?, ?, ?)",
        profileId,
        periodId,
        event,
        reason,
        actor,
        java.sql.Timestamp.from(Instant.now()));
  }

  private static void requireReason(String reason) {
    if (reason == null || reason.isBlank())
      throw new IllegalArgumentException("Reason is required");
  }
}
