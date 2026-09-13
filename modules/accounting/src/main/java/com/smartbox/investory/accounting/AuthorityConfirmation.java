package com.smartbox.investory.accounting;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Imported or manually recorded authority evidence; it does not submit anything. */
public record AuthorityConfirmation(
    String authority,
    String obligationOrArtifactType,
    LocalDate period,
    String externalReference,
    ConfirmationType confirmationType,
    ConfirmationStatus status,
    Instant receivedAt,
    Long sourceDocumentId,
    String note,
    BigDecimal amount,
    String calculationHash) {
  public AuthorityConfirmation(
      String authority,
      String obligationOrArtifactType,
      LocalDate period,
      String externalReference,
      ConfirmationType confirmationType,
      ConfirmationStatus status,
      Instant receivedAt,
      Long sourceDocumentId,
      String note) {
    this(
        authority,
        obligationOrArtifactType,
        period,
        externalReference,
        confirmationType,
        status,
        receivedAt,
        sourceDocumentId,
        note,
        null,
        null);
  }

  public AuthorityConfirmation(
      String authority,
      String obligationOrArtifactType,
      LocalDate period,
      String externalReference,
      ConfirmationType confirmationType,
      ConfirmationStatus status,
      Instant receivedAt,
      Long sourceDocumentId,
      String note,
      BigDecimal amount) {
    this(
        authority,
        obligationOrArtifactType,
        period,
        externalReference,
        confirmationType,
        status,
        receivedAt,
        sourceDocumentId,
        note,
        amount,
        null);
  }

  public enum ConfirmationType {
    JPK_UPO,
    VAT_UE_UPO,
    ZUS_DRA_ACCEPTANCE,
    ZUS_ACCOUNT_POSTING,
    TAX_ACCOUNT_POSTING,
    KSEF_ACCEPTANCE
  }

  public enum ConfirmationStatus {
    ACCEPTED,
    REJECTED,
    POSTED
  }
}
