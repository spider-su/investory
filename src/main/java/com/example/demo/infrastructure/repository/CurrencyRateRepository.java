package com.example.demo.infrastructure.repository;

import com.example.demo.infrastructure.CurrencyType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CurrencyRateRepository extends JpaRepository<CurrencyRate, Long> {

  List<CurrencyRate> findAllByOrderByBaseAscToCurrencyAscRateDateAsc();

  Optional<CurrencyRate> findFirstByRateDateAndBaseAndToCurrencyAndSourceAndMethod(
      LocalDate rateDate, CurrencyType base, CurrencyType toCurrency, String source, String method);

  Optional<CurrencyRate> findBySourceReference(String sourceReference);

  List<CurrencyRate> findAllByMethodInAndObservedAtIsNotNullOrderByObservedAtDesc(
      List<String> methods);

}
