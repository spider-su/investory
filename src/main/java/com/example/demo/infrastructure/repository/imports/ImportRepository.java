package com.example.demo.infrastructure.repository.imports;

import com.example.demo.infrastructure.BrokerType;
import com.example.demo.infrastructure.ImportBatchStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImportRepository extends JpaRepository<ImportHistory, Long> {
  Optional<ImportHistory> findFirstByBrokerAndFileSha256OrderByIdDesc(
      BrokerType broker, String fileSha256);

  Optional<ImportHistory> findFirstByBrokerAndFileSha256AndStatusOrderByAttemptNoDesc(
      BrokerType broker, String fileSha256, ImportBatchStatus status);

  Optional<ImportHistory> findFirstByBrokerAndFileSha256OrderByAttemptNoDesc(
      BrokerType broker, String fileSha256);

  Optional<ImportHistory> findFirstByOrderByIdDesc();

  @SuppressWarnings("deprecation")
  default ImportHistory getById(Long id) {
    return findById(id).orElseThrow(() -> new IllegalStateException("Import batch missing: " + id));
  }
}
