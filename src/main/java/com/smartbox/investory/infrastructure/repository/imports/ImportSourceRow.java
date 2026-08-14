package com.smartbox.investory.infrastructure.repository.imports;

import com.smartbox.investory.infrastructure.BrokerType;
import jakarta.persistence.*;
import java.time.ZonedDateTime;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@Entity
@Table(name = "import_source_rows")
public class ImportSourceRow {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "import_history_id", nullable = false)
  private Long importHistoryId;

  @Column(name = "source_file_id")
  private Long sourceFileId;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider", nullable = false, length = 32)
  private BrokerType broker;

  @Column(name = "section_name")
  private String sectionName;

  @Column(name = "sheet_name")
  private String sheetName;

  @Column(name = "archive_member_name")
  private String archiveMemberName;

  @Column(name = "source_row_number")
  private Integer sourceRowNumber;

  @Column(name = "source_record_id")
  private String sourceRecordId;

  @Column(name = "source_row_occurrence", nullable = false)
  private Integer sourceRowOccurrence = 1;

  @Column(name = "raw_text")
  private String rawText;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "raw_values", nullable = false, columnDefinition = "jsonb")
  private String rawValues;

  @Column(name = "created_at", nullable = false)
  private ZonedDateTime createdAt;
}
