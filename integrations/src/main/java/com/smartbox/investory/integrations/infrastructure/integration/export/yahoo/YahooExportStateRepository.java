package com.smartbox.investory.integrations.infrastructure.integration.export.yahoo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface YahooExportStateRepository
    extends JpaRepository<YahooExportStateEntity, Integer> {}
