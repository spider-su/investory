package com.example.demo.data.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FundamentalIndicatorsRepository extends JpaRepository<FundamentalIndicator, Long> {
}
