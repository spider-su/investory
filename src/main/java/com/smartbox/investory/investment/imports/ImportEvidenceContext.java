package com.smartbox.investory.investment.imports;

import com.smartbox.investory.infrastructure.BrokerType;

/** Request-local provenance scope. Never use this as financial state. */
public record ImportEvidenceContext(
    Long importHistoryId, Long sourceFileId, BrokerType broker, String archiveMemberName) {
  private static final ThreadLocal<ImportEvidenceContext> CURRENT = new ThreadLocal<>();

  public static void open(ImportEvidenceContext context) {
    CURRENT.set(context);
  }

  public static ImportEvidenceContext current() {
    return CURRENT.get();
  }

  public static void clear() {
    CURRENT.remove();
  }

  public static void archiveMember(String memberName) {
    ImportEvidenceContext current = CURRENT.get();
    if (current != null) {
      CURRENT.set(
          new ImportEvidenceContext(
              current.importHistoryId(), current.sourceFileId(), current.broker(), memberName));
    }
  }
}
