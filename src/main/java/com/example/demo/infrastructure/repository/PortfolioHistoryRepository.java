package com.example.demo.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Optional;

@Repository
public interface PortfolioHistoryRepository extends JpaRepository<PortfolioHistory, Long> {

    // Tolerates multiple same-day rows by returning the most recent one.
    Optional<PortfolioHistory> findFirstByDateAfterOrderByIdDesc(ZonedDateTime date);

    default Optional<PortfolioHistory> findOneAfterDate(ZonedDateTime date) {
        return findFirstByDateAfterOrderByIdDesc(date);
    }
}
