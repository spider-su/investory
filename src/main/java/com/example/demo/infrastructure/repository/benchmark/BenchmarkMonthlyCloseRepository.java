package com.example.demo.infrastructure.repository.benchmark;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BenchmarkMonthlyCloseRepository extends JpaRepository<BenchmarkMonthlyClose, Long> {

  List<BenchmarkMonthlyClose> findBySymbolOrderByMonthDateAsc(String symbol);
}
