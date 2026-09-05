package com.smartbox.investory.investment.imports;

import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportHistoryEntity;
import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportSourceFileEntity;
import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportSourceFileRepository;
import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportSourceRowEntity;
import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportSourceRowRepository;
import com.smartbox.investory.shared.time.ApplicationTime;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class ImportSourceEvidenceService {
  private final ImportSourceFileRepository fileRepository;
  private final ImportSourceRowRepository rowRepository;
  private final ObjectMapper objectMapper;
  private final ApplicationTime applicationTime;

  @Autowired
  public ImportSourceEvidenceService(
      ImportSourceFileRepository fileRepository,
      ImportSourceRowRepository rowRepository,
      ObjectMapper objectMapper,
      ApplicationTime applicationTime) {
    this.fileRepository = fileRepository;
    this.rowRepository = rowRepository;
    this.objectMapper = objectMapper;
    this.applicationTime = applicationTime;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ImportSourceFileEntity storeArtifact(
      ImportHistoryEntity batch, byte[] payload, String contentType) {
    Optional<ImportSourceFileEntity> existing =
        fileRepository.findByBrokerAndFileSha256(batch.getBroker(), batch.getFileSha256());
    if (existing.isPresent()) {
      return existing.get();
    }
    ImportSourceFileEntity file = new ImportSourceFileEntity();
    file.setBroker(batch.getBroker());
    file.setImportHistoryId(batch.getId());
    file.setFileName(batch.getFileName());
    file.setContentType(contentType);
    file.setFileSha256(batch.getFileSha256());
    file.setOriginalSize((long) payload.length);
    file.setRawPayload(payload.clone());
    file.setCreatedAt(applicationTime.now(applicationTime.businessZone()));
    return fileRepository.save(file);
  }

  public Scope open(
      ImportHistoryEntity batch, ImportSourceFileEntity file, String archiveMemberName) {
    ImportEvidenceContext.open(
        new ImportEvidenceContext(
            batch.getId(), file.getId(), batch.getBroker(), archiveMemberName));
    return new Scope();
  }

  @Transactional
  public Long recordRow(
      String sectionName,
      String sheetName,
      Integer sourceRowNumber,
      String sourceRecordId,
      int occurrence,
      String rawText,
      Map<String, ?> rawValues) {
    ImportEvidenceContext context = ImportEvidenceContext.current();
    if (context == null) {
      return null;
    }
    ImportSourceRowEntity row = new ImportSourceRowEntity();
    row.setImportHistoryId(context.importHistoryId());
    row.setSourceFileId(context.sourceFileId());
    row.setBroker(context.broker());
    row.setSectionName(sectionName);
    row.setSheetName(sheetName);
    row.setArchiveMemberName(context.archiveMemberName());
    row.setSourceRowNumber(sourceRowNumber);
    row.setSourceRecordId(sourceRecordId);
    row.setSourceRowOccurrence(occurrence);
    row.setRawText(rawText);
    row.setRawValues(toJson(rawValues));
    row.setLogicalRowSha256(
        BrokerSourceRowIdentity.logicalRowSha256(
            context.broker(),
            sectionName,
            sheetName,
            sourceRecordId,
            occurrence,
            rawText,
            row.getRawValues()));
    row.setCreatedAt(applicationTime.now(applicationTime.businessZone()));
    return rowRepository.save(row).getId();
  }

  private String toJson(Map<String, ?> values) {
    try {
      return objectMapper.writeValueAsString(values);
    } catch (JacksonException exception) {
      throw new IllegalArgumentException("Cannot serialize broker source row evidence", exception);
    }
  }

  public static final class Scope implements AutoCloseable {
    @Override
    public void close() {
      ImportEvidenceContext.clear();
    }
  }
}
