package com.example.demo.infrastructure.repository;

import com.example.demo.infrastructure.CurrencyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CurrencyRateRepository extends JpaRepository<CurrencyRate, Long> {
    @Query("SELECT c FROM CurrencyRate c WHERE c.base = :base AND c.toCurrency = :toCurrency")
    Optional<CurrencyRate> findByBaseAndToCurrency(@Param("base") CurrencyType base, @Param("toCurrency") CurrencyType toCurrency);
}
