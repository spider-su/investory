package com.smartbox.investory.investment.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportHistory;
import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportRepository;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImportHistoryAuditWriterTest {

  @Mock private ImportRepository importRepository;

  @InjectMocks private ImportBatchAuditWriter auditWriter;

  @Test
  void findExistingAppliedBatch_ignoresNewerFailedAttempt() {
    ImportHistory completed = new ImportHistory();
    completed.setId(10L);
    completed.setBroker(BrokerType.IBKR);
    completed.setFileSha256("abc");
    completed.setAttemptNo(1);
    completed.setStatus(ImportBatchStatus.COMPLETED);

    when(importRepository.findFirstByBrokerAndFileSha256AndStatusOrderByAttemptNoDesc(
            BrokerType.IBKR, "abc", ImportBatchStatus.COMPLETED))
        .thenReturn(Optional.of(completed));

    Optional<ImportHistory> result = auditWriter.findExistingAppliedBatch(BrokerType.IBKR, "abc");

    assertEquals(Optional.of(completed), result);
  }

  @Test
  void startBatch_persistsReceivedRowWithMetadata() {
    when(importRepository.save(any(ImportHistory.class)))
        .thenAnswer(
            invocation -> {
              ImportHistory saved = invocation.getArgument(0);
              saved.setId(101L);
              return saved;
            });

    ImportHistory batch =
        auditWriter.startBatch(BrokerType.IBKR, ImportSourceType.MANUAL, "ref", "ibkr.csv", "abc");

    assertEquals(101L, batch.getId());
    assertEquals(BrokerType.IBKR, batch.getBroker());
    assertEquals(ImportSourceType.MANUAL, batch.getSourceType());
    assertEquals("ref", batch.getSourceRef());
    assertEquals("ibkr.csv", batch.getFileName());
    assertEquals("abc", batch.getFileSha256());
    assertEquals(ImportBatchStatus.STARTED, batch.getStatus());
    assertNotNull(batch.getStartedAt());
    assertEquals(0, batch.getRowsTotal());
  }

  @Test
  void startBatch_createsLinkedAttemptForSameChecksumAfterFailure() {
    ImportHistory failed = new ImportHistory();
    failed.setId(55L);
    failed.setBroker(BrokerType.IBKR);
    failed.setStatus(ImportBatchStatus.FAILED);
    failed.setRowsTotal(99);
    failed.setRowsApplied(12);
    failed.setRowsFailed(3);
    failed.setErrorMessage("old error");
    failed.setFinishedAt(java.time.ZonedDateTime.now());

    when(importRepository.findFirstByBrokerAndFileSha256OrderByAttemptNoDesc(
            BrokerType.IBKR, "abc"))
        .thenReturn(Optional.of(failed));
    when(importRepository.save(any(ImportHistory.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ImportHistory batch =
        auditWriter.startBatch(
            BrokerType.IBKR, ImportSourceType.MANUAL, "ref2", "retry.csv", "abc");

    assertEquals(55L, batch.getReprocessOf());
    assertEquals(ImportBatchStatus.STARTED, batch.getStatus());
    assertEquals(0, batch.getRowsTotal());
    assertEquals(0, batch.getRowsApplied());
    assertEquals(0, batch.getRowsFailed());
    assertEquals("retry.csv", batch.getFileName());
    assertEquals("ref2", batch.getSourceRef());
    assertNull(batch.getFinishedAt());
    assertNull(batch.getErrorMessage());
    assertNotNull(batch.getStartedAt());
  }

  @Test
  void finalizeApplied_updatesCountsAndStatus() {
    ImportHistory existing = new ImportHistory();
    existing.setId(5L);
    existing.setStatus(ImportBatchStatus.STARTED);
    when(importRepository.getById(5L)).thenReturn(existing);
    when(importRepository.save(any(ImportHistory.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ImportHistory result =
        auditWriter.finalizeApplied(5L, new ImportExecutionResult(10, 9, 1, "done"));

    assertEquals(ImportBatchStatus.PARTIAL, result.getStatus());
    assertEquals(10, result.getRowsTotal());
    assertEquals(9, result.getRowsApplied());
    assertEquals(1, result.getRowsFailed());
    assertEquals("done", result.getErrorMessage());
    assertNotNull(result.getFinishedAt());
  }

  @Test
  void finalizeFailed_persistsErrorAndTruncatesRawPayload() {
    ImportHistory existing = new ImportHistory();
    existing.setId(7L);
    when(importRepository.getById(7L)).thenReturn(existing);
    when(importRepository.save(any(ImportHistory.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    byte[] big = new byte[ImportBatchAuditWriter.RAW_PAYLOAD_LIMIT + 1024];
    Arrays.fill(big, (byte) 'a');

    ImportHistory failed = auditWriter.finalizeFailed(7L, "boom", big);

    assertEquals(ImportBatchStatus.FAILED, failed.getStatus());
    org.junit.jupiter.api.Assertions.assertTrue(
        failed.getErrorMessage().startsWith("boom\n[truncated to 8192 of 9216 bytes]"));
    assertEquals(1, failed.getRowsFailed());
  }

  @Test
  void finalizeApplied_marksZeroAppliedRowsAsFailed() {
    ImportHistory existing = new ImportHistory();
    existing.setId(6L);
    when(importRepository.getById(6L)).thenReturn(existing);
    when(importRepository.save(any(ImportHistory.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ImportHistory result =
        auditWriter.finalizeApplied(6L, new ImportExecutionResult(1, 0, 1, "bad"));

    assertEquals(ImportBatchStatus.FAILED, result.getStatus());
  }

  @Test
  void startReprocessBatchCreatesNewAttemptLinkedToOriginal() {
    ImportHistory original = new ImportHistory();
    original.setId(10L);
    original.setBroker(BrokerType.IBKR);
    original.setFileSha256("a".repeat(64));
    original.setSourceType(ImportSourceType.MANUAL);
    original.setAttemptNo(1);
    ImportHistory failed = new ImportHistory();
    failed.setId(11L);
    failed.setBroker(BrokerType.IBKR);
    failed.setFileSha256(original.getFileSha256());
    failed.setAttemptNo(2);
    failed.setStatus(ImportBatchStatus.FAILED);
    when(importRepository.findFirstByBrokerAndFileSha256OrderByAttemptNoDesc(
            BrokerType.IBKR, original.getFileSha256()))
        .thenReturn(Optional.of(failed));
    when(importRepository.save(any(ImportHistory.class)))
        .thenAnswer(
            invocation -> {
              ImportHistory saved = invocation.getArgument(0);
              saved.setId(12L);
              return saved;
            });

    ImportHistory reprocess = auditWriter.startReprocessBatch(original);

    assertEquals(12L, reprocess.getId());
    assertEquals(3, reprocess.getAttemptNo());
    assertEquals(10L, reprocess.getReprocessOf());
    assertEquals(ImportBatchStatus.STARTED, reprocess.getStatus());
  }

  @Test
  void finalizeFailed_setsTotalWhenParserFailsBeforeReturningCounters() {
    ImportHistory existing = new ImportHistory();
    existing.setId(9L);
    existing.setRowsTotal(0);
    existing.setRowsApplied(0);
    existing.setRowsFailed(0);
    when(importRepository.getById(9L)).thenReturn(existing);
    when(importRepository.save(any(ImportHistory.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ImportHistory failed = auditWriter.finalizeFailed(9L, "boom", null);

    assertEquals(1, failed.getRowsTotal());
    assertEquals(0, failed.getRowsApplied());
    assertEquals(1, failed.getRowsFailed());
  }

  @Test
  void finalizeFailed_keepsShortPayloadInline() {
    ImportHistory existing = new ImportHistory();
    existing.setId(8L);
    when(importRepository.getById(8L)).thenReturn(existing);
    when(importRepository.save(any(ImportHistory.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    auditWriter.finalizeFailed(8L, "boom", "hello".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void truncate_isNullSafe() {
    assertNull(ImportBatchAuditWriter.truncate(null));
    assertNull(ImportBatchAuditWriter.truncate(new byte[0]));
  }

  @Test
  void finalizeApplied_throwsWhenBatchIsMissing() {
    when(importRepository.getById(404L))
        .thenThrow(new IllegalStateException("Import batch missing: 404"));
    assertThrows(
        IllegalStateException.class,
        () -> auditWriter.finalizeApplied(404L, new ImportExecutionResult(0, 0, 0, "")));
  }
}
