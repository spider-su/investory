package com.example.demo.controllers.rest;

import com.example.demo.services.imports.yahoo.YahooExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/export")
@RequiredArgsConstructor
public class ExportController {

    private final YahooExportService exportService;

    @PostMapping("/save")
    public String exportToFile() {
        String path = "yahoo_export.csv";
        try {
            exportService.exportToYahooCsv(path);
            return "Exported successfully to: " + path;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // OPTION B: Download as file from browser
//    @GetMapping("/download")
//    public ResponseEntity<byte[]> downloadCsv() {
//        try {
//            String path = "/tmp/yahoo_export.csv";
//            exportService.exportToYahooCsv(path);
//
//            File file = new File(path);
//            byte[] data = new FileInputStream(file).readAllBytes();
//
//            return ResponseEntity.ok()
//                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=yahoo_export.csv")
//                    .contentType(MediaType.parseMediaType("text/csv"))
//                    .body(data);
//        } catch (IOException e) {
//            return ResponseEntity.status(500).body(("Export failed: " + e.getMessage()).getBytes());
//        }
//    }
}
