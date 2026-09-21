package com.smartbox.investory.ryczalt.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface RyczaltInvoiceCandidateJpaRepository
    extends JpaRepository<RyczaltInvoiceCandidateEntity, Long> {
  Optional<RyczaltInvoiceCandidateEntity> findByProfileIdAndCandidateKey(long profileId, UUID key);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<RyczaltInvoiceCandidateEntity> findLockedByProfileIdAndCandidateKey(
      long profileId, UUID key);

  Optional<RyczaltInvoiceCandidateEntity> findByProfileIdAndSourceTypeAndSourceExternalId(
      long profileId, String source, String externalId);
}
