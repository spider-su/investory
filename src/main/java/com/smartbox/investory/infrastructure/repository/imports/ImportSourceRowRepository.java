package com.smartbox.investory.infrastructure.repository.imports;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportSourceRowRepository extends JpaRepository<ImportSourceRow, Long> {
  List<ImportSourceRow> findByImportHistoryIdOrderBySourceRowNumberAscIdAsc(Long importHistoryId);
}
