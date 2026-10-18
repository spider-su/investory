package com.smartbox.investory.investment.infrastructure.persistence.portfolio;

import com.smartbox.investory.investment.infrastructure.persistence.ReadOnlyRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public interface PortfolioFallbackReconciliationRepository
    extends ReadOnlyRepository<PortfolioFallbackReconciliationEntity, Long> {
  List<FallbackReconciliationStatusRow> findAllByPortfolioId(Long portfolioId);

  interface FallbackReconciliationStatusRow {
    String getFallbackReconciliationStatus();

    java.math.BigDecimal getRealizedProfitDifference();

    java.math.BigDecimal getUnrealizedProfitDifference();

    java.math.BigDecimal getDividendsDifference();

    java.math.BigDecimal getInterestDifference();
  }
}
