package com.smartbox.investory.investment.imports;

import com.smartbox.investory.infrastructure.BrokerType;
import com.smartbox.investory.infrastructure.ImportBatchStatus;

public record ImportBatchResponse(
    Long batchId,
    BrokerType broker,
    ImportBatchStatus status,
    int rowsTotal,
    int rowsApplied,
    int rowsFailed,
    String message,
    boolean duplicate) {}
