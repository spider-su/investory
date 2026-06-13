package com.example.demo.data.repository;

import com.example.demo.data.CurrencyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

@Repository
public interface OpenPositionHistoryRepository extends JpaRepository<OpenPositionHistory, Long> {
    @Query("SELECT o FROM OpenPositionHistory o WHERE o.date > :date")
    List<OpenPositionHistory> findAllAfterDate(@Param("date") ZonedDateTime date);
}
