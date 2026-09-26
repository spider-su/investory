package com.smartbox.investory.ryczalt.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface RyczaltObligationJpaRepository
    extends JpaRepository<RyczaltObligationEntity, Long> {
  List<RyczaltObligationEntity> findByProfileIdAndPeriodIdOrderByTypeAsc(
      long profileId, long periodId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from RyczaltObligationEntity o where o.id = :id")
  Optional<RyczaltObligationEntity> findLockedById(long id);
}
