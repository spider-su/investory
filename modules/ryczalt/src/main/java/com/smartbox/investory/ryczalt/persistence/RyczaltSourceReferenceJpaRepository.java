package com.smartbox.investory.ryczalt.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RyczaltSourceReferenceJpaRepository
    extends JpaRepository<RyczaltSourceReferenceEntity, Long> {
  Optional<RyczaltSourceReferenceEntity> findByProfileIdAndEntityTypeAndSourceAndExternalId(
      long profileId, String entityType, String source, String externalId);

  List<RyczaltSourceReferenceEntity> findByProfileIdAndEntityTypeAndEntityIdIn(
      long profileId, String entityType, Collection<Long> entityIds);
}
