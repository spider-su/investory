package com.smartbox.investory.ryczalt.calculation.application;

import com.smartbox.investory.ryczalt.calculation.InputFingerprint;
import com.smartbox.investory.ryczalt.calculation.ryczalt.RyczaltCalculationInput;
import com.smartbox.investory.ryczalt.calculation.ryczalt.RyczaltCalculationResult;
import com.smartbox.investory.ryczalt.calculation.ryczalt.RyczaltCalculator;
import com.smartbox.investory.ryczalt.persistence.CalculationType;
import com.smartbox.investory.ryczalt.persistence.RyczaltCalculationEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltCalculationJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltCalculationPersistenceAdapter;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodJpaRepository;
import java.time.YearMonth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates pure calculation, deterministic reuse and persisted calculation revisions. */
@Service
public class RyczaltCalculationApplicationService {
  private static final String RULE_VERSION = "ryczalt-2026";
  private static final String CALCULATOR_VERSION = "ryczalt-calculator-1";
  private final RyczaltPeriodJpaRepository periods;
  private final RyczaltCalculationJpaRepository calculations;
  private final RyczaltCalculationPersistenceAdapter persistence;
  private final RyczaltCalculator calculator = new RyczaltCalculator(RULE_VERSION);

  public RyczaltCalculationApplicationService(
      RyczaltPeriodJpaRepository periods,
      RyczaltCalculationJpaRepository calculations,
      RyczaltCalculationPersistenceAdapter persistence) {
    this.periods = periods;
    this.calculations = calculations;
    this.persistence = persistence;
  }

  @Transactional
  public CalculationExecution<RyczaltCalculationResult> calculate(
      long profileId, YearMonth month, RyczaltCalculationInput input) {
    RyczaltPeriodEntity period =
        periods
            .findLocked(profileId, month.getYear(), month.getMonthValue())
            .orElseThrow(() -> new IllegalArgumentException("Period does not exist: " + month));
    String fingerprint = InputFingerprint.ryczalt(input, RULE_VERSION);
    RyczaltCalculationEntity current =
        calculations
            .findByProfileIdAndPeriodIdAndTypeAndCurrentTrue(
                profileId, period.id(), CalculationType.RYCZALT)
            .orElse(null);
    if (current != null && current.getInputFingerprint().equals(fingerprint)) {
      return new CalculationExecution<>(null, true, current.getRevision(), fingerprint);
    }
    if (period.getStatus().isFrozen()) {
      if (current == null)
        throw new IllegalStateException("Frozen period has no current calculation");
      return new CalculationExecution<>(
          null, true, current.getRevision(), current.getInputFingerprint());
    }
    RyczaltCalculationResult result = calculator.calculate(input);
    RyczaltCalculationEntity saved =
        persistence.saveCurrent(
            period,
            profileId,
            CalculationType.RYCZALT,
            json(result),
            fingerprint,
            RULE_VERSION,
            CALCULATOR_VERSION);
    period.markCalculated(java.time.Instant.now());
    periods.save(period);
    return new CalculationExecution<>(result, false, saved.getRevision(), fingerprint);
  }

  private String json(RyczaltCalculationResult result) {
    return "{\"revenue\":\""
        + result.revenue().toPlainString()
        + "\",\"revenueBeforeDeductions\":\""
        + result.revenue().toPlainString()
        + "\",\"socialContributionDeduction\":\""
        + result.socialContributionDeduction().toPlainString()
        + "\",\"healthContributionPaid\":\""
        + result.healthContributionPaid().toPlainString()
        + "\",\"healthDeduction\":\""
        + result.healthDeduction().toPlainString()
        + "\",\"otherDeduction\":\"0\""
        + ",\"taxableBase\":\""
        + result.taxableBase().toPlainString()
        + "\",\"calculatedTax\":\""
        + result.calculatedTax().toPlainString()
        + "\",\"ruleVersion\":\""
        + result.ruleVersion()
        + "\"}";
  }
}
