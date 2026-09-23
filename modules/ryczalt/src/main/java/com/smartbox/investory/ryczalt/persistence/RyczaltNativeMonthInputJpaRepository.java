package com.smartbox.investory.ryczalt.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RyczaltNativeMonthInputJpaRepository
    extends JpaRepository<RyczaltNativeMonthInputEntity, Long> {
  Optional<RyczaltNativeMonthInputEntity> findByProfileIdAndYearAndMonth(
      long profileId, int year, int month);
}
