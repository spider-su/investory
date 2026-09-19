package com.smartbox.investory.ryczalt.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RyczaltTransactionJpaRepository
    extends JpaRepository<RyczaltTransactionEntity, Long> {
  List<RyczaltTransactionEntity> findByProfileIdAndPeriodIdOrderByBookingDateAscIdAsc(
      long profileId, long periodId);
}
