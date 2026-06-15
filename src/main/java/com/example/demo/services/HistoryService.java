package com.example.demo.services;

import com.example.demo.data.CashOperationType;
import com.example.demo.data.CurrencyType;
import com.example.demo.data.repository.*;
import com.example.demo.services.models.Portfolio;
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

    final static long MY_PORTFOLIO = 1L;

    private static long nzId(OpenPositionHistory history) {
        return history.getId() == null ? 0L : history.getId();
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

        Portfolio portfolio = new Portfolio();
        portfolio.setTotal(0);
        portfolio.setBaseCurrency(CurrencyType.USD);
        openedPositions.forEach((symbol, positions) -> {
            OpenPositionHistory history = positionToHistory(symbol, positionHistory, positions, stocks, now);
            portfolio.setTotal(portfolio.getTotal() + currencyRateService.convertToBaseCurrency(positions.stream()
                     .map(openedPosition -> openedPosition.getProfit() + openedPosition.getCommission())
                            .reduce(Double::sum).orElse(0.0), portfolio.getBaseCurrency(), history.getCurrency()));
        });
        openPositionHistoryRepository.saveAll(positionHistory.values());

        Map<CurrencyType, List<ClosedPosition>> closedPositions = closedPositionRepository.findAll().stream()
                .collect(Collectors.groupingBy(ClosedPosition::getCurrency));

        closedPositions.forEach((currency, positions) -> {
            Double profit = positions.stream().map(closedPosition -> closedPosition.getProfit() + closedPosition.getCommission()).reduce(Double::sum).orElse(0.0);
            portfolio.setTotal(portfolio.getTotal() + currencyRateService.convertToBaseCurrency(profit, portfolio.getBaseCurrency(), currency));
        });

        Map<CurrencyType, List<CashOperation>> cashOperations = cashOperationRepository.findAll().stream()
                .collect(Collectors.groupingBy(CashOperation::getCurrency));
        cashOperations.forEach((currency, operations) -> {
            Double dividends = operations.stream()
                    .filter(cashOperation -> cashOperation.getType() == CashOperationType.DIVIDEND)
                    .map(CashOperation::getAmount)
                    .reduce(Double::sum)
                    .orElse(0.0);
            portfolio.setTotal(portfolio.getTotal() + currencyRateService.convertToBaseCurrency(dividends, portfolio.getBaseCurrency(), currency));
        });

        portfolioHistory.setPortfolioId(MY_PORTFOLIO);
        portfolioHistory.setCurrency(CurrencyType.USD);
        if (portfolioHistory.getOpenTotal() == null) {
            portfolioHistory.setOpenTotal(portfolio.getTotal());
        } else {
            portfolioHistory.setCloseTotal(portfolio.getTotal());
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
        if (stock != null) {
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
