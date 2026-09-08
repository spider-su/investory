package com.smartbox.investory.investment.valuation.fx.persistence;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyFxRateRepository extends JpaRepository<DailyFxRateEntity, Long> {
  Optional<DailyFxRateEntity> findByRateDateAndBaseAndToCurrency(
      LocalDate rateDate, CurrencyType base, CurrencyType toCurrency);
}
