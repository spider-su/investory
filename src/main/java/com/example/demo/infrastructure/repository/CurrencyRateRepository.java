package com.example.demo.infrastructure.repository;

import com.example.demo.infrastructure.CurrencyType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CurrencyRateRepository extends JpaRepository<CurrencyRate, Long> {

    List<CurrencyRate> findAllByOrderByBaseAscToCurrencyAscMonthStartAsc();

    Optional<CurrencyRate> findByMonthStartAndBaseAndToCurrency(LocalDate monthStart, CurrencyType base, CurrencyType toCurrency);
}
