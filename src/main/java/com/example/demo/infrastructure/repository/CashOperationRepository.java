package com.example.demo.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CashOperationRepository extends JpaRepository<CashOperation, Long> {
    @Query("SELECT DISTINCT c.symbol FROM CashOperation c WHERE c.symbol IS NOT NULL AND c.symbol <> ''")
    List<String> findDistinctNonEmptySymbols();
}
