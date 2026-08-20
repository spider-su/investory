package com.smartbox.investory.investment.infrastructure.persistence.imports;

import com.smartbox.investory.investment.imports.BrokerType;
import jakarta.persistence.*;
import java.time.ZonedDateTime;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@Entity
@Table(name = "import_source_files")
public class ImportSourceFile {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider", nullable = false, length = 32)
  private BrokerType broker;

  @Column(name = "import_history_id", nullable = false)
  private Long importHistoryId;

  @Column(name = "file_name")
  private String fileName;

  @Column(name = "content_type")
  private String contentType;

  @Column(name = "file_sha256", nullable = false, length = 64)
  private String fileSha256;

  @Column(name = "original_size", nullable = false)
  private Long originalSize;

  @JdbcTypeCode(SqlTypes.VARBINARY)
  @Column(name = "raw_payload", nullable = false)
  private byte[] rawPayload;

  @Column(name = "created_at", nullable = false)
  private ZonedDateTime createdAt;
}
