package com.smartbox.investory.infrastructure.repository.portfolio;

import com.smartbox.investory.infrastructure.repository.ReadOnlyRepository;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SymbolPerformanceRepository
    extends ReadOnlyRepository<SymbolPerformance, SymbolPerformanceId> {

  @Query("SELECT row FROM SymbolPerformance row WHERE row.symbol = :symbol")
  List<SymbolPerformance> findAllBySymbol(@Param("symbol") String symbol);
}
