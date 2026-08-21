package com.smartbox.investory.investment.infrastructure.persistence.portfolio;

import com.smartbox.investory.investment.infrastructure.persistence.ReadOnlyRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public interface PortfolioMonthlyPerformanceRepository
    extends ReadOnlyRepository<PortfolioMonthlyPerformanceEntity, PortfolioMonthlyPerformanceId> {

  List<PortfolioMonthlyPerformanceEntity> findAllByOrderByMonthAscPortfolioIdAsc();

  List<PortfolioMonthlyPerformanceEntity> findByPortfolioIdAndMonthBetweenOrderByMonthAsc(
      Long portfolioId, LocalDate from, LocalDate to);
}
