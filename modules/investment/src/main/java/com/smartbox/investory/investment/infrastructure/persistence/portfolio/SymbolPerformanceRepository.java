package com.smartbox.investory.investment.infrastructure.persistence.portfolio;

import com.smartbox.investory.investment.infrastructure.persistence.ReadOnlyRepository;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SymbolPerformanceRepository
    extends ReadOnlyRepository<SymbolPerformanceEntity, SymbolPerformanceId> {

  @Query("SELECT row FROM SymbolPerformanceEntity row WHERE row.symbol = :symbol")
  List<SymbolPerformanceEntity> findAllBySymbol(@Param("symbol") String symbol);
}
