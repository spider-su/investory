package com.example.demo.data.repository;

import com.example.demo.data.BrokerType;
import com.example.demo.data.ImportBatchStatus;
import com.example.demo.data.ImportSourceType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.ZonedDateTime;

@Data
@Entity
@Table(name = "import_batch")
public class ImportBatch {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	private BrokerType broker;

	@Enumerated(EnumType.STRING)
	@Column(name = "source_type")
	private ImportSourceType sourceType;

	@Column(name = "source_ref")
	private String sourceRef;

	@Column(name = "file_name")
	private String fileName;

	@Column(name = "file_sha256")
	private String fileSha256;

	@Column(name = "started_at")
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
}

