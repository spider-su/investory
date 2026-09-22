package com.smartbox.investory.ryczalt.persistence;

import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RyczaltInvoicePaymentMatchJpaRepository
    extends JpaRepository<RyczaltInvoicePaymentMatchEntity, Long> {
  @Query(
      "select coalesce(sum(m.matchedAmount), 0) from RyczaltInvoicePaymentMatchEntity m where m.profileId=:profileId and m.transactionId=:transactionId")
  BigDecimal allocatedForTransaction(
      @Param("profileId") long profileId, @Param("transactionId") long transactionId);
}
