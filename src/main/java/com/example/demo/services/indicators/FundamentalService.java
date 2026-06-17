package com.example.demo.services.indicators;



import com.example.demo.infrastructure.repository.FundamentalIndicator;
import com.example.demo.infrastructure.repository.FundamentalIndicatorsRepository;
import com.example.demo.infrastructure.repository.Stock;
import com.example.demo.infrastructure.repository.StockRepository;
import com.example.demo.clients.TwelveDataService;
import com.example.demo.services.imports.yahoo.YahooFinanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.example.demo.services.MarketService.NOT_SUPPORTED_SYMBOLS;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class FundamentalService {

    private final TwelveDataService twelveDataService;
    private final YahooFinanceService yahooFinanceService;

    private final StockRepository stockRepository;
    private final FundamentalIndicatorsRepository fundamentalIndicatorsRepository;
    public void createFundamentalsFromStock() {
        ZonedDateTime now = ZonedDateTime.now();
        Map<String, FundamentalIndicator> fundamentals = stockRepository.findAll().stream()
                .collect(Collectors.toMap(Stock::getSymbol,
                        s -> FundamentalIndicator.builder().symbol(s.getSymbol()).syncDate(now).build()));
        fundamentalIndicatorsRepository.saveAll(fundamentals.values());

        updateFundamentals();
    }

    public void updateFundamentals() {
        ZonedDateTime now = ZonedDateTime.now();
        Map<String, FundamentalIndicator> fundamentals = fundamentalIndicatorsRepository.findAll().stream()
                .collect(Collectors.toMap(FundamentalIndicator::getSymbol, Function.identity()));
        String tickers = String.join(",", fundamentals.keySet().stream()
                .filter(s -> !NOT_SUPPORTED_SYMBOLS.contains(s))
                .collect(Collectors.toSet()));
        try {
            Map<String, FundamentalIndicator> fundamentalIndicatorBySymbol = new HashMap<>();
//                    twelveDataService.fetchFundamentalIndicator(tickers);

            fundamentals.forEach((symbol, indicator) -> {
//                yahooFinanceService.fetchPERatioFromApi(symbol);
                twelveDataService.fetchTechnicalIndicatorsFromTwelveData(symbol.substring(0, symbol.indexOf(".")));
                FundamentalIndicator fundamentalIndicator = fundamentalIndicatorBySymbol.get(symbol);
                if (fundamentalIndicator == null) {
                    return;
                }
                indicator.setDividendYield(fundamentalIndicator.getDividendYield());
                indicator.setEps(fundamentalIndicator.getEps());
                indicator.setPeRatio(fundamentalIndicator.getPeRatio());
                indicator.setSyncDate(now);
            });
            fundamentalIndicatorsRepository.saveAll(fundamentals.values());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public Map<String, FundamentalIndicator> getBySymbol(String symbol) {
        return Map.of();
    }
}
