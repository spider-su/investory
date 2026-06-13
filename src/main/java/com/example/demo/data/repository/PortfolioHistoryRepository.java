package com.example.demo.data.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioHistoryRepository extends JpaRepository<PortfolioHistory, Long> {
    @Query("SELECT o FROM PortfolioHistory o WHERE o.date > :date")
    Optional<PortfolioHistory> findOneAfterDate(@Param("date") ZonedDateTime date);
}
