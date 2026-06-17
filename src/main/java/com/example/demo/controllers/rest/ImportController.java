package com.example.demo.controllers.rest;

import com.example.demo.data.BrokerType;
import com.example.demo.data.ImportSourceType;
import com.example.demo.services.imports.ImportBatchResponse;
import com.example.demo.services.imports.ImportBatchDetailsResponse;
import com.example.demo.services.imports.ImportFailedException;
import com.example.demo.services.imports.ImportOrchestratorService;
import com.example.demo.services.imports.ImportRowErrorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/import")
@RequiredArgsConstructor
public class ImportController {

    private final ImportOrchestratorService importOrchestratorService;

    @GetMapping("/batches")
    List<ImportBatchDetailsResponse> listBatches(@RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit) {
        int safeLimit = Math.max(1, Math.min(100, limit));
        return importOrchestratorService.listBatches(safeLimit);
    }

    @GetMapping("/batches/{id}")
    ImportBatchDetailsResponse getBatch(@PathVariable("id") Long id) {
        return importOrchestratorService.getBatch(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import batch not found: " + id));
    }

    @GetMapping("/batches/latest")
    ImportBatchDetailsResponse getLatestBatch() {
        return importOrchestratorService.getLatestBatch()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No import batches found"));
    }

    @GetMapping("/batches/{id}/errors")
    List<ImportRowErrorResponse> getBatchErrors(@PathVariable("id") Long id) {
        return importOrchestratorService.getBatchErrors(id);
    }

    @PostMapping("/broker/{broker}")
    ImportBatchResponse importByBroker(@PathVariable("broker") String broker,
                                       @RequestParam("file") MultipartFile file,
                                       @RequestParam(value = "source", required = false, defaultValue = "MANUAL") ImportSourceType sourceType,
                                       @RequestParam(value = "sourceRef", required = false) String sourceRef) {
        BrokerType brokerType = BrokerType.fromValue(broker);
        return importOrchestratorService.importFile(
                brokerType,
                readBytes(file),
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.bin",
                sourceType,
                sourceRef);
    }

    /**
     * Legacy alias kept for older HTTP scripts. New callers should use
     * {@link #importByBroker(String, MultipartFile, ImportSourceType, String)}.
     */
    @PostMapping("/xtb")
    ImportBatchResponse importFromXtb(@RequestParam("file") MultipartFile file) {
        return importOrchestratorService.importFile(
                BrokerType.XTB,
                readBytes(file),
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "xtb.xlsx",
                ImportSourceType.MANUAL,
                null);
    }


    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read uploaded file: " + e.getMessage(), e);
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
