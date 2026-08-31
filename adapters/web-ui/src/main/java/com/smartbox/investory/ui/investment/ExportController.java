package com.smartbox.investory.ui.investment;

import com.smartbox.investory.investment.api.exporting.YahooPortfolioExportApi;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/export")
@RequiredArgsConstructor
public class ExportController {

  private final YahooPortfolioExportApi exportService;

  /**
   * Generates the full Yahoo portfolio CSV from DB (monthly snapshots) and returns it as a browser
   * download. No file upload required.
   */
  @GetMapping("/generate")
  public ResponseEntity<byte[]> generatePortfolioCsv() {
    try {
      Path tempFile = Files.createTempFile("yahoo-export-", ".csv");
      try {
        exportService.exportToYahooCsv(tempFile.toString());
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
      log.error("Failed to generate Yahoo portfolio CSV", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }
}
