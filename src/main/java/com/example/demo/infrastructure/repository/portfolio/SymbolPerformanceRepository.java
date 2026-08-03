package com.example.demo.infrastructure.repository.portfolio;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SymbolPerformanceRepository
    extends JpaRepository<SymbolPerformance, SymbolPerformanceId> {

  @Query("SELECT row FROM SymbolPerformance row WHERE row.symbol = :symbol")
  List<SymbolPerformance> findAllBySymbol(@Param("symbol") String symbol);

  default void deleteAllRows() {}
}
