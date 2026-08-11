package com.example.demo.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationRefreshService {

  private final PortfolioProjectionService portfolioProjectionService;

  @Async("reconciliationRefreshExecutor")
  public void refreshAfterImport(Long batchId) {
    try {
      portfolioProjectionService.refreshReconciliationViews();
    } catch (Exception exception) {
      log.warn(
          "Reconciliation refresh failed after import (batchId={}): {}",
          batchId,
          exception.getMessage(),
          exception);
    }
  }
}
