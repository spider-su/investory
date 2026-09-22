package com.smartbox.investory.ryczalt.application;

import com.smartbox.investory.ryczalt.persistence.RyczaltInvoiceCandidateEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltInvoiceCandidateJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltSourceReferenceEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltSourceReferenceJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Atomically persists a candidate and its provenance in a fresh transaction. */
@Service
public class RyczaltInvoiceCandidatePersistenceService {
  private final RyczaltInvoiceCandidateJpaRepository candidates;
  private final RyczaltSourceReferenceJpaRepository sources;

  public RyczaltInvoiceCandidatePersistenceService(
      RyczaltInvoiceCandidateJpaRepository candidates,
      RyczaltSourceReferenceJpaRepository sources) {
    this.candidates = candidates;
    this.sources = sources;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public RyczaltInvoiceCandidateEntity persist(
      RyczaltInvoiceCandidateEntity candidate, String metadata) {
    var saved = candidates.saveAndFlush(candidate);
    sources.save(
        new RyczaltSourceReferenceEntity(
            saved.getProfileId(),
            "INVOICE_CANDIDATE",
            saved.getId(),
            saved.getSourceType(),
            saved.getSourceExternalId(),
            metadata));
    sources.flush();
    return saved;
  }
}
