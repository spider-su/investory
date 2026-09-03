package com.smartbox.investory.investment.infrastructure.persistence.portfolio;

import com.smartbox.investory.investment.infrastructure.persistence.ReadOnlyRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public interface PortfolioTaxYearRealizedRepository
    extends ReadOnlyRepository<PortfolioTaxYearRealizedEntity, String> {
  List<PortfolioTaxYearRealizedEntity> findByPortfolioIdOrderByTaxYearAsc(Long portfolioId);
}
