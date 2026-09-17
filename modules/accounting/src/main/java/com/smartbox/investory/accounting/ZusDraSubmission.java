package com.smartbox.investory.accounting;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;

/** Readable result of a ZUS DRA submission attempt. */
public record ZusDraSubmission(
    String id,
    LocalDate period,
    byte[] payload,
    String payloadHash,
    Instant createdAt,
    Path payloadFile,
    Status status) {
  public enum Status {
    MOCK_STORED
  }
}
