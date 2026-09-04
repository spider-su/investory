package com.smartbox.investory.investment.infrastructure.persistence.imports;

import com.smartbox.investory.investment.imports.BrokerType;
import com.smartbox.investory.investment.imports.ImportBatchStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImportRepository extends JpaRepository<ImportHistoryEntity, Long> {
  Optional<ImportHistoryEntity>
      findFirstByPortfolioIdAndBrokerAndFileSha256AndStatusOrderByAttemptNoDesc(
          Long portfolioId, BrokerType broker, String fileSha256, ImportBatchStatus status);

  Optional<ImportHistoryEntity> findFirstByBrokerAndFileSha256OrderByAttemptNoDesc(
      BrokerType broker, String fileSha256);

  Optional<ImportHistoryEntity> findFirstByPortfolioIdAndBrokerAndFileSha256OrderByAttemptNoDesc(
      Long portfolioId, BrokerType broker, String fileSha256);

  Optional<ImportHistoryEntity> findFirstByOrderByIdDesc();

  Optional<ImportHistoryEntity> findFirstByStatusOrderByFinishedAtDesc(ImportBatchStatus status);

  Optional<ImportHistoryEntity> findFirstByPortfolioIdAndStatusOrderByFinishedAtDesc(
      Long portfolioId, ImportBatchStatus status);

  default ImportHistoryEntity requireById(Long id) {
    return findById(id).orElseThrow(() -> new IllegalStateException("Import batch missing: " + id));
  }
}
