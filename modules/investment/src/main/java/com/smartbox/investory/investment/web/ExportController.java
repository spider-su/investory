package com.smartbox.investory.investment.web;

import com.smartbox.investory.investment.api.exporting.YahooPortfolioExportApi;
import jakarta.validation.constraints.Positive;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/investment/export")
@RequiredArgsConstructor
public class ExportController {

  private final YahooPortfolioExportApi exportService;

  /**
   * Generates the full Yahoo portfolio CSV from DB (monthly snapshots) and returns it as a browser
   * download. No file upload required.
   */
  @GetMapping("/generate")
  public ResponseEntity<byte[]> generatePortfolioCsv(@RequestParam @Positive Long portfolioId) {
    try {
      Path tempFile = Files.createTempFile("yahoo-export-", ".csv");
      try {
        exportService.exportToYahooCsv(portfolioId, tempFile.toString());
        byte[] bytes = Files.readAllBytes(tempFile);
        String fileName =
            String.format(
                Locale.ENGLISH,
                "yahoo-portfolio-%s.csv",
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(bytes);
      } finally {
        Files.deleteIfExists(tempFile);
      }
    } catch (Exception e) {
      throw new PortfolioExportException("Failed to generate Yahoo portfolio CSV", e);
    }
  }

  /** Keeps transport failures on the global REST error contract. */
  public static final class PortfolioExportException extends RuntimeException {
    public PortfolioExportException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
