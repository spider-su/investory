package com.example.demo.services.imports;

import com.example.demo.data.BrokerType;
import com.example.demo.data.ImportBatchStatus;
import com.example.demo.data.ImportSourceType;

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

