package com.example.demo.controllers.rest;

import com.example.demo.data.BrokerType;
import com.example.demo.data.ImportSourceType;
import com.example.demo.services.MarketService;
import com.example.demo.services.imports.ImportBatchResponse;
import com.example.demo.services.imports.ImportOrchestratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/import")
@RequiredArgsConstructor
public class ImportController {

    private final ImportOrchestratorService importOrchestratorService;
    private final MarketService marketService;

    @PostMapping("/broker/{broker}")
    ImportBatchResponse importByBroker(@PathVariable("broker") String broker,
                                       @RequestParam("file") MultipartFile file,
                                       @RequestParam(value = "source", required = false, defaultValue = "MANUAL") ImportSourceType sourceType,
                                       @RequestParam(value = "sourceRef", required = false) String sourceRef) {
        try {
            return importOrchestratorService.importFile(
                    BrokerType.fromValue(broker),
                    file.getBytes(),
                    file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.xlsx",
                    sourceType,
                    sourceRef
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to import broker export", e);
        }
    }

    @PostMapping("/xtb")
    void importFromXtb(@RequestParam("file") MultipartFile file) {
        try {
            importOrchestratorService.importFile(
                    BrokerType.XTB,
                    file.getBytes(),
                    file.getOriginalFilename() != null ? file.getOriginalFilename() : "xtb.xlsx",
                    ImportSourceType.MANUAL,
                    null
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to import XTB export", e);
        }
    }

    @PostMapping("/stock/create")
    void createOpenedPositions() {
        try {
            marketService.createStocks();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create OpenedPositions stock", e);
        }
    }

    @PostMapping("/stock/sync")
    void sync() {
        try {
            marketService.updateStocks();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create OpenedPositions stock", e);
        }
    }
}
