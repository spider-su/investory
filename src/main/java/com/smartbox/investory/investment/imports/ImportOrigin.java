package com.smartbox.investory.investment.imports;

import java.time.ZonedDateTime;

public record ImportOrigin(
    long financialRowId,
    String financialTable,
    String provider,
    long importBatchId,
    Integer attemptNo,
    String status,
    String fileName,
    String fileSha256,
    ZonedDateTime importedAt,
    String sectionName,
    String sheetName,
    String archiveMemberName,
    Integer sourceRowNumber,
    String sourceRecordId,
    Integer sourceRowOccurrence,
    String rawText,
    String rawValues) {}
