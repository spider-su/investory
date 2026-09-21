package com.smartbox.investory.ryczalt.persistence;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RyczaltCounterpartyJpaRepository
    extends JpaRepository<RyczaltCounterpartyEntity, Long> {
  List<RyczaltCounterpartyEntity> findByProfileIdOrderByLegalName(long profileId);

  Optional<RyczaltCounterpartyEntity> findByIdAndProfileId(long id, long profileId);

  Optional<RyczaltCounterpartyEntity> findByProfileIdAndTaxIdentifierAndCountry(
      long profileId, String taxIdentifier, String country);
}
