package com.example.demo.infrastructure.repository.portfolio;

import java.util.List;
import com.example.demo.infrastructure.repository.ReadOnlyRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PortfolioMonthlyPerformanceRepository
    extends ReadOnlyRepository<PortfolioMonthlyPerformance, PortfolioMonthlyPerformanceId> {

  List<PortfolioMonthlyPerformance> findAllByOrderByMonthAscPortfolioIdAsc();
}
