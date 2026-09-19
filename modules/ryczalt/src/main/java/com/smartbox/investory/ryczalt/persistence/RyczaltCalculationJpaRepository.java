package com.smartbox.investory.ryczalt.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RyczaltCalculationJpaRepository
    extends JpaRepository<RyczaltCalculationEntity, Long> {
  Optional<RyczaltCalculationEntity> findByProfileIdAndPeriodIdAndType(
      long profileId, long periodId, CalculationType type);
}
