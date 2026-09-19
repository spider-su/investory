package com.smartbox.investory.ryczalt.persistence;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RyczaltCounterpartyRuleJpaRepository
    extends JpaRepository<RyczaltCounterpartyRuleEntity, Long> {
  List<RyczaltCounterpartyRuleEntity> findByProfileIdAndCounterpartyIdOrderByName(
      long profileId, long counterpartyId);

  Optional<RyczaltCounterpartyRuleEntity> findByIdAndProfileIdAndCounterpartyId(
      long id, long profileId, long counterpartyId);

  long countByProfileIdAndCounterpartyId(long profileId, long counterpartyId);
}
