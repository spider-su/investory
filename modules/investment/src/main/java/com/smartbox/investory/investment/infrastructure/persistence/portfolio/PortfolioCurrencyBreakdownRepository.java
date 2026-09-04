package com.smartbox.investory.investment.infrastructure.persistence.portfolio;

import com.smartbox.investory.investment.infrastructure.persistence.ReadOnlyRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PortfolioCurrencyBreakdownRepository
    extends ReadOnlyRepository<PortfolioCurrencyBreakdownEntity, PortfolioCurrencyBreakdownId> {
  java.util.List<PortfolioCurrencyBreakdownEntity> findAllByPortfolioId(Long portfolioId);

  java.util.List<PortfolioCurrencyBreakdownEntity> findAllByMetricType(String metricType);

  java.util.List<PortfolioCurrencyBreakdownEntity> findAllByPortfolioIdAndMetricType(
      Long portfolioId, String metricType);
}
