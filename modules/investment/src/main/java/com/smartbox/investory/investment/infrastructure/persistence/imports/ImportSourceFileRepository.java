package com.smartbox.investory.investment.infrastructure.persistence.imports;

import com.smartbox.investory.investment.imports.BrokerType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportSourceFileRepository extends JpaRepository<ImportSourceFileEntity, Long> {
  Optional<ImportSourceFileEntity> findByBrokerAndFileSha256(BrokerType broker, String fileSha256);
}
