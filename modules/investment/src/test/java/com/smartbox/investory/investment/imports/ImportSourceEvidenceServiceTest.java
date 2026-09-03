package com.smartbox.investory.investment.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportHistoryEntity;
import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportSourceFileEntity;
import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportSourceFileRepository;
import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportSourceRowEntity;
import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportSourceRowRepository;
import java.util.LinkedHashMap;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("Import Source Evidence Service")
class ImportSourceEvidenceServiceTest {
  @Mock private ImportSourceFileRepository fileRepository;
  @Mock private ImportSourceRowRepository rowRepository;

  private ImportSourceEvidenceService service;

  @BeforeEach
  void setUp() {
    service = new ImportSourceEvidenceService(fileRepository, rowRepository, new ObjectMapper());
  }

  @AfterEach
  void clearContext() {
    ImportEvidenceContext.clear();
  }

  @DisplayName("duplicate Checksum Reuses Immutable Artifact")
  @Test
  void duplicateChecksumReusesImmutableArtifact() {
    ImportHistoryEntity batch = batch();
    ImportSourceFileEntity existing = new ImportSourceFileEntity();
    existing.setId(7L);
    when(fileRepository.findByBrokerAndFileSha256(BrokerType.IBKR, batch.getFileSha256()))
        .thenReturn(Optional.of(existing));

    ImportSourceFileEntity result = service.storeArtifact(batch, new byte[] {1}, "text/csv");

    assertSame(existing, result);
    verify(fileRepository).findByBrokerAndFileSha256(BrokerType.IBKR, batch.getFileSha256());
  }

  @DisplayName("source Row Retains Batch Location And Original Values")
  @Test
  void sourceRowRetainsBatchLocationAndOriginalValues() {
    ImportHistoryEntity batch = batch();
    ImportSourceFileEntity file = new ImportSourceFileEntity();
    file.setId(7L);
    service.open(batch, file, "member.xlsx");
    when(rowRepository.save(any(ImportSourceRowEntity.class)))
        .thenAnswer(
            invocation -> {
              ImportSourceRowEntity row = invocation.getArgument(0);
              row.setId(11L);
              return row;
            });

    LinkedHashMap<String, String> values = new LinkedHashMap<>();
    values.put("Amount", "0.1");
    values.put("Currency", "USD");
    Long id =
        service.recordRow(
            "Cash Operations", "Cash Operations", 42, "TX-1", 1, "original,0.1,USD", values);

    ArgumentCaptor<ImportSourceRowEntity> captor =
        ArgumentCaptor.forClass(ImportSourceRowEntity.class);
    verify(rowRepository).save(captor.capture());
    ImportSourceRowEntity saved = captor.getValue();
    assertEquals(11L, id);
    assertEquals(99L, saved.getImportHistoryId());
    assertEquals(7L, saved.getSourceFileId());
    assertEquals(42, saved.getSourceRowNumber());
    assertEquals("TX-1", saved.getSourceRecordId());
    assertEquals("{\"Amount\":\"0.1\",\"Currency\":\"USD\"}", saved.getRawValues());
    assertEquals(64, saved.getLogicalRowSha256().length());

    ImportSourceRowEntity same = new ImportSourceRowEntity();
    service.open(batch, file, "other-member-name.xlsx");
    when(rowRepository.save(any(ImportSourceRowEntity.class)))
        .thenAnswer(
            invocation -> {
              ImportSourceRowEntity row = invocation.getArgument(0);
              same.setLogicalRowSha256(row.getLogicalRowSha256());
              return row;
            });
    service.recordRow(
        "Cash Operations", "Cash Operations", 999, "TX-1", 1, "original,0.1,USD", values);
    assertEquals(saved.getLogicalRowSha256(), same.getLogicalRowSha256());
  }

  private ImportHistoryEntity batch() {
    ImportHistoryEntity batch = new ImportHistoryEntity();
    batch.setId(99L);
    batch.setBroker(BrokerType.IBKR);
    batch.setFileName("statement.csv");
    batch.setFileSha256("0123456789012345678901234567890123456789012345678901234567890123");
    return batch;
  }
}
