package com.smartbox.investory.investment.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface YahooExportStateRepository extends JpaRepository<YahooExportStateEntity, Integer> {
  default Optional<YahooExportStateEntity> singleton() {
    return findById(1);
  }
}
