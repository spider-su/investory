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
    private final AccountSummaryRepository accountSummaryRepository;


    private static double nz(Double value) {
        return value == null ? 0.0 : value;
    }

    /**
     * Whether a deposit/withdrawal represents real external cash funding rather than an
     * internal sub-account transfer or a currency conversion (which the broker also books
     * as Deposit/Withdraw rows but are not actual income/outflow for the portfolio).
     */
    private static boolean isExternalFunding(CashOperation operation) {
        String comment = operation.getComment();
        if (comment == null) {
            return true;
        }
        String lower = comment.toLowerCase();
        // Exclude internal movements only: XTB sub-account transfers ("Transfer from X to Y")
        // and FX conversions. IBKR real funding reads "Electronic Fund Transfer" and must count.
        return !(lower.contains("currency conversion")
                || lower.contains("transfer from")
                || lower.contains("transfer to"));
    }

    public Portfolio calculateTotalProfitLoss() {
        Map<CurrencyType, List<ClosedPosition>> closedPositions = closedPositionRepository.findAll().stream()
                .collect(Collectors.groupingBy(ClosedPosition::getCurrency));

        Portfolio portfolio = new Portfolio();
        closedPositions.forEach((currency, positions) -> {
            Double profit = positions.stream().map(p -> nz(p.getProfit()) + nz(p.getCommission()) + nz(p.getSwap())).reduce(Double::sum).orElse(0.0);
            portfolio.getProfitByCurrency().merge(currency, profit, Double::sum);
            portfolio.setTotalProfitInBase(portfolio.getTotalProfitInBase() + currencyRateService.convertToBaseCurrency(profit, portfolio.getBaseCurrency(), currency));
        });

        Map<CurrencyType, List<OpenedPosition>> openedPositions = openedPositionRepository.findAll().stream()
                .collect(Collectors.groupingBy(OpenedPosition::getCurrency));
        openedPositions.forEach((currency, positions) -> {
            Double unrealized = positions.stream().map(p -> nz(p.getProfit()) + nz(p.getCommission()) + nz(p.getSwap())).reduce(Double::sum).orElse(0.0);
            portfolio.getUnrealizedByCurrency().merge(currency, unrealized, Double::sum);
            portfolio.setTotalUnrealizedInBase(portfolio.getTotalUnrealizedInBase() + currencyRateService.convertToBaseCurrency(unrealized, portfolio.getBaseCurrency(), currency));
        });

        Map<CurrencyType, List<CashOperation>> cashOperations = cashOperationRepository.findAll().stream()
                .collect(Collectors.groupingBy(CashOperation::getCurrency));
        cashOperations.forEach((currency, positions) -> {
            Double grossDividends = positions.stream()
                    .filter(cashOperation -> cashOperation.getType() == CashOperationType.DIVIDEND)
                    .map(CashOperation::getAmount).reduce(Double::sum).orElse(0.0);
            // Dividend withholding tax (already deducted at source); amounts are negative.
            Double withholdingTax = positions.stream()
                    .filter(cashOperation -> cashOperation.getType() == CashOperationType.WITHHOLDING_TAX)
                    .map(CashOperation::getAmount).reduce(Double::sum).orElse(0.0);
            Double netDividends = grossDividends + withholdingTax;
            portfolio.getDividendsByCurrency().merge(currency, netDividends, Double::sum);
            portfolio.setDividends(portfolio.getDividends() + currencyRateService.convertToBaseCurrency(netDividends, portfolio.getBaseCurrency(), currency));
            portfolio.setDividendTax(portfolio.getDividendTax() + currencyRateService.convertToBaseCurrency(withholdingTax, portfolio.getBaseCurrency(), currency));

            for (CashOperation op : positions) {
                double base = currencyRateService.convertToBaseCurrency(nz(op.getAmount()), portfolio.getBaseCurrency(), currency);
                if (op.getType() == null) {
                    continue;
                }
                switch (op.getType()) {
                    case DEPOSIT:
                        // Only count real external funding, not FX conversions / inter-account transfers.
                        if (isExternalFunding(op)) {
                            portfolio.setDeposits(portfolio.getDeposits() + base);
                        }
                        break;
                    case WITHDRAWAL:
                        if (isExternalFunding(op)) {
                            portfolio.setWithdrawals(portfolio.getWithdrawals() + base);
                        }
                        break;
                    case FREE_FUNDS_INTEREST:
                    case FREE_FUNDS_INTEREST_TAX:
                        portfolio.setInterest(portfolio.getInterest() + base);
                        break;
                    default:
                        break;
                }
            }
        });
        portfolio.setNetDeposits(portfolio.getDeposits() + portfolio.getWithdrawals());

        // Ensure every supported currency (incl. PLN) is represented across all breakdowns,
        // so accounts with no positions/dividends still show a 0 row instead of disappearing.
        for (CurrencyType currency : CurrencyType.values()) {
            portfolio.getProfitByCurrency().putIfAbsent(currency, 0.0);
            portfolio.getUnrealizedByCurrency().putIfAbsent(currency, 0.0);
            portfolio.getDividendsByCurrency().putIfAbsent(currency, 0.0);
        }

        portfolio.setTotal(portfolio.getTotalProfitInBase() + portfolio.getTotalUnrealizedInBase() + portfolio.getDividends());

        // Estimated capital-gains tax ("Belka" 19%) for the CURRENT tax year only, applying
        // loss carry-forward from prior years (Polish rule: losses deductible over the next 5 years).
        int currentYear = java.time.Year.now().getValue();
        Map<Integer, Double> realizedByYear = closedPositionRepository.findAll().stream()
                .filter(p -> p.getCloseTime() != null)
                .collect(Collectors.groupingBy(
                        p -> p.getCloseTime().getYear(),
                        Collectors.summingDouble(p -> currencyRateService.convertToBaseCurrency(
                                nz(p.getProfit()) + nz(p.getCommission()) + nz(p.getSwap()),
                                portfolio.getBaseCurrency(), p.getCurrency()))));

        // Walk years chronologically: loss years feed a pool; gain years consume losses from the
        // previous 5 years (oldest first). Only the current year's resulting tax is reported.
        Map<Integer, Double> lossPool = new TreeMap<>();
        double currentYearTaxable = 0.0;
        double appliedToCurrentYear = 0.0;
        for (Integer year : new TreeSet<>(realizedByYear.keySet())) {
            double net = realizedByYear.getOrDefault(year, 0.0);
            if (net < 0) {
                lossPool.merge(year, -net, Double::sum);
                continue;
            }
            double remainingGain = net;
            for (Map.Entry<Integer, Double> loss : lossPool.entrySet()) {
                if (remainingGain <= 0) {
                    break;
                }
                int lossYear = loss.getKey();
                if (lossYear < year - 5 || lossYear >= year) {
                    continue; // outside the 5-year deduction window
                }
                double use = Math.min(remainingGain, loss.getValue());
                loss.setValue(loss.getValue() - use);
                remainingGain -= use;
                if (year == currentYear) {
                    appliedToCurrentYear += use;
                }
            }
            if (year == currentYear) {
                currentYearTaxable = remainingGain;
            }
        }
        portfolio.setCapitalGainsTax(Math.round(currentYearTaxable * 0.19 * 100.0) / 100.0);
        portfolio.setLossCarryForward(Math.round(appliedToCurrentYear * 100.0) / 100.0);

        // Balance = total assets value (equity) of every account as of the latest import,
        // converted to the base currency.
        double balance = accountSummaryRepository.findAll().stream()
                .mapToDouble(summary -> currencyRateService.convertToBaseCurrency(
                        nz(summary.getEquity()), portfolio.getBaseCurrency(), summary.getCurrency()))
                .sum();
        if (balance == 0.0) {
            // No broker equity snapshot imported yet (account_summaries empty): approximate
            // current assets from the market value of open positions (purchase value + unrealized P/L).
            balance = openedPositionRepository.findAll().stream()
                    .mapToDouble(position -> currencyRateService.convertToBaseCurrency(
                            nz(position.getPurchaseValue()) + nz(position.getProfit()),
                            portfolio.getBaseCurrency(), position.getCurrency()))
                    .sum();
        }
        portfolio.setBalance(balance);

        // Exchange rates for the currencies board (units of each currency per 1 base currency)
        for (CurrencyType currency : CurrencyType.values()) {
            if (currency == portfolio.getBaseCurrency()) {
                continue;
            }
            try {
                portfolio.getExchangeRates().put(currency, currencyRateService.getRate(portfolio.getBaseCurrency(), currency));
            } catch (RuntimeException e) {
                log.warn("No FX rate available for {} -> {}", portfolio.getBaseCurrency(), currency);
            }
        }

        portfolio.setPerformancePerSymbol(calculatePerformancePerInstrument(portfolio.getBaseCurrency()));
        portfolio.setMonthlyPerformance(calculateMonthlyPerformance());
        portfolio.setOpenPositionsFlow(getOpenPositionsFlow());
        return portfolio;
    }

    // 2. Monthly Performance
    public Performance calculateMonthlyPerformance() {
        Performance performance = new Performance();
        List<ClosedPosition> closed = closedPositionRepository.findAll();
        int currentYear = java.time.Year.now().getValue();
        performance.setCalculateMonthlyPerformance(closed.stream()
                .collect(Collectors.groupingBy(
                        position -> monthlyBucketKey(position, currentYear),
                        TreeMap::new, // Use TreeMap to keep the result sorted by year / year-month
                        Collectors.summingDouble(
                                position -> currencyRateService.convertToBaseCurrency(position.getProfit() + position.getCommission(), performance.getBaseCurrency(), position.getCurrency())
                        )
                )));
        performance.setMonthlyOperationsCount(closed.stream()
                .collect(Collectors.groupingBy(
                        position -> monthlyBucketKey(position, currentYear),
                        TreeMap::new,
                        Collectors.counting()
                )));
        performance.setMonthlyCashflow(closed.stream()
                .collect(Collectors.groupingBy(
                        position -> monthlyBucketKey(position, currentYear),
                        TreeMap::new,
                        Collectors.summingDouble(position -> {
                            double volume = position.getVolume() != null ? Math.abs(position.getVolume()) : 0.0;
                            double openPrice = position.getOpenPrice() != null ? Math.abs(position.getOpenPrice()) : 0.0;
                            double closePrice = position.getClosePrice() != null ? Math.abs(position.getClosePrice()) : 0.0;
                            // Cashflow = total traded value of both legs (open + close) of each closed trade.
                            double notional = volume * (openPrice + closePrice);
                            return currencyRateService.convertToBaseCurrency(notional, performance.getBaseCurrency(), position.getCurrency());
                        })
                )));
        performance.setTotalOpen(openedPositionRepository.findAll().stream()
                .map(OpenedPosition::getPurchaseValue).filter(Objects::nonNull).reduce(Double::sum).orElse(0.0));
//        performance.setTotalProfit(closed.stream().map(ClosedPosition::getPurchaseValue).reduce(Double::sum).orElse(0.0));
//        performance.setBase(performance.getTotalOpen() - performance.getTotalProfit());
        return performance;
    }

    /**
     * Buckets a closed position for the monthly performance chart.
     * Positions closed before January 1st of the current year are aggregated by
     * year (e.g. "2024", "2025"), while positions in the current year are kept
     * per-month (e.g. "2026-01").
     */
    private String monthlyBucketKey(ClosedPosition position, int currentYear) {
        int year = position.getCloseTime().getYear();
        if (year < currentYear) {
            return String.format("%d", year);
        }
        return String.format("%d-%02d", year, position.getCloseTime().getMonthValue());
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
        double amount = position.getAmount() != null ? position.getAmount() : 0.0;
        double openPrice = position.getOpenPrice() != null ? position.getOpenPrice() : 0.0;
        // If the market-close snapshot wasn't captured yet, fall back to the open price.
        double closePrice = position.getClosePrice() != null ? position.getClosePrice() : openPrice;
        // Balance (market value) of the open positions at market open / close.
        double openBalance = openPrice * amount;
        double closeBalance = closePrice * amount;
        item.setDayOpen(currencyRateService.convertToBaseCurrency(openBalance, CurrencyType.USD, position.getCurrency()));
        item.setDayClosed(currencyRateService.convertToBaseCurrency(closeBalance, CurrencyType.USD, position.getCurrency()));
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


