package com.smartbox.investory.investment.imports;

public record ImportBatchResponse(
    Long batchId,
    BrokerType broker,
    ImportBatchStatus status,
    int rowsTotal,
    int rowsApplied,
    int rowsFailed,
    String message,
    boolean duplicate,
    java.util.Set<Long> affectedAccountIds) {
  public ImportBatchResponse {
    affectedAccountIds = java.util.Set.copyOf(affectedAccountIds);
  }
}
