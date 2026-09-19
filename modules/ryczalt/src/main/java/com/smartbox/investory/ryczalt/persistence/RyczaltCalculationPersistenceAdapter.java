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
    RyczaltCalculationEntity entity =
        new RyczaltCalculationEntity(
            period,
            profileId,
            type,
            CalculationStatus.CURRENT,
            resultJson,
            inputFingerprint,
            ruleVersion,
            calculatorVersion,
            Instant.now());
    return calculations.save(entity);
  }
}
