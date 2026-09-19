package com.smartbox.investory.ryczalt.persistence;

import java.time.Instant;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Stores explicit calculator output without coupling persistence to calculator result classes. */
@Repository
public class RyczaltCalculationPersistenceAdapter {
  private final RyczaltCalculationJpaRepository calculations;

  public RyczaltCalculationPersistenceAdapter(RyczaltCalculationJpaRepository calculations) {
    this.calculations = calculations;
  }

  @Transactional
  public RyczaltCalculationEntity saveCurrent(
      RyczaltPeriodEntity period,
      long profileId,
      CalculationType type,
      String resultJson,
      String inputFingerprint,
      String ruleVersion,
      String calculatorVersion) {
    RyczaltCalculationEntity previous =
        calculations
            .findByProfileIdAndPeriodIdAndTypeAndCurrentTrue(profileId, period.id(), type)
            .orElse(null);
    if (previous != null) {
      previous.markStale();
      calculations.save(previous);
    }
    int revision =
        calculations
            .findTopByProfileIdAndPeriodIdAndTypeOrderByRevisionDesc(profileId, period.id(), type)
            .map(existing -> existing.getRevision() + 1)
            .orElse(1);
    RyczaltCalculationEntity entity =
        new RyczaltCalculationEntity(
            period,
            profileId,
            type,
            CalculationStatus.CALCULATED,
            resultJson,
            inputFingerprint,
            ruleVersion,
            calculatorVersion,
            Instant.now(),
            revision,
            true);
    return calculations.save(entity);
  }
}
