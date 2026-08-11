package com.example.demo.infrastructure.repository;

import com.example.demo.infrastructure.CurrencyType;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
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

  @Modifying
  @Query(
      value = "UPDATE investory.fx_configuration SET config_value = to_char(CAST(:firstSupportedDate AS timestamp), 'YYYY-MM-DD') WHERE config_key = 'daily_history_start'",
      nativeQuery = true)
  void setDailyHistoryStart(@Param("firstSupportedDate") LocalDate firstSupportedDate);

  Optional<CurrencyRate> findFirstByRateDateAndBaseAndToCurrencyAndSourceAndMethod(
      LocalDate rateDate, CurrencyType base, CurrencyType toCurrency, String source, String method);

  Optional<CurrencyRate> findByRateDateAndBaseAndToCurrencyAndSourceAndMethodAndSourceReference(
      LocalDate rateDate,
      CurrencyType base,
      CurrencyType toCurrency,
      String source,
      String method,
      String sourceReference);

  @Query(
      value = """
          SELECT *
          FROM investory.exchange_rates
          WHERE method IN ('XTB_EXECUTION', 'IBKR_EXECUTION')
            AND rate_date = CAST(:transactionTime AS date)
            AND observed_at <= :transactionTime
            AND ((base = :sourceCurrency AND to_currency = :targetCurrency)
              OR (base = :targetCurrency AND to_currency = :sourceCurrency))
          ORDER BY CASE WHEN base = :sourceCurrency THEN 0 ELSE 1 END,
                   observed_at DESC NULLS LAST,
                   source_reference ASC NULLS LAST
          LIMIT 1
          """,
      nativeQuery = true)
  Optional<CurrencyRate> findExecutionRateAtOrBefore(
      @Param("transactionTime") ZonedDateTime transactionTime,
      @Param("sourceCurrency") String sourceCurrency,
      @Param("targetCurrency") String targetCurrency);

}
