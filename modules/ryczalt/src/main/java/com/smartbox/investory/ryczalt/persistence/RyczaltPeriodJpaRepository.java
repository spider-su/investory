package com.smartbox.investory.ryczalt.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RyczaltPeriodJpaRepository extends JpaRepository<RyczaltPeriodEntity, Long> {
  Optional<RyczaltPeriodEntity> findByProfileIdAndYearAndMonth(long profileId, int year, int month);

  List<RyczaltPeriodEntity> findByProfileIdOrderByYearDescMonthDesc(long profileId);
}
