package com.smartbox.investory.infrastructure.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface YahooExportStateRepository extends JpaRepository<YahooExportState, Integer> {
  default Optional<YahooExportState> singleton() {
    return findById(1);
  }
}
