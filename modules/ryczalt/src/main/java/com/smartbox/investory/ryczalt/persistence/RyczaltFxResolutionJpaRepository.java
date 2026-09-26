package com.smartbox.investory.ryczalt.persistence;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RyczaltFxResolutionJpaRepository
    extends JpaRepository<RyczaltFxResolutionEntity, Long> {
  Optional<RyczaltFxResolutionEntity> findByProviderAndCurrencyAndRequestedDate(
      String provider, CurrencyType currency, LocalDate requestedDate);

  @Modifying
  @Query(
      value =
          """
          insert into investory.ryczalt_fx_resolution
              (currency, requested_date, effective_date, rate, provider, provider_reference, resolved_at)
          values (:currency, :requestedDate, :effectiveDate, :rate, :provider, :providerReference, :resolvedAt)
          on conflict (provider, currency, requested_date) do nothing
          """,
      nativeQuery = true)
  int insertIfAbsent(
      @Param("currency") String currency,
      @Param("requestedDate") LocalDate requestedDate,
      @Param("effectiveDate") LocalDate effectiveDate,
      @Param("rate") BigDecimal rate,
      @Param("provider") String provider,
      @Param("providerReference") String providerReference,
      @Param("resolvedAt") Instant resolvedAt);
}
