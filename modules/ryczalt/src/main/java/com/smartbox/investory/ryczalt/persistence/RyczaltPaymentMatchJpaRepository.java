package com.smartbox.investory.ryczalt.persistence;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RyczaltPaymentMatchJpaRepository
    extends JpaRepository<RyczaltPaymentMatchEntity, Long> {
  List<RyczaltPaymentMatchEntity> findByProfileIdAndObligationId(long profileId, long obligationId);

  List<RyczaltPaymentMatchEntity> findByProfileIdAndTransactionId(
      long profileId, long transactionId);

  Optional<RyczaltPaymentMatchEntity> findByProfileIdAndObligationIdAndTransactionId(
      long profileId, long obligationId, long transactionId);

  @Query(
      "select coalesce(sum(m.matchedAmount), 0) from RyczaltPaymentMatchEntity m "
          + "where m.profileId = :profileId and m.obligation.id = :obligationId")
  BigDecimal allocatedForObligation(
      @Param("profileId") long profileId, @Param("obligationId") long obligationId);

  @Query(
      "select coalesce(sum(m.matchedAmount), 0) from RyczaltPaymentMatchEntity m "
          + "where m.profileId = :profileId and m.transaction.id = :transactionId")
  BigDecimal allocatedForTransaction(
      @Param("profileId") long profileId, @Param("transactionId") long transactionId);
}
