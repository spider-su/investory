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

public interface RyczaltFxRateJpaRepository extends JpaRepository<RyczaltFxRateEntity, Long> {
  Optional<RyczaltFxRateEntity> findByProviderAndCurrencyAndEffectiveDate(
      String provider, CurrencyType currency, LocalDate effectiveDate);

  Optional<RyczaltFxRateEntity>
      findTopByProviderAndCurrencyAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
          String provider, CurrencyType currency, LocalDate effectiveDate);

  @Modifying
  @Query(
      value =
          """
          insert into investory.ryczalt_fx_rate
              (currency, effective_date, rate, provider, provider_reference, fetched_at)
          values (:currency, :effectiveDate, :rate, :provider, :providerReference, :fetchedAt)
          on conflict (provider, currency, effective_date) do nothing
          """,
      nativeQuery = true)
  int insertIfAbsent(
      @Param("currency") String currency,
      @Param("effectiveDate") LocalDate effectiveDate,
      @Param("rate") BigDecimal rate,
      @Param("provider") String provider,
      @Param("providerReference") String providerReference,
      @Param("fetchedAt") Instant fetchedAt);
}
