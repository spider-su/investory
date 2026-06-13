package com.example.demo.controllers.rest;

import com.example.demo.services.CurrencyRateUpdaterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/currency")
@RequiredArgsConstructor
public class CurrencyRateController {

    private final CurrencyRateUpdaterService updaterService;

    @PostMapping("/refresh")
    public void refreshRates() {
        updaterService.updateCurrencyRates();
    }
}
