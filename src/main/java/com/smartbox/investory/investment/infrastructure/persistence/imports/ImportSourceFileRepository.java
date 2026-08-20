package com.smartbox.investory.investment.infrastructure.persistence.imports;

import com.smartbox.investory.infrastructure.BrokerType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportSourceFileRepository extends JpaRepository<ImportSourceFile, Long> {
  Optional<ImportSourceFile> findByBrokerAndFileSha256(BrokerType broker, String fileSha256);
}
