package com.smartbox.investory.ryczalt.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RyczaltObligationJpaRepository
    extends JpaRepository<RyczaltObligationEntity, Long> {
  List<RyczaltObligationEntity> findByProfileIdAndPeriodIdOrderByTypeAsc(
      long profileId, long periodId);
}
