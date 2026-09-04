package com.smartbox.investory.investment.infrastructure.persistence.portfolio;

import com.smartbox.investory.investment.infrastructure.persistence.ReadOnlyRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PortfolioMonthlyPerformanceRepository
    extends ReadOnlyRepository<PortfolioMonthlyPerformanceEntity, PortfolioMonthlyPerformanceId> {

  List<PortfolioMonthlyPerformanceEntity> findAllByOrderByMonthAscPortfolioIdAsc();

  List<PortfolioMonthlyPerformanceEntity> findByMonthGreaterThanEqualOrderByMonthAscPortfolioIdAsc(
      LocalDate from);

  List<PortfolioMonthlyPerformanceEntity> findByMonthLessThanEqualOrderByMonthAscPortfolioIdAsc(
      LocalDate to);

  List<PortfolioMonthlyPerformanceEntity> findByMonthBetweenOrderByMonthAscPortfolioIdAsc(
      LocalDate from, LocalDate to);

  List<PortfolioMonthlyPerformanceEntity> findByPortfolioIdOrderByMonthAsc(Long portfolioId);

  @Query(
      "select distinct p.baseCurrency from PortfolioMonthlyPerformanceEntity p "
          + "where p.portfolioId = :portfolioId")
  List<CurrencyType> findCurrenciesByPortfolioId(@Param("portfolioId") Long portfolioId);

  List<PortfolioMonthlyPerformanceEntity> findByPortfolioIdAndMonthGreaterThanEqualOrderByMonthAsc(
      Long portfolioId, LocalDate from);

  List<PortfolioMonthlyPerformanceEntity> findByPortfolioIdAndMonthLessThanEqualOrderByMonthAsc(
      Long portfolioId, LocalDate to);

  List<PortfolioMonthlyPerformanceEntity> findByPortfolioIdAndMonthBetweenOrderByMonthAsc(
      Long portfolioId, LocalDate from, LocalDate to);

  @Query(
      "select coalesce(sum(p.profit), 0) from PortfolioMonthlyPerformanceEntity p "
          + "where p.portfolioId = :portfolioId")
  BigDecimal sumProfitByPortfolioId(@Param("portfolioId") Long portfolioId);
}
