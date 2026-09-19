package com.smartbox.investory.ryczalt.persistence;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RyczaltFxRateJpaRepository extends JpaRepository<RyczaltFxRateEntity, Long> {
  Optional<RyczaltFxRateEntity> findByProviderAndCurrencyAndEffectiveDate(
      String provider, CurrencyType currency, LocalDate effectiveDate);

  Optional<RyczaltFxRateEntity>
      findTopByProviderAndCurrencyAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
          String provider, CurrencyType currency, LocalDate effectiveDate);
}
