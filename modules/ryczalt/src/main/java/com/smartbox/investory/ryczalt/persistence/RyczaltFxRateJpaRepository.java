package com.smartbox.investory.ryczalt.persistence;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RyczaltFxRateJpaRepository extends JpaRepository<RyczaltFxRateEntity, Long> {
  Optional<RyczaltFxRateEntity> findByProviderAndCurrencyAndEffectiveDate(
      String provider, String currency, LocalDate effectiveDate);
}
