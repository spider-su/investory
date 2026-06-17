package com.example.demo.controllers.rest;

import com.example.demo.services.MarketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stock-catalog admin endpoints. Split out of {@code ImportController} so the import
 * surface is focused on broker-statement uploads only; these endpoints manage the
 * {@code stocks} catalog used by quote refresh + IBKR re-pricing.
 *
 * <p>Both endpoints require {@code ROLE_ADMIN} (enforced globally by
 * {@code SecurityConfig} for {@code POST/PUT/DELETE}).
 */
@Slf4j
@RestController
@RequestMapping("/stock")
@RequiredArgsConstructor
public class StockController {

    private final MarketService marketService;

    /** Seed a {@code stocks} row for every open position that doesn't already have one. */
    @PostMapping("/create")
    void create() {
        marketService.createStocks();
    }

    /** Refresh market prices for all known {@code stocks} via {@code TwelveDataService}. */
    @PostMapping("/sync")
    void sync() {
        marketService.updateStocks();
    }
}

