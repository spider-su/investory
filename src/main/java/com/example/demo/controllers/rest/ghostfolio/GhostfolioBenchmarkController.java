package com.example.demo.controllers.rest.ghostfolio;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only bootstrap data for Ghostfolio benchmark and watchlist widgets. */
@RestController
@RequestMapping("/api/v1")
public class GhostfolioBenchmarkController {

    @GetMapping("/watchlist")
    public Map<String, Object> watchlist() {
        return Map.of("watchlist", List.of());
    }

    @GetMapping("/benchmarks")
    public Map<String, Object> benchmarks() {
        return Map.of(
                "benchmarks",
                List.of(
                        benchmark("4227afc8", "BEAR_MARKET", "Bitcoin", "bitcoin", "2025-10-06T00:00:00.000Z", -0.4791631595086581, "DOWN", "DOWN"),
                        benchmark("4534c002", "NEUTRAL_MARKET", "DAX", "^GDAXI", "2026-07-06T00:00:00.000Z", -0.014433039105029518, "UP", "UP"),
                        benchmark("4534c002", "NEUTRAL_MARKET", "Dow Jones Industrial Average", "^DJI", "2026-07-06T00:00:00.000Z", -0.02089607270867635, "UP", "UNKNOWN"),
                        benchmark("e4d089e3", "NEUTRAL_MARKET", "FTSE All-World", "AMS:VWRL", "2026-07-06T00:00:00.000Z", -0.010405053883314752, "UP", "UNKNOWN"),
                        benchmark("4534c002", "BEAR_MARKET", "Gold", "GC=F", "2026-01-29T00:00:00.000Z", -0.230614456390022, "DOWN", "UP"),
                        benchmark("e4d089e3", "NEUTRAL_MARKET", "MSCI World", "NYSEARCA:URTH", "2026-06-02T00:00:00.000Z", -0.02570569405373945, "UP", "UNKNOWN"),
                        benchmark("4534c002", "NEUTRAL_MARKET", "Nasdaq Composite", "^IXIC", "2026-06-02T00:00:00.000Z", -0.07817539594106925, "UP", "UNKNOWN"),
                        benchmark("4534c002", "NEUTRAL_MARKET", "Nasdaq-100", "^NDX", "2026-06-02T00:00:00.000Z", -0.0825899571970771, "UP", "UNKNOWN"),
                        benchmark("e4d089e3", "NEUTRAL_MARKET", "Nikkei 225", "INDEXNIKKEI:NI225", "2026-06-25T00:00:00.000Z", -0.10274320906653563, "UP", "UNKNOWN"),
                        benchmark("e4d089e3", "NEUTRAL_MARKET", "S&P 500", "INDEXSP:.INX", "2026-06-02T00:00:00.000Z", -0.025992867073686757, "UP", "UNKNOWN"),
                        benchmark("e4d089e3", "NEUTRAL_MARKET", "SMI", "INDEXSWX:SMI", "2026-07-03T00:00:00.000Z", -0.002931176963223019, "UP", "UNKNOWN")));
    }

    private static Map<String, Object> benchmark(
            String dataSource,
            String marketCondition,
            String name,
            String symbol,
            String allTimeHighDate,
            double allTimeHighPerformancePercent,
            String trend50d,
            String trend200d) {
        return Map.of(
                "dataSource",
                dataSource,
                "marketCondition",
                marketCondition,
                "name",
                name,
                "performances",
                Map.of(
                        "allTimeHigh",
                        Map.of(
                                "date",
                                allTimeHighDate,
                                "performancePercent",
                                allTimeHighPerformancePercent)),
                "symbol",
                symbol,
                "trend50d",
                trend50d,
                "trend200d",
                trend200d);
    }
}
