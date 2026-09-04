package com.smartbox.investory.investment.infrastructure.persistence.account;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountDailyRepository extends JpaRepository<AccountDailyEntity, Long> {

  List<AccountDailyEntity> findByDateOrderByAccountIdAsc(LocalDate date);

  List<AccountDailyEntity> findByDateAndAccountIdInOrderByAccountIdAsc(
      LocalDate date, java.util.Collection<Long> accountIds);

  List<AccountDailyEntity> findByDateGreaterThanEqualOrderByDateAscAccountIdAsc(LocalDate from);

  List<AccountDailyEntity> findByDateGreaterThanEqualAndAccountIdInOrderByDateAscAccountIdAsc(
      LocalDate from, java.util.Collection<Long> accountIds);

  @Query(
      value =
          """
          SELECT
              snapshot_date AS "date",
              equity AS "endValue",
              deposits AS contributions,
              withdrawals AS withdrawals
          FROM investory.app_v_portfolio_performance_daily
          WHERE portfolio_id = :portfolioId
            AND snapshot_date BETWEEN :from AND :to
          ORDER BY snapshot_date
          """,
      nativeQuery = true)
  List<PortfolioPerformanceDailyRow> findPortfolioPerformanceDaily(
      @Param("portfolioId") Long portfolioId,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to);

  @Modifying
  @Query(
      """
      DELETE FROM AccountDailyEntity row
      WHERE row.accountId = :accountId AND row.date >= :date
      """)
  void deleteByAccountIdAndDateGreaterThanEqual(
      @Param("accountId") Long accountId, @Param("date") LocalDate date);

  /** Base-currency, non-cash-only daily boundary from the canonical performance projection. */
  interface PortfolioPerformanceDailyRow {
    LocalDate getDate();

    BigDecimal getEndValue();

    BigDecimal getContributions();

    BigDecimal getWithdrawals();
  }
}
