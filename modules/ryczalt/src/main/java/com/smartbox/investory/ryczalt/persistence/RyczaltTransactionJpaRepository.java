package com.smartbox.investory.ryczalt.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface RyczaltTransactionJpaRepository
    extends JpaRepository<RyczaltTransactionEntity, Long> {
  List<RyczaltTransactionEntity> findByProfileIdAndPeriodIdOrderByBookingDateAscIdAsc(
      long profileId, long periodId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from RyczaltTransactionEntity t where t.id = :id")
  Optional<RyczaltTransactionEntity> findLockedById(long id);
}
