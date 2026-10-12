package com.smartbox.investory.integrations.export.yahoo.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface YahooExportStateRepository extends JpaRepository<YahooExportStateEntity, Long> {
  Optional<YahooExportStateEntity> findFirstByOrderByExportedAtDesc();
}
