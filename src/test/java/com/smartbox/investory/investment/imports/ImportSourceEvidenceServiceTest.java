package com.smartbox.investory.investment.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportHistory;
import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportSourceFile;
import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportSourceFileRepository;
import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportSourceRow;
import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportSourceRowRepository;
import java.util.LinkedHashMap;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
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

  @Test
  void duplicateChecksumReusesImmutableArtifact() {
    ImportHistory batch = batch();
    ImportSourceFile existing = new ImportSourceFile();
    existing.setId(7L);
    when(fileRepository.findByBrokerAndFileSha256(BrokerType.IBKR, batch.getFileSha256()))
        .thenReturn(Optional.of(existing));

    ImportSourceFile result = service.storeArtifact(batch, new byte[] {1}, "text/csv");

    assertSame(existing, result);
    verify(fileRepository).findByBrokerAndFileSha256(BrokerType.IBKR, batch.getFileSha256());
  }

  @Test
  void sourceRowRetainsBatchLocationAndOriginalValues() {
    ImportHistory batch = batch();
    ImportSourceFile file = new ImportSourceFile();
    file.setId(7L);
    service.open(batch, file, "member.xlsx");
    when(rowRepository.save(any(ImportSourceRow.class)))
        .thenAnswer(
            invocation -> {
              ImportSourceRow row = invocation.getArgument(0);
              row.setId(11L);
              return row;
            });

    LinkedHashMap<String, String> values = new LinkedHashMap<>();
    values.put("Amount", "0.1");
    values.put("Currency", "USD");
    Long id =
        service.recordRow(
            "Cash Operations", "Cash Operations", 42, "TX-1", 1, "original,0.1,USD", values);

    ArgumentCaptor<ImportSourceRow> captor = ArgumentCaptor.forClass(ImportSourceRow.class);
    verify(rowRepository).save(captor.capture());
    ImportSourceRow saved = captor.getValue();
    assertEquals(11L, id);
    assertEquals(99L, saved.getImportHistoryId());
    assertEquals(7L, saved.getSourceFileId());
    assertEquals(42, saved.getSourceRowNumber());
    assertEquals("TX-1", saved.getSourceRecordId());
    assertEquals("{\"Amount\":\"0.1\",\"Currency\":\"USD\"}", saved.getRawValues());
  }

  private ImportHistory batch() {
    ImportHistory batch = new ImportHistory();
    batch.setId(99L);
    batch.setBroker(BrokerType.IBKR);
    batch.setFileName("statement.csv");
    batch.setFileSha256("0123456789012345678901234567890123456789012345678901234567890123");
    return batch;
  }
}
