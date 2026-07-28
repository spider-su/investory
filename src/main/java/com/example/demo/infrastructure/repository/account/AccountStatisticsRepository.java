package com.example.demo.infrastructure.repository.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountStatisticsRepository extends JpaRepository<AccountStatistics, Long> {
}
