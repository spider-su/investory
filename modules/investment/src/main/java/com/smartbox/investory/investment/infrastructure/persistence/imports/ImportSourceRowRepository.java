package com.smartbox.investory.investment.infrastructure.persistence.imports;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportSourceRowRepository extends JpaRepository<ImportSourceRowEntity, Long> {
  List<ImportSourceRowEntity> findByImportHistoryIdOrderBySourceRowNumberAscIdAsc(Long importHistoryId);
}
