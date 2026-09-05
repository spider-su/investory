package com.smartbox.investory.investment.infrastructure.persistence.portfolio;

import com.smartbox.investory.investment.infrastructure.persistence.ReadOnlyRepository;
import java.time.LocalDate;
import java.util.List;
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

  List<PortfolioMonthlyPerformanceEntity> findByPortfolioIdAndMonthGreaterThanEqualOrderByMonthAsc(
      Long portfolioId, LocalDate from);

  List<PortfolioMonthlyPerformanceEntity> findByPortfolioIdAndMonthLessThanEqualOrderByMonthAsc(
      Long portfolioId, LocalDate to);

  List<PortfolioMonthlyPerformanceEntity> findByPortfolioIdAndMonthBetweenOrderByMonthAsc(
      Long portfolioId, LocalDate from, LocalDate to);
}
