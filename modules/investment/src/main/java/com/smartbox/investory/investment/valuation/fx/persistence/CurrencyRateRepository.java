package com.smartbox.investory.investment.valuation.fx.persistence;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CurrencyRateRepository extends JpaRepository<CurrencyRateEntity, Long> {

  @Query(
      value =
          "SELECT * FROM investory.resolve_fx_rate(:valuationDate, :sourceCurrency, :targetCurrency)",
      nativeQuery = true)
  Optional<FxRateResolutionRow> resolveFxRate(
      @Param("valuationDate") LocalDate valuationDate,
      @Param("sourceCurrency") String sourceCurrency,
      @Param("targetCurrency") String targetCurrency);

  @Query(
      value =
          """
          SELECT resolved.*
          FROM (VALUES ('PLN'), ('USD'), ('EUR')) AS sources(currency)
          CROSS JOIN (VALUES ('PLN'), ('USD'), ('EUR')) AS targets(currency)
          CROSS JOIN LATERAL investory.resolve_fx_rate(
              :valuationDate, sources.currency, targets.currency) AS resolved
          WHERE sources.currency <> targets.currency
          """,
      nativeQuery = true)
  List<FxRateResolutionRow> resolveFxRatesForDate(@Param("valuationDate") LocalDate valuationDate);

  @Query(
      value =
          """
          SELECT resolved.*, CAST(gs AS date) AS valuation_date
          FROM generate_series(
                   CAST(:startDate AS date), CAST(:endDate AS date), INTERVAL '1 day') AS gs
          CROSS JOIN (VALUES ('PLN'), ('USD'), ('EUR')) AS sources(currency)
          CROSS JOIN (VALUES ('PLN'), ('USD'), ('EUR')) AS targets(currency)
          CROSS JOIN LATERAL investory.resolve_fx_rate(
              CAST(gs AS date), sources.currency, targets.currency) AS resolved
          WHERE sources.currency <> targets.currency
          """,
      nativeQuery = true)
  List<FxRateResolutionRow> resolveFxRatesForDateRange(
      @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

  @Modifying
  @Query(
      value =
          """
          UPDATE investory.fx_configuration
          SET config_value = to_char(CAST(:firstSupportedDate AS timestamp), 'YYYY-MM-DD')
          WHERE config_key = 'daily_history_start'
            AND (
              CAST(:firstSupportedDate AS date) = DATE '9999-12-31'
              OR investory.fx_daily_coverage_supported(CAST(:firstSupportedDate AS date))
            )
          """,
      nativeQuery = true)
  int setDailyHistoryStartIfSupported(@Param("firstSupportedDate") LocalDate firstSupportedDate);

  Optional<CurrencyRateEntity> findFirstByRateDateAndBaseAndToCurrencyAndSourceAndMethod(
      LocalDate rateDate, CurrencyType base, CurrencyType toCurrency, String source, String method);

  Optional<CurrencyRateEntity>
      findByRateDateAndBaseAndToCurrencyAndSourceAndMethodAndSourceReference(
          LocalDate rateDate,
          CurrencyType base,
          CurrencyType toCurrency,
          String source,
          String method,
          String sourceReference);

  @Query(
      value =
          """
          SELECT *
          FROM investory.exchange_rates
          WHERE method IN ('XTB_EXECUTION', 'IBKR_EXECUTION')
            AND rate_date = :transactionDate
            AND observed_at <= :transactionTime
            AND ((base = :sourceCurrency AND to_currency = :targetCurrency)
              OR (base = :targetCurrency AND to_currency = :sourceCurrency))
          ORDER BY CASE WHEN base = :sourceCurrency THEN 0 ELSE 1 END,
                   observed_at DESC NULLS LAST,
                   source_reference ASC NULLS LAST
          LIMIT 1
          """,
      nativeQuery = true)
  Optional<CurrencyRateEntity> findExecutionRateAtOrBefore(
      @Param("transactionTime") ZonedDateTime transactionTime,
      @Param("transactionDate") LocalDate transactionDate,
      @Param("sourceCurrency") String sourceCurrency,
      @Param("targetCurrency") String targetCurrency);
}
