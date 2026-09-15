package com.smartbox.investory.accounting;

import java.time.LocalDate;

public record AccountingExportArtifact(
    String format,
    String schemaVersion,
    LocalDate period,
    String contentType,
    String filename,
    byte[] payload,
    String fingerprint,
    String validationStatus) {}
