package com.smartbox.investory.accounting;

import java.time.Instant;
import java.time.LocalDate;

/** Common representation of a generated filing artifact, without submission automation. */
public record AccountingFilingArtifact(
    Type type,
    LocalDate period,
    String schemaVersion,
    byte[] payload,
    String payloadHash,
    Instant generatedAt,
    Status status) {
  public enum Type {
    JPK_V7M,
    VAT_UE,
    ZUS_DRA_REPRESENTATION
  }

  public enum Status {
    GENERATED,
    VALID,
    INVALID,
    SUBMITTED
  }
}
