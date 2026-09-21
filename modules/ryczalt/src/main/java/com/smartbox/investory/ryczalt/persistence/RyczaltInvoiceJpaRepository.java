package com.smartbox.investory.ryczalt.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RyczaltInvoiceJpaRepository extends JpaRepository<RyczaltInvoiceEntity, Long> {
  List<RyczaltInvoiceEntity> findByProfileIdOrderByAccountingDateAscIdAsc(long profileId);

  java.util.Optional<RyczaltInvoiceEntity> findByIdAndProfileId(long id, long profileId);

  List<RyczaltInvoiceEntity> findByProfileIdAndCounterparty_IdOrderByAccountingDateAscIdAsc(
      long profileId, long counterpartyId);

  List<RyczaltInvoiceEntity> findByProfileIdAndPeriodIdOrderByAccountingDateAscIdAsc(
      long profileId, long periodId);

  List<RyczaltInvoiceEntity>
      findByProfileIdAndPeriodIdAndCounterparty_IdOrderByAccountingDateAscIdAsc(
          long profileId, long periodId, long counterpartyId);

  long countByProfileIdAndCounterparty_Id(long profileId, long counterpartyId);
}
