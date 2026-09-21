package com.smartbox.investory.ryczalt.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RyczaltInvoiceJpaRepository extends JpaRepository<RyczaltInvoiceEntity, Long> {
  List<RyczaltInvoiceEntity> findByProfileIdAndPeriodIdOrderByAccountingDateAscIdAsc(
      long profileId, long periodId);

  List<RyczaltInvoiceEntity>
      findByProfileIdAndPeriodIdAndCounterparty_IdOrderByAccountingDateAscIdAsc(
          long profileId, long periodId, long counterpartyId);
}
