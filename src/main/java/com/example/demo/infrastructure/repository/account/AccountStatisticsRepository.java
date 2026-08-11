package com.example.demo.infrastructure.repository.account;

import com.example.demo.infrastructure.repository.ReadOnlyRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountStatisticsRepository extends ReadOnlyRepository<AccountStatistics, Long> {}
