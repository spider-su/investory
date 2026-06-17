package com.example.demo.services;

import com.example.demo.infrastructure.CashOperationType;
import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.repository.*;
import com.example.demo.services.currency.CurrencyRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class HistoryService {
    private final CurrencyRateService currencyRateService;
    private final OpenedPositionRepository openedPositionRepository;
    private final ClosedPositionRepository closedPositionRepository;
    private final CashOperationRepository cashOperationRepository;
    private final StockRepository stockRepository;
    private final OpenPositionHistoryRepository openPositionHistoryRepository;
    private final PortfolioHistoryRepository portfolioHistoryRepository;

    static final long MY_PORTFOLIO = 1L;
    private static final CurrencyType BASE_CURRENCY = CurrencyType.USD;

    private static long nzId(OpenPositionHistory history) {
        return history.getId() == null ? 0L : history.getId();
    }

    private static double nz(Double value) {
        return value == null ? 0.0 : value;
    }

    public Collection<OpenPositionHistory> saveHistory() {
        Map<String, List<OpenedPosition>> openedPositions = openedPositionRepository.findAll().stream()
                .collect(Collectors.groupingBy(OpenedPosition::getSymbol));
        Map<String, Stock> stocks = stockRepository.findAll().stream()
                .collect(Collectors.toMap(Stock::getSymbol, Function.identity(), (a, b) -> b, LinkedHashMap::new));

        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime midnight = now.toLocalDate().atStartOfDay(ZoneId.systemDefault());
        Map<String, OpenPositionHistory> positionHistory = openPositionHistoryRepository.findAllAfterDate(midnight).stream()
                .collect(Collectors.toMap(
                        OpenPositionHistory::getSymbol,
                        Function.identity(),
                        // Same-day duplicates can exist for a symbol; keep the most recently created row.
                        (a, b) -> nzId(a) >= nzId(b) ? a : b,
                        LinkedHashMap::new));
        PortfolioHistory portfolioHistory = portfolioHistoryRepository.findOneAfterDate(midnight).orElse(new PortfolioHistory());

        // Single base-currency accumulator: open net + closed net + dividends. Replaces the
        // previous "build a Portfolio DTO just to track a running double" pattern.
        double total = 0.0;
        for (Map.Entry<String, List<OpenedPosition>> entry : openedPositions.entrySet()) {
            String symbol = entry.getKey();
            List<OpenedPosition> positions = entry.getValue();
            OpenPositionHistory history = positionToHistory(symbol, positionHistory, positions, stocks, now);
            double netOpen = positions.stream()
                    .mapToDouble(p -> nz(p.getProfit()) + nz(p.getCommission()))
                    .sum();
            total += currencyRateService.convertToBaseCurrency(netOpen, BASE_CURRENCY, history.getCurrency());
        }
        openPositionHistoryRepository.saveAll(positionHistory.values());

        Map<CurrencyType, List<ClosedPosition>> closedPositions = closedPositionRepository.findAll().stream()
                .collect(Collectors.groupingBy(ClosedPosition::getCurrency));
        for (Map.Entry<CurrencyType, List<ClosedPosition>> entry : closedPositions.entrySet()) {
            double netClosed = entry.getValue().stream()
                    .mapToDouble(p -> nz(p.getProfit()) + nz(p.getCommission()))
                    .sum();
            total += currencyRateService.convertToBaseCurrency(netClosed, BASE_CURRENCY, entry.getKey());
        }

        Map<CurrencyType, List<CashOperation>> cashOperations = cashOperationRepository.findAll().stream()
                .collect(Collectors.groupingBy(CashOperation::getCurrency));
        for (Map.Entry<CurrencyType, List<CashOperation>> entry : cashOperations.entrySet()) {
            double dividends = entry.getValue().stream()
                    .filter(op -> op.getType() == CashOperationType.DIVIDEND)
                    .mapToDouble(op -> nz(op.getAmount()))
                    .sum();
            total += currencyRateService.convertToBaseCurrency(dividends, BASE_CURRENCY, entry.getKey());
        }

        portfolioHistory.setPortfolioId(MY_PORTFOLIO);
        portfolioHistory.setCurrency(BASE_CURRENCY);
        if (portfolioHistory.getOpenTotal() == null) {
            portfolioHistory.setOpenTotal(total);
        } else {
            portfolioHistory.setCloseTotal(total);
        }
        portfolioHistory.setDate(now);
        portfolioHistoryRepository.save(portfolioHistory);
        return positionHistory.values();
    }

    private OpenPositionHistory positionToHistory(String symbol, Map<String, OpenPositionHistory> positionHistory, List<OpenedPosition> positions, Map<String, Stock> stocks, ZonedDateTime now) {
        OpenPositionHistory history = positionHistory.get(symbol);
        if (history == null) {
            history = new OpenPositionHistory();
            OpenedPosition position = CollectionUtils.firstElement(positions);
            history.setSymbol(symbol);
            history.setCurrency(position.getCurrency());
            history.setAmount(positions.stream().map(OpenedPosition::getVolume).reduce(Double::sum).orElse(0.0));
            history.setDate(now);
            positionHistory.put(symbol, history);
        }
        // Capture a genuine market-open and market-close price for the day so the
        // dashboard shows real intraday movement instead of the same value twice.
        double openCostBasis = getOpenPrice(positions);
        double dayOpenPrice = getDayOpenPrice(symbol, positions, stocks);
        double dayClosePrice = getMarketPrice(symbol, positions, stocks);
        history.setOpenPrice(dayOpenPrice);
        history.setOpenProfit((dayOpenPrice - openCostBasis) * history.getAmount());
        history.setClosePrice(dayClosePrice);
        history.setCloseProfit((dayClosePrice - openCostBasis) * history.getAmount());
        return history;
    }

    private static double getDayOpenPrice(String symbol, List<OpenedPosition> positions, Map<String, Stock> stocks) {
        Stock stock = stocks.get(symbol);
        if (stock != null && stock.getDayOpenPrice() != null) {
            return stock.getDayOpenPrice();
        }
        // Fall back to the latest market price if no distinct open price is available.
        return getMarketPrice(symbol, positions, stocks);
    }

    private static double getMarketPrice(String symbol, List<OpenedPosition> positions, Map<String, Stock> stocks) {
        Stock stock = stocks.get(symbol);
        // The stocks row can exist without a market price (e.g. just-seeded by createStocks before
        // the first updateStocks run). Fall through to the position-level price in that case
        // instead of NPE-ing on the unboxing below.
        if (stock != null && stock.getMarketPrice() != null) {
            return stock.getMarketPrice();
        }
        double totalVolume = positions.stream()
                .map(OpenedPosition::getVolume)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        if (totalVolume == 0.0) {
            return 0.0;
        }
        double weightedSum = positions.stream()
                .filter(position -> position.getMarketPrice() != null && position.getVolume() != null)
                .mapToDouble(position -> position.getMarketPrice() * position.getVolume())
                .sum();
        return weightedSum / totalVolume;
    }

    private static double getOpenPrice(List<OpenedPosition> positions) {
        double totalVolume = positions.stream()
                .map(OpenedPosition::getVolume)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        if (totalVolume == 0.0) {
            return 0.0;
        }
        double weightedSum = positions.stream()
                .filter(position -> position.getOpenPrice() != null && position.getVolume() != null)
                .mapToDouble(position -> position.getOpenPrice() * position.getVolume())
                .sum();
        return weightedSum / totalVolume;
    }
}


