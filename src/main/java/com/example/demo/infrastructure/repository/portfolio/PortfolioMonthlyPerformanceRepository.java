package com.example.demo.infrastructure.repository.portfolio;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PortfolioMonthlyPerformanceRepository
    extends JpaRepository<PortfolioMonthlyPerformance, PortfolioMonthlyPerformanceId> {

  List<PortfolioMonthlyPerformance> findAllByOrderByMonthAscPortfolioIdAsc();
}
