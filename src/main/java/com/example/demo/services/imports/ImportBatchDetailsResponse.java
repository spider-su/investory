package com.example.demo.services.imports;

import com.example.demo.infrastructure.BrokerType;
import com.example.demo.infrastructure.ImportBatchStatus;
import com.example.demo.infrastructure.ImportSourceType;

import java.time.ZonedDateTime;

public record ImportBatchDetailsResponse(Long batchId,
                                         BrokerType broker,
                                         ImportSourceType sourceType,
                                         String sourceRef,
                                         String fileName,
                                         String fileSha256,
                                         ImportBatchStatus status,
                                         int rowsTotal,
                                         int rowsApplied,
                                         int rowsFailed,
                                         String message,
                                         boolean duplicate,
                                         ZonedDateTime startedAt,
                                         ZonedDateTime finishedAt) {
}

