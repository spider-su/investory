package com.example.demo.services;

import com.example.demo.data.CashOperationType;
import com.example.demo.data.CurrencyType;
import com.example.demo.data.repository.*;
import com.example.demo.services.models.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PortfolioService {

    private final CurrencyRateService currencyRateService;
    private final ClosedPositionRepository closedPositionRepository;
    private final OpenedPositionRepository openedPositionRepository;
    private final OpenPositionHistoryRepository openPositionHistoryRepository;
    private final CashOperationRepository cashOperationRepository;


    public Portfolio calculateTotalProfitLoss() {
        Map<CurrencyType, List<ClosedPosition>> closedPositions = closedPositionRepository.findAll().stream()
                .collect(Collectors.groupingBy(ClosedPosition::getCurrency));

        Portfolio portfolio = new Portfolio();
        closedPositions.forEach((currency, positions) -> {
            Double profit = positions.stream().map(closedPosition -> closedPosition.getProfit() + closedPosition.getCommission()).reduce(Double::sum).orElse(0.0);
            portfolio.getProfitByCurrency().merge(currency, profit, Double::sum);
            portfolio.setTotalProfitInBase(portfolio.getTotalProfitInBase() + currencyRateService.convertToBaseCurrency(profit, portfolio.getBaseCurrency(), currency));
        });

        Map<CurrencyType, List<OpenedPosition>> openedPositions = openedPositionRepository.findAll().stream()
                .collect(Collectors.groupingBy(OpenedPosition::getCurrency));
        openedPositions.forEach((currency, positions) -> {
            Double unrealized = positions.stream().map(openedPosition -> openedPosition.getProfit() + openedPosition.getCommission()).reduce(Double::sum).orElse(0.0);
            portfolio.getUnrealizedByCurrency().merge(currency, unrealized, Double::sum);
            portfolio.setTotalUnrealizedInBase(portfolio.getTotalUnrealizedInBase() + currencyRateService.convertToBaseCurrency(unrealized, portfolio.getBaseCurrency(), currency));
        });

        Map<CurrencyType, List<CashOperation>> cashOperations = cashOperationRepository.findAll().stream()
                .collect(Collectors.groupingBy(CashOperation::getCurrency));
        cashOperations.forEach((currency, positions) -> {
            Double dividends = positions.stream()
                    .filter(cashOperation -> cashOperation.getType() == CashOperationType.DIVIDEND)
                    .map(CashOperation::getAmount).reduce(Double::sum).orElse(0.0);
            portfolio.getDividendsByCurrency().merge(currency, dividends, Double::sum);
            portfolio.setDividends(portfolio.getDividends() + currencyRateService.convertToBaseCurrency(dividends, portfolio.getBaseCurrency(), currency));
        });

        portfolio.setTotal(portfolio.getTotalProfitInBase() + portfolio.getTotalUnrealizedInBase() + portfolio.getDividends());
        portfolio.setPerformancePerSymbol(calculatePerformancePerInstrument(portfolio.getBaseCurrency()));
        portfolio.setMonthlyPerformance(calculateMonthlyPerformance());
        portfolio.setOpenPositionsFlow(getOpenPositionsFlow());
        return portfolio;
    }

    // 2. Monthly Performance
    public Performance calculateMonthlyPerformance() {
        Performance performance = new Performance();
        List<ClosedPosition> closed = closedPositionRepository.findAll();
        performance.setCalculateMonthlyPerformance(closed.stream()
                .collect(Collectors.groupingBy(
                        position -> String.format("%d-%02d",
                                position.getCloseTime().getYear(),
                                position.getCloseTime().getMonthValue()
                        ),
                        TreeMap::new, // Use TreeMap to keep the result sorted by year-month
                        Collectors.summingDouble(
                                position -> currencyRateService.convertToBaseCurrency(position.getProfit() + position.getCommission(), performance.getBaseCurrency(), position.getCurrency())
                        )
                )));
        performance.setTotalOpen(openedPositionRepository.findAll().stream()
                .map(OpenedPosition::getPurchaseValue).filter(Objects::nonNull).reduce(Double::sum).orElse(0.0));
//        performance.setTotalProfit(closed.stream().map(ClosedPosition::getPurchaseValue).reduce(Double::sum).orElse(0.0));
//        performance.setBase(performance.getTotalOpen() - performance.getTotalProfit());
        return performance;
    }

    // 4. Win Rate (percentage of profitable trades)
    public double calculateWinRate() {
        List<ClosedPosition> closedPositions = closedPositionRepository.findAll();
        long totalPositions = closedPositions.size();
        if (totalPositions == 0) {
            return 0.0;
        }
        long profitablePositions = closedPositions.stream()
                .filter(position -> position.getProfit() > 0)
                .count();

        return (double) profitablePositions / totalPositions * 100;
    }

    // 5. Largest Win / Largest Loss
    public Map<String, Double> calculateLargestWinLoss() {
        List<ClosedPosition> closedPositions = closedPositionRepository.findAll();

        double largestWin = closedPositions.stream()
                .filter(Objects::nonNull)
                .mapToDouble(ClosedPosition::getProfit)
                .filter(profit -> profit > 0)
                .max()
                .orElse(0.0);

        double largestLoss = closedPositions.stream()
                .filter(Objects::nonNull)
                .mapToDouble(ClosedPosition::getProfit)
                .filter(profit -> profit < 0)
                .min()
                .orElse(0.0);

        return Map.of("largestWin", largestWin, "largestLoss", largestLoss);
    }

    public List<InstrumentPerformance> calculatePerformancePerInstrument(CurrencyType baseCurrency) {
        List<ClosedPosition> closedPositions = closedPositionRepository.findAll();
        List<OpenedPosition> openedPositions = openedPositionRepository.findAll();

        Map<String, Double> closedProfits = closedPositions.stream()
                .collect(Collectors.groupingBy(
                        ClosedPosition::getSymbol,
                        Collectors.summingDouble(position -> currencyRateService.convertToBaseCurrency(position.getProfit(), baseCurrency, position.getCurrency()))
                ));

        Map<String, Double> openedProfits = openedPositions.stream()
                .collect(Collectors.groupingBy(
                        OpenedPosition::getSymbol,
                        Collectors.summingDouble(position -> currencyRateService.convertToBaseCurrency(position.getProfit(), baseCurrency, position.getCurrency()))
                ));

        // Merge symbols from both maps
        Set<String> allSymbols = new HashSet<>();
        allSymbols.addAll(closedProfits.keySet());
        allSymbols.addAll(openedProfits.keySet());

        List<InstrumentPerformance> instrumentPerformances = allSymbols.stream()
                .map(symbol -> new InstrumentPerformance(
                        symbol,
                        closedProfits.getOrDefault(symbol, 0.0),
                        openedProfits.getOrDefault(symbol, 0.0),
                        0
                ))
                .peek(s -> s.setTotal(s.getClosedProfit() + s.getUnrealizedProfit()))
                .sorted(Comparator.comparing(InstrumentPerformance::getTotal)) // optional sort
                .collect(Collectors.toList());

        double totalSum = instrumentPerformances.stream()
                .filter(Objects::nonNull)
                .mapToDouble(InstrumentPerformance::getTotal).sum();
        double threshold = totalSum * 0.019;

        List<InstrumentPerformance> major = new ArrayList<>();
        double otherClosed = 0.0;
        double otherUnrealized = 0.0;

        for (InstrumentPerformance dto : instrumentPerformances) {
            if (Math.abs(dto.getTotal()) >= Math.abs(threshold)) {
                major.add(dto);
            } else {
                otherClosed += dto.getClosedProfit();
                otherUnrealized += dto.getUnrealizedProfit();
            }
        }
        major = major.stream()
                .sorted(Comparator.comparingDouble(InstrumentPerformance::getTotal).reversed())
                .collect(Collectors.toList());
        if (otherClosed != 0.0 || otherUnrealized != 0.0) {
            major.add(new InstrumentPerformance("Other", otherClosed, otherUnrealized, otherClosed + otherUnrealized));
        }

        return major;
    }

    // 7. Cash Flow Over Time (Daily, Monthly)
    public Map<String, Double> calculateCashFlowOverTime(CurrencyType baseCurrency) {
        List<ClosedPosition> closedPositions = closedPositionRepository.findAll();

        return closedPositions.stream()
                .collect(Collectors.groupingBy(position -> position.getCloseTime().toString(),
                        Collectors.summingDouble(position -> currencyRateService.convertToBaseCurrency(position.getProfit(), baseCurrency, position.getCurrency()))));
    }

    public Map<String, OpenPositionsPerformance> getOpenPositionsFlow() {
        ZonedDateTime days30 = ZonedDateTime.now().minusDays(30);
        Map<LocalDate, List<OpenPositionHistory>> history = openPositionHistoryRepository.findAllAfterDate(days30).stream()
                .collect(Collectors.groupingBy(openPositionHistory -> openPositionHistory.getDate().toLocalDate()));

        return history.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(entry -> entry.getKey().toString(),
                        entry -> calculatePerformanceItem(entry.getValue()),
                        (e1, e2) -> e1, LinkedHashMap::new));
    }

    private OpenPositionsPerformance calculatePerformanceItem(List<OpenPositionHistory> positions) {
        OpenPositionsPerformance positionsPerformance = new OpenPositionsPerformance();

        List<OpenPositionsPerformanceItem> positionsPerformanceItems = positions.stream().map(this::toPositionItem).collect(Collectors.toList());
        positionsPerformance.setPositions(positionsPerformanceItems);

        positionsPerformance.setTotalOpen(positionsPerformanceItems.stream().map(OpenPositionsPerformanceItem::getDayOpen)
                .reduce(Double::sum).orElse(0.0));
        positionsPerformance.setTotalClosed(positionsPerformanceItems.stream().map(OpenPositionsPerformanceItem::getDayClosed)
                .reduce(Double::sum).orElse(0.0));
        return positionsPerformance;
    }

    private OpenPositionsPerformanceItem toPositionItem(OpenPositionHistory position) {
        OpenPositionsPerformanceItem item = new OpenPositionsPerformanceItem();
        item.setSymbol(position.getSymbol());
        double openProfit = position.getOpenProfit() != null ? position.getOpenProfit() : 0.0;
        double closeProfit = position.getCloseProfit() != null ? position.getCloseProfit() : 0.0;
        item.setDayOpen(currencyRateService.convertToBaseCurrency(openProfit, CurrencyType.USD, position.getCurrency()));
        item.setDayClosed(currencyRateService.convertToBaseCurrency(closeProfit, CurrencyType.USD, position.getCurrency()));
        return item;
    }

    // 8. Dividends Received (if modeled)
//    public double calculateDividendsReceived() {
//        List<ClosedPosition> closedPositions = closedPositionRepository.findAll();
//
//        return closedPositions.stream()
//                .filter(ClosedPosition::isDividend)
//                .mapToDouble(ClosedPosition::getDividendAmount)
//                .sum();
//    }



}


