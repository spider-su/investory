package com.example.demo.controllers.rest;

import com.example.demo.services.indicators.FundamentalService;
import com.example.demo.services.indicators.TechnicalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/indicator")
@RequiredArgsConstructor
public class IndicatorController {

    private final TechnicalService technicalService;
    private final FundamentalService fundamentalService;

    @PostMapping("/technical")
    void createTechnicalsFromStock() {
        try {
            technicalService.createTechnicalsFromStock();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create TechnicalIndicators", e);
        }
    }

    @PutMapping("/technical")
    void updateTechnicals() {
        try {
            technicalService.updateTechnicals();
        } catch (Exception e) {
            throw new RuntimeException("Failed to update TechnicalIndicators", e);
        }
    }


    @PostMapping("/fundamental")
    void createFundamentalsFromStock() {
        try {
            fundamentalService.createFundamentalsFromStock();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create TechnicalIndicators", e);
        }
    }

    @PutMapping("/fundamental")
    void updateFundamentals() {
        try {
            fundamentalService.updateFundamentals();
        } catch (Exception e) {
            throw new RuntimeException("Failed to update TechnicalIndicators", e);
        }
    }
}
