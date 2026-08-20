package com.smartbox.investory.ui.investment;

import com.smartbox.investory.investment.imports.BrokerType;
import com.smartbox.investory.investment.imports.ImportBatchResponse;
import com.smartbox.investory.investment.imports.ImportFailedException;
import com.smartbox.investory.investment.imports.ImportOrchestratorService;
import com.smartbox.investory.investment.imports.ImportSourceType;
import java.io.IOException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/import")
@RequiredArgsConstructor
public class ImportController {

  private final ImportOrchestratorService importOrchestratorService;

  @PostMapping
  ImportBatchResponse importAuto(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "source", required = false, defaultValue = "MANUAL")
          ImportSourceType sourceType,
      @RequestParam(value = "sourceRef", required = false) String sourceRef) {
    String fileName =
        file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.bin";
    BrokerType brokerType = detectBrokerType(fileName);
    return importOrchestratorService.importFile(
        brokerType, readBytes(file), fileName, sourceType, sourceRef);
  }

  @PostMapping("/broker/{broker}")
  ImportBatchResponse importByBroker(
      @PathVariable("broker") String broker,
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "source", required = false, defaultValue = "MANUAL")
          ImportSourceType sourceType,
      @RequestParam(value = "sourceRef", required = false) String sourceRef) {
    BrokerType brokerType = BrokerType.fromValue(broker);
    return importOrchestratorService.importFile(
        brokerType,
        readBytes(file),
        file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.bin",
        sourceType,
        sourceRef);
  }

  private static BrokerType detectBrokerType(String fileName) {
    String normalized = fileName.toLowerCase(Locale.ROOT);
    if (normalized.endsWith(".csv")) {
      return BrokerType.IBKR;
    }
    if (normalized.endsWith(".xlsx") || normalized.endsWith(".zip")) {
      return BrokerType.XTB;
    }
    throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Unsupported import file extension for auto-detect: "
            + fileName
            + ". Supported: .csv -> IBKR, .xlsx/.zip -> XTB");
  }

  private static byte[] readBytes(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Could not read uploaded file: " + e.getMessage(), e);
    }
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  String handleIllegalArgument(IllegalArgumentException e) {
    return e.getMessage();
  }

  @ExceptionHandler(ImportFailedException.class)
  @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
  String handleImportFailed(ImportFailedException e) {
    log.warn("Import failed: {}", e.getMessage());
    return e.getMessage();
  }
}
