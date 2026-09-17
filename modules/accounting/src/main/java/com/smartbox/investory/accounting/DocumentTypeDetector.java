package com.smartbox.investory.accounting;

import java.util.Locale;

final class DocumentTypeDetector {
  private DocumentTypeDetector() {}

  static Type detect(DocumentInput input) {
    String contentType =
        input.contentType() == null ? "" : input.contentType().toLowerCase(Locale.ROOT);
    if ("application/pdf".equals(contentType) || hasPdfSignature(input.bytes())) return Type.PDF;
    if ("image/jpeg".equals(contentType) || "image/png".equals(contentType)) return Type.IMAGE;

    String fileName = input.fileName() == null ? "" : input.fileName().toLowerCase(Locale.ROOT);
    if (fileName.endsWith(".pdf")) return Type.PDF;
    if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png")) {
      return Type.IMAGE;
    }
    return Type.UNSUPPORTED;
  }

  private static boolean hasPdfSignature(byte[] bytes) {
    return bytes != null
        && bytes.length >= 5
        && bytes[0] == '%'
        && bytes[1] == 'P'
        && bytes[2] == 'D'
        && bytes[3] == 'F'
        && bytes[4] == '-';
  }

  enum Type {
    PDF,
    IMAGE,
    UNSUPPORTED
  }
}
