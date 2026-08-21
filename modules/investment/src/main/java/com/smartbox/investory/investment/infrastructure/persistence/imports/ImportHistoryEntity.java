package com.smartbox.investory.investment.infrastructure.persistence.imports;

import com.smartbox.investory.investment.imports.BrokerType;
import com.smartbox.investory.investment.imports.ImportBatchStatus;
import com.smartbox.investory.investment.imports.ImportSourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;
import lombok.Data;

@Data
@Entity
@Table(name = "import_history")
public class ImportHistoryEntity {

  @Id
  @SequenceGenerator(
      name = "import_history_id_seq",
      sequenceName = "import_history_id_seq",
      allocationSize = 1)
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "import_history_id_seq")
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider", nullable = false, length = 32)
  private BrokerType broker;

  @Column(name = "source_type")
  @Enumerated(EnumType.STRING)
  private ImportSourceType sourceType;

  @Column(name = "source_ref")
  private String sourceRef;

  @Column(name = "file_name")
  private String fileName;

  @Column(name = "file_sha256", nullable = false, length = 255)
  private String fileSha256;

  @Column(name = "started_at", nullable = false)
  private ZonedDateTime startedAt;

  @Column(name = "finished_at")
  private ZonedDateTime finishedAt;

  @Enumerated(EnumType.STRING)
  private ImportBatchStatus status;

  @Column(name = "rows_total")
  private Integer rowsTotal;

  @Column(name = "rows_applied")
  private Integer rowsApplied;

  @Column(name = "rows_failed")
  private Integer rowsFailed;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "attempt_no", nullable = false)
  private Integer attemptNo = 1;

  @Column(name = "reprocess_of")
  private Long reprocessOf;
}
