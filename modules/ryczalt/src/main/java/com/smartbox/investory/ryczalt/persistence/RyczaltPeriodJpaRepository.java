package com.smartbox.investory.ryczalt.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RyczaltPeriodJpaRepository extends JpaRepository<RyczaltPeriodEntity, Long> {
  @Query("select distinct p.profileId from RyczaltPeriodEntity p")
  List<Long> findDistinctProfileIds();

  Optional<RyczaltPeriodEntity> findByProfileIdAndYearAndMonth(long profileId, int year, int month);

  List<RyczaltPeriodEntity> findByProfileIdOrderByYearDescMonthDesc(long profileId);
}
