package com.smartbox.investory.investment.infrastructure.persistence.portfolio;

import com.smartbox.investory.investment.infrastructure.persistence.ReadOnlyRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public interface PortfolioMonthlySummaryRepository
    extends ReadOnlyRepository<PortfolioMonthlySummaryEntity, String> {
  List<PortfolioMonthlySummaryEntity> findByPortfolioIdOrderByMonthAsc(Long portfolioId);
}
