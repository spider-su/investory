package com.smartbox.investory.ryczalt.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RyczaltInvoiceCandidateJpaRepository
    extends JpaRepository<RyczaltInvoiceCandidateEntity, Long> {
  Optional<RyczaltInvoiceCandidateEntity> findByProfileIdAndCandidateKey(long profileId, UUID key);

  Optional<RyczaltInvoiceCandidateEntity> findByProfileIdAndSourceTypeAndSourceExternalId(
      long profileId, String source, String externalId);
}
