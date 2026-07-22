package com.example.demo.controllers.rest.ghostfolio;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Maps Investory open-position summaries to Ghostfolio holdings. */
@RestController
@RequestMapping("/api/v1/portfolio")
@RequiredArgsConstructor
public class GhostfolioHoldingsController {

    private final GhostfolioCompatibilityService compatibilityService;

    @GetMapping("/holdings")
    public Map<String, Object> holdings(
            @RequestParam(name = "accounts", required = false) String accountIds,
            @RequestParam(name = "symbol", required = false) String symbol) {
        return compatibilityService.holdings(accountIds, symbol);
    }
}
