package com.smartbox.investory.ryczalt.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RyczaltCalculationJpaRepository
    extends JpaRepository<RyczaltCalculationEntity, Long> {
  Optional<RyczaltCalculationEntity> findByProfileIdAndPeriodIdAndType(
      long profileId, long periodId, CalculationType type);

  Optional<RyczaltCalculationEntity> findByProfileIdAndPeriodIdAndTypeAndCurrentTrue(
      long profileId, long periodId, CalculationType type);

  List<RyczaltCalculationEntity> findByProfileIdAndPeriodIdAndCurrentTrue(
      long profileId, long periodId);

  List<RyczaltCalculationEntity> findByProfileIdAndPeriodId(long profileId, long periodId);

  Optional<RyczaltCalculationEntity> findTopByProfileIdAndPeriodIdAndTypeOrderByRevisionDesc(
      long profileId, long periodId, CalculationType type);
}
