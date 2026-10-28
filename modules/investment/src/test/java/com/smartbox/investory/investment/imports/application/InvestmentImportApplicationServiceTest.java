package com.smartbox.investory.investment.imports.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.smartbox.investory.investment.api.importing.*;
import com.smartbox.investory.investment.imports.*;
import org.junit.jupiter.api.Test;

class InvestmentImportApplicationServiceTest {
  @Test
  void importsWithoutSynchronousRefreshThenEnqueuesCommittedBatch() {
    var importer = mock(ImportOrchestratorService.class);
    var jobs = mock(ImportRefreshService.class);
    when(importer.importFile(
            eq(7L),
            eq(BrokerType.IBKR),
            any(),
            eq("test.csv"),
            eq(ImportSourceType.MANUAL),
            isNull(),
            eq(false)))
        .thenReturn(
            new ImportBatchResponse(
                42L,
                BrokerType.IBKR,
                ImportBatchStatus.COMPLETED,
                3,
                3,
                0,
                "Saved",
                false,
                java.util.Set.of(101L)));
    var result =
        new InvestmentImportApplicationService(importer, jobs)
            .importAuto(7L, "test.csv", new byte[] {1}, ImportSource.MANUAL, null);
    assertTrue(result.message().contains("background refresh queued"));
    var order = inOrder(importer, jobs);
    order
        .verify(importer)
        .importFile(
            eq(7L),
            eq(BrokerType.IBKR),
            any(),
            eq("test.csv"),
            eq(ImportSourceType.MANUAL),
            isNull(),
            eq(false));
    order.verify(jobs).enqueue(7L, 42L, java.util.Set.of(101L));
  }

  @Test
  void explicitDeferKeepsRefreshDeferred() {
    var importer = mock(ImportOrchestratorService.class);
    var jobs = mock(ImportRefreshService.class);
    when(importer.importFile(any(), any(), any(), any(), any(), any(), eq(false)))
        .thenReturn(
            new ImportBatchResponse(
                42L,
                BrokerType.IBKR,
                ImportBatchStatus.COMPLETED,
                3,
                3,
                0,
                "Saved",
                false,
                java.util.Set.of(101L)));
    new InvestmentImportApplicationService(importer, jobs)
        .importAuto(7L, "test.csv", new byte[] {1}, ImportSource.MANUAL, null, true);
    verifyNoInteractions(jobs);
  }
}
