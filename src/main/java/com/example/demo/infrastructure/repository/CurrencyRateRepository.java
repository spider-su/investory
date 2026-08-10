package com.example.demo.infrastructure.repository;

import com.example.demo.infrastructure.CurrencyType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CurrencyRateRepository extends JpaRepository<CurrencyRate, Long> {

  @Query(value = "SELECT * FROM investory.resolve_fx_rate(:valuationDate, :sourceCurrency, :targetCurrency, :purpose)", nativeQuery = true)
  Optional<FxRateResolutionRow> resolveFxRate(
      @Param("valuationDate") LocalDate valuationDate,
      @Param("sourceCurrency") String sourceCurrency,
      @Param("targetCurrency") String targetCurrency,
      @Param("purpose") String purpose);

  List<CurrencyRate> findAllByOrderByBaseAscToCurrencyAscRateDateAsc();

  Optional<CurrencyRate> findFirstByRateDateAndBaseAndToCurrencyAndSourceAndMethod(
      LocalDate rateDate, CurrencyType base, CurrencyType toCurrency, String source, String method);

  Optional<CurrencyRate> findBySourceReference(String sourceReference);

}
