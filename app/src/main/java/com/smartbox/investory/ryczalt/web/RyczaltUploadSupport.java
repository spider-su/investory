package com.smartbox.investory.ryczalt.web;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Boundary validation and safe names for native Ryczalt uploads. */
final class RyczaltUploadSupport {
  static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

  private RyczaltUploadSupport() {}

  static String filename(String original, String fallback) {
    String value = original == null ? "" : original.replace('\\', '/');
    int slash = value.lastIndexOf('/');
    if (slash >= 0) value = value.substring(slash + 1);
    value = value.replaceAll("[\\p{Cntrl}]", "_").trim();
    if (value.isBlank()) value = fallback;
    if (value.length() <= 255) return value;
    int dot = value.lastIndexOf('.');
    String extension = dot > 0 ? value.substring(dot) : "";
    int stemLength = Math.max(1, 255 - extension.length());
    return value.substring(0, stemLength) + extension;
  }

  static String requireBank(String filename, String contentType, byte[] content) {
    requireCommon(filename, content);
    if (!filename.toLowerCase(Locale.ROOT).endsWith(".csv"))
      throw bad("Bank upload must be a CSV file");
    String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
    boolean accepted =
        type.equals("text/csv")
            || type.equals("application/csv")
            || type.equals("application/vnd.ms-excel")
            || (type.equals("application/octet-stream")
                && filename.toLowerCase(Locale.ROOT).endsWith(".csv"));
    if (!accepted) throw bad("Unsupported bank content type");
    return filename;
  }

  static String requireInvoice(String filename, String contentType, byte[] content) {
    requireCommon(filename, content);
    if (!filename.toLowerCase(Locale.ROOT).endsWith(".pdf")
        || contentType == null
        || !contentType.equalsIgnoreCase("application/pdf")
        || !looksLikePdf(content)) throw bad("Invoice upload must be a PDF file");
    return filename;
  }

  private static void requireCommon(String filename, byte[] content) {
    if (content == null || content.length == 0) throw bad("Upload must not be empty");
    if (content.length > MAX_FILE_SIZE) throw bad("Upload exceeds the 10 MB limit");
    if (filename == null || filename.isBlank()) throw bad("Upload filename is required");
  }

  private static boolean looksLikePdf(byte[] content) {
    return content.length >= 4
        && new String(content, 0, 4, StandardCharsets.ISO_8859_1).equals("%PDF");
  }

  private static ResponseStatusException bad(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
