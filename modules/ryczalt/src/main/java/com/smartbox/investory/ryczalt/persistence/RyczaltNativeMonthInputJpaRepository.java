package com.smartbox.investory.ryczalt.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RyczaltNativeMonthInputJpaRepository
    extends JpaRepository<RyczaltNativeMonthInputEntity, Long> {
  Optional<RyczaltNativeMonthInputEntity> findByProfileIdAndYearAndMonth(
      long profileId, int year, int month);

  @Query(
      "select input from RyczaltNativeMonthInputEntity input "
          + "where input.profileId = :profileId "
          + "and (input.year < :year or (input.year = :year and input.month < :month)) "
          + "order by input.year desc, input.month desc")
  Optional<RyczaltNativeMonthInputEntity> findLatestBefore(
      @Param("profileId") long profileId, @Param("year") int year, @Param("month") int month);
}
