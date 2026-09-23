package com.smartbox.investory.ryczalt.persistence;

import com.smartbox.investory.ryczalt.application.port.RyczaltAuditEventWriter;
import com.smartbox.investory.ryczalt.calculation.CalculationInvalidationPolicy;
import com.smartbox.investory.ryczalt.calculation.InputChange;
import com.smartbox.investory.ryczalt.domain.PeriodStatus;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns period transitions and writes a small audit trail for material transitions. */
@Service
public class RyczaltPeriodLifecycleService {
  private final RyczaltPeriodJpaRepository periods;
  private final RyczaltCalculationJpaRepository calculations;
  private final RyczaltObligationJpaRepository obligations;
  private final RyczaltAuditEventWriter auditEvents;

  public RyczaltPeriodLifecycleService(
      RyczaltPeriodJpaRepository periods,
      RyczaltCalculationJpaRepository calculations,
      RyczaltObligationJpaRepository obligations,
      RyczaltAuditEventWriter auditEvents) {
    this.periods = periods;
    this.calculations = calculations;
    this.obligations = obligations;
    this.auditEvents = auditEvents;
  }

  @Transactional
  public void freeze(long profileId, YearMonth month, String actor, String reason) {
    requireReason(reason);
    RyczaltPeriodEntity period = findLocked(profileId, month);
    if (!period.getStatus().canFreeze()) {
      throw new IllegalStateException("Only calculated or paid periods can be frozen");
    }
    if (!FreezeEligibility.isEligible(
        period,
        calculations.findByProfileIdAndPeriodId(profileId, period.id()),
        obligations.findByProfileIdAndPeriodIdOrderByTypeAsc(profileId, period.id()))) {
      throw new IllegalStateException(
          "Period needs current calculations and settled obligations before freezing");
    }
    period.markFrozen(Instant.now());
    periods.save(period);
    calculations
        .findByProfileIdAndPeriodIdAndCurrentTrue(profileId, period.id())
        .forEach(
            calculation -> {
              calculation.markFrozen();
              calculations.save(calculation);
            });
    auditEvents.write(profileId, period.id(), "PERIOD_FROZEN", reason, actor, Instant.now());
  }

  @Transactional
  public void reopen(long profileId, YearMonth month, String actor, String reason) {
    requireReason(reason);
    RyczaltPeriodEntity period = findLocked(profileId, month);
    if (!period.getStatus().isFrozen()) throw new IllegalStateException("Period is not frozen");
    period.markReopened(reason, Instant.now());
    periods.save(period);
    auditEvents.write(profileId, period.id(), "PERIOD_REOPENED", reason, actor, Instant.now());
  }

  @Transactional
  public void invalidate(long profileId, YearMonth month, InputChange change, String actor) {
    RyczaltPeriodEntity period = findLocked(profileId, month);
    if (period.getStatus().isFrozen()) {
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
      period.markDirty();
      periods.save(period);
    }
    auditEvents.write(
        profileId, period.id(), "CALCULATION_INVALIDATED", change.name(), actor, Instant.now());
  }

  private RyczaltPeriodEntity findLocked(long profileId, YearMonth month) {
    return periods
        .findLocked(profileId, month.getYear(), month.getMonthValue())
        .orElseThrow(() -> new IllegalArgumentException("Period does not exist: " + month));
  }

  private static void requireReason(String reason) {
    if (reason == null || reason.isBlank())
      throw new IllegalArgumentException("Reason is required");
  }
}
