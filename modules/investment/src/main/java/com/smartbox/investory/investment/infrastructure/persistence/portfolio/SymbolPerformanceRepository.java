package com.smartbox.investory.investment.infrastructure.persistence.portfolio;

import com.smartbox.investory.investment.infrastructure.persistence.ReadOnlyRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public interface SymbolPerformanceRepository
    extends ReadOnlyRepository<SymbolPerformanceEntity, SymbolPerformanceId> {

  List<SymbolPerformanceEntity> findAllByPortfolioId(Long portfolioId);

  List<SymbolPerformanceEntity> findAllByPortfolioIdAndSymbol(Long portfolioId, String symbol);
}
