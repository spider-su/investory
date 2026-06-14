package com.example.demo.data.repository;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "import_row_error")
public class ImportRowError {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "batch_id")
	private ImportBatch batch;

	@Column(name = "sheet_name")
	private String sheetName;

	@Column(name = "row_number")
	private Integer rowNumber;

	@Column(name = "error_code")
	private String errorCode;

	@Column(name = "error_message")
	private String errorMessage;

	@Column(name = "raw_payload")
	private String rawPayload;
}

