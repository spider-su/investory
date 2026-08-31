package com.smartbox.investory.services.imports;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbox.investory.infrastructure.repository.imports.ImportHistory;
import com.smartbox.investory.infrastructure.repository.imports.ImportSourceFile;
import com.smartbox.investory.infrastructure.repository.imports.ImportSourceFileRepository;
import com.smartbox.investory.infrastructure.repository.imports.ImportSourceRow;
import com.smartbox.investory.infrastructure.repository.imports.ImportSourceRowRepository;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportSourceEvidenceService {
  private final ImportSourceFileRepository fileRepository;
  private final ImportSourceRowRepository rowRepository;
  private final ObjectMapper objectMapper;

  @Autowired
  public ImportSourceEvidenceService(
      ImportSourceFileRepository fileRepository, ImportSourceRowRepository rowRepository) {
    this(fileRepository, rowRepository, new ObjectMapper());
  }

  public ImportSourceEvidenceService(
      ImportSourceFileRepository fileRepository,
      ImportSourceRowRepository rowRepository,
      ObjectMapper objectMapper) {
    this.fileRepository = fileRepository;
    this.rowRepository = rowRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ImportSourceFile storeArtifact(ImportHistory batch, byte[] payload, String contentType) {
    Optional<ImportSourceFile> existing =
        fileRepository.findByBrokerAndFileSha256(batch.getBroker(), batch.getFileSha256());
    if (existing.isPresent()) {
      return existing.get();
    }
    ImportSourceFile file = new ImportSourceFile();
    file.setBroker(batch.getBroker());
    file.setImportHistoryId(batch.getId());
    file.setFileName(batch.getFileName());
    file.setContentType(contentType);
    file.setFileSha256(batch.getFileSha256());
    file.setOriginalSize((long) payload.length);
    file.setRawPayload(payload.clone());
    file.setCreatedAt(ZonedDateTime.now());
    return fileRepository.save(file);
  }

  public Scope open(ImportHistory batch, ImportSourceFile file, String archiveMemberName) {
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
    ImportSourceRow row = new ImportSourceRow();
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
    row.setCreatedAt(ZonedDateTime.now());
    return rowRepository.save(row).getId();
  }

  private String toJson(Map<String, ?> values) {
    try {
      return objectMapper.writeValueAsString(values);
    } catch (JsonProcessingException exception) {
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
