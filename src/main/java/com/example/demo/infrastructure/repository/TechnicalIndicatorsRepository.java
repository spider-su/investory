package com.example.demo.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TechnicalIndicatorsRepository extends JpaRepository<TechnicalIndicator, Long> {
}
