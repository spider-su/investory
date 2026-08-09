package com.example.demo.infrastructure.repository;

import com.example.demo.infrastructure.CurrencyType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;

@Repository
public interface CurrencyRateRepository extends JpaRepository<CurrencyRate, Long> {

  List<CurrencyRate> findAllByOrderByBaseAscToCurrencyAscRateDateAsc();

  Optional<CurrencyRate> findFirstByRateDateAndBaseAndToCurrencyAndSourceAndMethod(
      LocalDate rateDate, CurrencyType base, CurrencyType toCurrency, String source, String method);

  Optional<CurrencyRate> findBySourceReference(String sourceReference);

  @Query("select r from CurrencyRate r where r.rateDate = :rateDate and r.base = :base and r.toCurrency = :toCurrency")
  Optional<CurrencyRate> findByMonthStartAndBaseAndToCurrency(
      @org.springframework.data.repository.query.Param("rateDate") LocalDate rateDate,
      @org.springframework.data.repository.query.Param("base") CurrencyType base,
      @org.springframework.data.repository.query.Param("toCurrency") CurrencyType toCurrency);

  List<CurrencyRate> findAllByOrderByBaseAscToCurrencyAscMonthStartAsc();
}
