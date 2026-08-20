package com.smartbox.investory.ui.investment;

import com.smartbox.investory.investment.api.InvestmentImportApi;
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

  private final InvestmentImportApi importApi;

  @PostMapping
  Object importAuto(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "source", required = false, defaultValue = "MANUAL")
          String sourceType,
      @RequestParam(value = "sourceRef", required = false) String sourceRef) {
    String fileName =
        file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.bin";
    return importApi.importAuto(fileName, readBytes(file), sourceType, sourceRef);
  }

  @PostMapping("/broker/{broker}")
  Object importByBroker(
      @PathVariable("broker") String broker,
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "source", required = false, defaultValue = "MANUAL")
          String sourceType,
      @RequestParam(value = "sourceRef", required = false) String sourceRef) {
    return importApi.importForBroker(
        broker,
        file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.bin",
        readBytes(file),
        sourceType,
        sourceRef);
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

  @ExceptionHandler(InvestmentImportApi.ImportFailure.class)
  @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
  String handleImportFailed(InvestmentImportApi.ImportFailure e) {
    log.warn("Import failed: {}", e.getMessage());
    return e.getMessage();
  }
}
