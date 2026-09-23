package com.smartbox.investory.ryczalt.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RyczaltInvoiceJpaRepository extends JpaRepository<RyczaltInvoiceEntity, Long> {
  @EntityGraph(attributePaths = "counterparty")
  List<RyczaltInvoiceEntity> findByProfileIdOrderByAccountingDateAscIdAsc(long profileId);

  java.util.Optional<RyczaltInvoiceEntity> findByIdAndProfileId(long id, long profileId);

  @EntityGraph(attributePaths = "counterparty")
  List<RyczaltInvoiceEntity> findByProfileIdAndCounterparty_IdOrderByAccountingDateAscIdAsc(
      long profileId, long counterpartyId);

  @EntityGraph(attributePaths = "counterparty")
  List<RyczaltInvoiceEntity> findByProfileIdAndPeriodIdOrderByAccountingDateAscIdAsc(
      long profileId, long periodId);

  @EntityGraph(attributePaths = "counterparty")
  List<RyczaltInvoiceEntity>
      findByProfileIdAndPeriodIdAndCounterparty_IdOrderByAccountingDateAscIdAsc(
          long profileId, long periodId, long counterpartyId);

  long countByProfileIdAndCounterparty_Id(long profileId, long counterpartyId);
}
