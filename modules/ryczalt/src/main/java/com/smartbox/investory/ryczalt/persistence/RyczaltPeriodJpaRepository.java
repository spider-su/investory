package com.smartbox.investory.ryczalt.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface RyczaltPeriodJpaRepository extends JpaRepository<RyczaltPeriodEntity, Long> {
  @Query("select distinct p.profileId from RyczaltPeriodEntity p")
  List<Long> findDistinctProfileIds();

  Optional<RyczaltPeriodEntity> findByProfileIdAndYearAndMonth(long profileId, int year, int month);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select p from RyczaltPeriodEntity p
      where p.profileId = :profileId and p.year = :year and p.month = :month
      """)
  Optional<RyczaltPeriodEntity> findLocked(long profileId, int year, int month);

  List<RyczaltPeriodEntity> findByProfileIdOrderByYearDescMonthDesc(long profileId);
}
