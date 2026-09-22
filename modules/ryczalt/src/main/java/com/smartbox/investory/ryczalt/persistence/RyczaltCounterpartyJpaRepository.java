package com.smartbox.investory.ryczalt.persistence;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RyczaltCounterpartyJpaRepository
    extends JpaRepository<RyczaltCounterpartyEntity, Long> {
  @Query(
      "select new com.smartbox.investory.ryczalt.domain.Counterparty(c.id, c.profileId, c.taxIdentifier, c.country, c.legalName, c.alias, c.bankAccount, "
          + "(select count(r) from RyczaltCounterpartyRuleEntity r where r.profileId = c.profileId and r.counterparty.id = c.id), "
          + "(select count(i) from RyczaltInvoiceEntity i where i.profileId = c.profileId and i.counterparty.id = c.id)) "
          + "from RyczaltCounterpartyEntity c where c.profileId = :profileId order by c.legalName")
  List<com.smartbox.investory.ryczalt.domain.Counterparty> findSummaries(
      @Param("profileId") long profileId);

  Optional<RyczaltCounterpartyEntity> findByIdAndProfileId(long id, long profileId);

  Optional<RyczaltCounterpartyEntity> findByProfileIdAndTaxIdentifierAndCountry(
      long profileId, String taxIdentifier, String country);
}
