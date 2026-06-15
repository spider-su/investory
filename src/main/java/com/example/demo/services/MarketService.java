package com.example.demo.services;

import com.example.demo.services.models.StockQuote;
import com.example.demo.data.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class MarketService {

    public static final Set<String> NOT_SUPPORTED_SYMBOLS = Set.of("CSPX");
    private static final String IBKR_ACCOUNT = "IBKR";

    private final TwelveDataService twelveDataService;

    private final OpenedPositionRepository openedPositionRepository;
    private final StockRepository stockRepository;
    private final HistoryService historyService;
    private final AccountSummaryRepository accountSummaryRepository;

    public void createStocks() {
        Map<String, List<OpenedPosition>> openedPositions = openedPositionRepository.findAll().stream()
                .filter(p -> p.getSymbol() != null
                        && (p.getSymbol().contains(".US") || IBKR_ACCOUNT.equals(p.getAccount())))
                .collect(Collectors.groupingBy(OpenedPosition::getSymbol));

        Map<String, Stock> stocks = stockRepository.findAll().stream()
                .collect(Collectors.toMap(Stock::getSymbol, Function.identity(), (a, b) -> b, LinkedHashMap::new));

        ZonedDateTime now = ZonedDateTime.now();
        openedPositions.forEach((symbol, positions) -> {
            if (!stocks.containsKey(symbol)) {
                stocks.put(symbol, positionToStock(positions, now));
            }
        });
        stockRepository.saveAll(stocks.values());
    }

    private Stock positionToStock(List<OpenedPosition> positions, ZonedDateTime now) {
        Stock stock = new Stock();
        OpenedPosition position = CollectionUtils.firstElement(positions);
        stock.setCurrency(position.getCurrency());
        stock.setSymbol(position.getSymbol());
        // XTB symbols are TICKER.EXCHANGE; IBKR symbols are bare tickers.
        int dot = position.getSymbol().indexOf(".");
        stock.setTicker(dot >= 0 ? position.getSymbol().substring(0, dot) : position.getSymbol());

        stock.setAmount(positions.stream().map(OpenedPosition::getVolume).reduce(Double::sum).orElse(0.0));
        stock.setOpenPrice(positions.stream().map(OpenedPosition::getOpenPrice)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).average().orElse(0.0));
        stock.setMarketPrice(positions.stream().map(OpenedPosition::getMarketPrice)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).average().orElse(0.0));
        stock.setDayOpenPrice(stock.getMarketPrice());
        stock.setProfit(positions.stream().map(OpenedPosition::getProfit).reduce(Double::sum).orElse(0.0));

        stock.setSyncDate(now);
        stock.setUpdatedDate(now.minusHours(1));
        return stock;
    }

    public void updateStocks() {
        log.info("Updating stock prices for the open positions started");
        Map<String, Stock> stocks = stockRepository.findAll().stream()
                .sorted(Comparator.comparing(Stock::getUpdatedDate))
                .collect(Collectors.toMap(
                        Stock::getTicker,
                        Function.identity(),
                        (a, b) -> b,
                        LinkedHashMap::new // preserve order
                ));

        List<Map<String, Stock>> chunks = splitIntoChunks(stocks, 8);
        log.info("Found {} stocks, divided in {} chunks", stocks.size(), chunks.size());
        AtomicInteger i = new AtomicInteger(1);
        chunks.forEach(chunk  -> {
            log.info("Updating chunk {} out of {}", i.getAndIncrement(), chunks.size());
            try {
                updateStockMarketPrice(chunk);
            } catch (Exception e) {
                // e.g. TwelveData 429 rate limit: skip this chunk, keep the prices we already
                // fetched, and let a later run pick up the rest instead of failing the whole sync.
                log.warn("Skipping stock chunk (will retry next run): {}", e.getMessage());
            }
            try {
                Thread.sleep(120_000); // 2 minutes
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });
        log.info("Updating stock prices for the open positions finished");
    }

    public static <K, V> List<Map<K, V>> splitIntoChunks(Map<K, V> map, int chunkSize) {
        List<Map<K, V>> chunks = new ArrayList<>();
        Map<K, V> currentChunk = new LinkedHashMap<>();
        int count = 0;

        for (Map.Entry<K, V> entry : map.entrySet()) {
            currentChunk.put(entry.getKey(), entry.getValue());
            count++;

            if (count % chunkSize == 0) {
                chunks.add(currentChunk);
                currentChunk = new LinkedHashMap<>();
            }
        }

        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk);
        }

        return chunks;
    }

    private void updateStockMarketPrice(Map<String, Stock> stocks) {
        try {
            ZonedDateTime now = ZonedDateTime.now();
            String tickers = String.join(",", stocks.keySet().stream()
                    .filter(s -> !NOT_SUPPORTED_SYMBOLS.contains(s))
                    .collect(Collectors.toSet()));
            log.info("Fetching data for : {}", tickers);
            Map<String, StockQuote> stockQuotes = twelveDataService.fetchStockQuotes(tickers);
            log.info("Fetched. {}", stockQuotes.values().stream()
                    .map(q -> String.format("%s.%s: %.2f", q.getSymbol(), q.getCurrency(), q.getClose())).collect(Collectors.joining(",")));
            stocks.forEach((ticker, stock) -> {
                StockQuote stockQuote = stockQuotes.get(ticker);
                if (stockQuote == null) {
                    return;
                }
                stock.setMarketPrice(stockQuote.getClose());
                stock.setDayOpenPrice(stockQuote.getOpen());
                stock.setProfit(stock.getAmount() * (stock.getMarketPrice() - stock.getOpenPrice()));
                stock.setUpdatedDate(now);
            });
            stockRepository.saveAll(stocks.values());
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch from TwelveDataService", e);
        }
    }

    public void syncStocks() {
        Map<String, List<OpenedPosition>> openedPositions = openedPositionRepository.findAll().stream()
                .collect(Collectors.groupingBy(OpenedPosition::getSymbol));

        Map<String, Stock> stocks = stockRepository.findAll().stream()
                .collect(Collectors.toMap(Stock::getSymbol, Function.identity(), (a, b) -> b, LinkedHashMap::new));

        try {
            ZonedDateTime now = ZonedDateTime.now();
            openedPositions.forEach((symbol, positions) -> {
                Stock stock = stocks.get(symbol);
                if (stock == null) {
                    return;
                }
                positions.forEach(position -> {
                    position.setMarketPrice(stock.getMarketPrice());
                    position.setProfit(position.getVolume() * (position.getMarketPrice() - position.getOpenPrice()));
                });
                stock.setSyncDate(now);
                openedPositionRepository.saveAll(positions);
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch from TwelveDataService", e);
        }
    }

    public void fullPortfolioUpdate() {
        createStocks();   // ensure a Stock row exists for every quotable holding (idempotent)
        updateStocks();
//        syncStocks();
        syncIbkrPositions();
        historyService.saveHistory();
    }

    /**
     * Applies the latest fetched market prices to IBKR open positions so their unrealized P/L
     * reflects current value (XTB positions keep their imported snapshot and are left untouched).
     */
    public void syncIbkrPositions() {
        List<OpenedPosition> positions = openedPositionRepository.findAllByAccount(IBKR_ACCOUNT);
        if (positions.isEmpty()) {
            return;
        }
        Map<String, Stock> stocksBySymbol = stockRepository.findAll().stream()
                .collect(Collectors.toMap(Stock::getSymbol, Function.identity(), (a, b) -> b));

        for (OpenedPosition position : positions) {
            Stock stock = stocksBySymbol.get(position.getSymbol());
            if (stock == null || stock.getMarketPrice() == null || stock.getMarketPrice() == 0.0) {
                continue; // no quote resolved -> keep cost-based value
            }
            double openPrice = position.getOpenPrice() != null ? position.getOpenPrice() : 0.0;
            position.setMarketPrice(stock.getMarketPrice());
            position.setProfit(position.getVolume() * (stock.getMarketPrice() - openPrice));
        }
        openedPositionRepository.saveAll(positions);

        // Re-value the IBKR account equity at market = cash balance + market value of holdings,
        // so the dashboard Balance reflects current value (not import-time cost basis).
        accountSummaryRepository.findById(IBKR_ACCOUNT).ifPresent(summary -> {
            double cash = summary.getBalance() != null ? summary.getBalance() : 0.0;
            double marketValue = positions.stream()
                    .mapToDouble(p -> (p.getVolume() != null ? p.getVolume() : 0.0)
                            * (p.getMarketPrice() != null ? p.getMarketPrice() : 0.0))
                    .sum();
            summary.setEquity(Math.round((cash + marketValue) * 100.0) / 100.0);
            summary.setUpdatedAt(ZonedDateTime.now());
            accountSummaryRepository.save(summary);
        });
    }
}
