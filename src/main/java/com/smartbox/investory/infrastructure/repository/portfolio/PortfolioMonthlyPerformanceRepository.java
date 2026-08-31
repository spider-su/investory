package com.smartbox.investory.infrastructure.repository.portfolio;

import com.smartbox.investory.infrastructure.repository.ReadOnlyRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public interface PortfolioMonthlyPerformanceRepository
    extends ReadOnlyRepository<PortfolioMonthlyPerformance, PortfolioMonthlyPerformanceId> {

  List<PortfolioMonthlyPerformance> findAllByOrderByMonthAscPortfolioIdAsc();

  List<PortfolioMonthlyPerformance> findByPortfolioIdAndMonthBetweenOrderByMonthAsc(
      Long portfolioId, LocalDate from, LocalDate to);
}
