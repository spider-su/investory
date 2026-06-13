package com.example.demo.controllers.rest;

import com.example.demo.services.MarketService;
import com.example.demo.services.XtbImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final XtbImportService xtbImportService;
    private final MarketService marketService;

    @PostMapping("/xtb")
    void importFromXtb(@RequestParam("file") MultipartFile file) {
        try {
            xtbImportService.importXtbExport(file.getInputStream());
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
