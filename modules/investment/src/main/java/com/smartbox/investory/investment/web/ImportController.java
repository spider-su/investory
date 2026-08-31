package com.smartbox.investory.investment.web;

import com.smartbox.investory.investment.api.importing.ImportBroker;
import com.smartbox.investory.investment.api.importing.ImportSource;
import com.smartbox.investory.investment.api.importing.InvestmentImportApi;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/investment/imports")
@RequiredArgsConstructor
public class ImportController {

  private final InvestmentImportApi importApi;

  @PostMapping
  public InvestmentImportApi.ImportResult importAuto(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "source", defaultValue = "MANUAL") ImportSource source,
      @RequestParam(value = "sourceRef", required = false) String sourceRef) {
    String fileName =
        file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.bin";
    return importApi.importAuto(fileName, readBytes(file), source, sourceRef);
  }

  @PostMapping("/broker/{broker}")
  public InvestmentImportApi.ImportResult importByBroker(
      @PathVariable("broker") ImportBroker broker,
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "source", defaultValue = "MANUAL") ImportSource source,
      @RequestParam(value = "sourceRef", required = false) String sourceRef) {
    return importApi.importForBroker(
        broker,
        file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.bin",
        readBytes(file),
        source,
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
}
