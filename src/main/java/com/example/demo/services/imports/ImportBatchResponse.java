package com.example.demo.services.imports;

import com.example.demo.data.BrokerType;
import com.example.demo.data.ImportBatchStatus;

public record ImportBatchResponse(Long batchId,
                                  BrokerType broker,
                                  ImportBatchStatus status,
                                  int rowsTotal,
                                  int rowsApplied,
                                  int rowsFailed,
                                  String message) {
}

