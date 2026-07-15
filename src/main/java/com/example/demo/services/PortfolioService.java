package com.example.demo.services;

import com.example.demo.infrastructure.CashOperationType;
import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.repository.*;
import com.example.demo.infrastructure.repository.account.Account;
import com.example.demo.infrastructure.repository.account.AccountMonthlyPerformance;
import com.example.demo.infrastructure.repository.account.AccountMonthlyPerformanceRepository;
import com.example.demo.infrastructure.repository.account.AccountRepository;
import com.example.demo.infrastructure.repository.account.AccountStatistics;
import com.example.demo.infrastructure.repository.account.AccountStatisticsRepository;
import com.example.demo.infrastructure.repository.portfolio.PortfolioAssetAllocation;
import com.example.demo.infrastructure.repository.portfolio.PortfolioAssetAllocationRepository;
import com.example.demo.infrastructure.repository.portfolio.PortfolioCurrencyBreakdown;
import com.example.demo.infrastructure.repository.portfolio.PortfolioCurrencyBreakdownRepository;
import com.example.demo.infrastructure.repository.portfolio.PortfolioKpiSummary;
import com.example.demo.infrastructure.repository.portfolio.PortfolioKpiSummaryRepository;
import com.example.demo.services.currency.CurrencyRateService;
import com.example.demo.services.models.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PortfolioService {

    /**
     * Symbols whose total |P/L| falls below {@code OTHER_BUCKET_RATIO * |grand total|} are
     * collapsed into a single "Other" row in the per-instrument ranking, so the chart isn't
     * dominated by long-tail noise.
     */
    private static final double OTHER_BUCKET_RATIO = 0.019;

    private final CurrencyRateService currencyRateService;
    private final ClosedPositionRepository closedPositionRepository;
    private final OpenedPositionRepository openedPositionRepository;
    private final CashOperationRepository cashOperationRepository;
    private final AccountRepository accountRepository;
    private final AccountStatisticsRepository accountStatisticsRepository;
    private final AccountMonthlyPerformanceRepository accountMonthlyPerformanceRepository;
    private final PortfolioAssetAllocationRepository portfolioAssetAllocationRepository;
    private final PortfolioCurrencyBreakdownRepository portfolioCurrencyBreakdownRepository;
    private final PortfolioKpiSummaryRepository portfolioKpiSummaryRepository;
    private final TaxCalculator taxCalculator;
    private final CashFlowAggregator cashFlowAggregator;


    private static double nz(Double value) {
        return value == null ? 0.0 : value;
    }

    public Portfolio calculateTotalProfitLoss() {
        Portfolio portfolio = new Portfolio();
        boolean kpiApplied = applyKpiSummary(portfolio);
        if (kpiApplied) {
            applyCashFlowSupplement(portfolio);
        } else {
            applyCalculatedTotals(portfolio);
        }
        portfolio.setAccountBalances(calculateAccountBalances(portfolio.getBaseCurrency()));
        portfolio.setOpenPositionValues(calculateOpenPositionValues());
        portfolio.setDividendGainers(calculateDividendGainers(portfolio.getBaseCurrency()));
        if (!applyPortfolioCurrencyBreakdowns(portfolio, !kpiApplied)) {
            boolean usedAccountStatistics = applyAccountStatisticsCurrencyBreakdowns(portfolio, !kpiApplied);
            if (!usedAccountStatistics) {
                applyOpenPositionUnrealizedCurrencyBreakdown(portfolio, !kpiApplied);
            }
        }
        ensureCurrencyRows(portfolio);
        applyInvestmentProfitFromWealth(portfolio);

        // Estimated capital-gains tax ("Belka" 19%) for the CURRENT tax year only, applying
        // loss carry-forward from prior years (Polish rule: losses deductible over the next 5 years).
        TaxCalculator.TaxSummary tax = taxCalculator.calculate(
                closedPositionRepository.findAll(), portfolio.getBaseCurrency());
        portfolio.setCapitalGainsTax(tax.capitalGainsTax());
        portfolio.setLossCarryForward(tax.lossCarryForward());

        // Exchange rates for the currencies board (units of each currency per 1 base currency)
        for (CurrencyType currency : CurrencyType.values()) {
            if (currency == portfolio.getBaseCurrency()) {
                continue;
            }
            OptionalDouble rate = currencyRateService.findRate(portfolio.getBaseCurrency(), currency);
            if (rate.isPresent()) {
                portfolio.getExchangeRates().put(currency, rate.getAsDouble());
            } else {
                log.warn("No FX rate available for {} -> {}", portfolio.getBaseCurrency(), currency);
            }
        }

        portfolio.setPerformancePerSymbol(calculatePerformancePerInstrument(portfolio.getBaseCurrency()));
        portfolio.setMonthlyPerformance(calculateMonthlyPerformance());
        return portfolio;
    }

    private boolean applyKpiSummary(Portfolio portfolio) {
        Optional<PortfolioKpiSummary> summary = portfolioKpiSummaryRepository.findAll().stream().findFirst();
        if (summary.isEmpty()) {
            return false;
        }

        PortfolioKpiSummary kpi = summary.get();
        CurrencyType baseCurrency = kpi.getBaseCurrency();
        double realized = nz(kpi.getTotalRealizedProfit());
        double unrealized = nz(kpi.getTotalUnrealizedProfit());
        double dividends = nz(kpi.getTotalDividends());
        double deposits = nz(kpi.getTotalDeposits());
        double netDeposits = nz(kpi.getNetDeposits());
        double balance = nz(kpi.getTotalEquity());
        double cash = nz(kpi.getTotalCash());

        portfolio.setBaseCurrency(baseCurrency);
        portfolio.setRealizedProfit(realized);
        portfolio.setUnrealizedProfit(unrealized);
        portfolio.setDividends(dividends);
        portfolio.setDeposits(deposits);
        portfolio.setNetDeposits(netDeposits);
        portfolio.setWithdrawals(netDeposits - deposits);
        portfolio.setCash(cash);
        portfolio.setBalance(balance);
        portfolio.setRoi(netDeposits > 0 ? (balance - netDeposits) / netDeposits * 100.0 : 0.0);

        portfolio.getRealizedByCurrency().put(baseCurrency, realized);
        portfolio.getUnrealizedByCurrency().put(baseCurrency, unrealized);
        portfolio.getDividendsByCurrency().put(baseCurrency, dividends);
        return true;
    }

    private void applyCashFlowSupplement(Portfolio portfolio) {
        List<CashOperation> cashOperations = cashOperationRepository.findAll();
        if (CollectionUtils.isEmpty(cashOperations)) {
            return;
        }
        CashFlowAggregator.CashFlowSummary cashFlow = cashFlowAggregator.aggregate(
                cashOperations, portfolio.getBaseCurrency());
        portfolio.setDividendTax(cashFlow.dividendTax());
        portfolio.setInterest(cashFlow.interest());
    }

    private void applyCalculatedTotals(Portfolio portfolio) {
        List<ClosedPosition> closedPositions = closedPositionRepository.findAll();
        List<CashOperation> cashOperations = cashOperationRepository.findAll();

        // ── Realized P/L from imported closed positions ──────────────────────
        for (ClosedPosition position : closedPositions) {
            CurrencyType currency = position.getCurrency() != null
                ? position.getCurrency() : portfolio.getBaseCurrency();
            LocalDate rateDate = position.getCloseTime() != null
                ? position.getCloseTime().toLocalDate() : LocalDate.now();
            double profit = nz(position.getProfit()) + nz(position.getCommission()) + nz(position.getSwap());
            portfolio.getRealizedByCurrency().merge(currency, profit, Double::sum);
            portfolio.setRealizedProfit(
                portfolio.getRealizedProfit()
                    + currencyRateService.convertToBaseCurrency(
                        profit, portfolio.getBaseCurrency(), currency, rateDate));
        }

        // ── Unrealized P/L (live from open positions — always real-time) ─────
        Map<CurrencyType, List<OpenedPosition>> openedPositions = openedPositionRepository.findAll().stream()
                .collect(Collectors.groupingBy(OpenedPosition::getCurrency));
        openedPositions.forEach((currency, positions) -> {
            Double unrealized = positions.stream().map(p -> nz(p.getProfit()) + nz(p.getCommission()) + nz(p.getSwap())).reduce(Double::sum).orElse(0.0);
            portfolio.getUnrealizedByCurrency().merge(currency, unrealized, Double::sum);
            portfolio.setUnrealizedProfit(portfolio.getUnrealizedProfit() + currencyRateService.convertToBaseCurrency(unrealized, portfolio.getBaseCurrency(), currency, java.time.LocalDate.now()));
        });

        // ── Dividends from imported cash operations ──────────────────────────
        double dividendsTotal = 0.0;
        for (CashOperation cashOperation : cashOperations) {
            if (cashOperation.getType() == CashOperationType.DIVIDEND) {
                CurrencyType currency = cashOperation.getCurrency() != null
                    ? cashOperation.getCurrency() : portfolio.getBaseCurrency();
                LocalDate rateDate = cashOperation.getDate() != null
                    ? cashOperation.getDate().toLocalDate() : LocalDate.now();
                double amount = nz(cashOperation.getAmount());
                double amountInBase = currencyRateService.convertToBaseCurrency(
                    amount, portfolio.getBaseCurrency(), currency, rateDate);
                dividendsTotal += amountInBase;
                portfolio.getDividendsByCurrency().merge(currency, amount, Double::sum);
            }
        }

        // Fall back to cash_operations for deposits/withdrawals/interest/tax.
        CashFlowAggregator.CashFlowSummary cashFlow = cashFlowAggregator.aggregate(
                cashOperations, portfolio.getBaseCurrency());
        portfolio.setDeposits(cashFlow.deposits());
        portfolio.setWithdrawals(cashFlow.withdrawals());
        portfolio.setNetDeposits(cashFlow.netDeposits());
        portfolio.setInterest(cashFlow.interest());
        // Use summary-derived dividends when available, fall back to cashflow aggregator
        portfolio.setDividends(dividendsTotal > 0 ? dividendsTotal : cashFlow.dividends());
        portfolio.setDividendTax(cashFlow.dividendTax());
        if (dividendsTotal == 0.0) {
            cashFlow.dividendsByCurrency().forEach(
                (currency, amount) -> portfolio.getDividendsByCurrency().merge(currency, amount, Double::sum));
        }

        // Balance breakdown from account_statistics.
        List<AccountStatistics> stats = accountStatisticsRepository.findAll();
        double marketValue;
        double cash;
        double balance;
        if (!stats.isEmpty()) {
            List<AccountStatistics> activeStats = stats.stream()
                    .filter(s -> nz(s.getCashBalance()) + nz(s.getMarketValue()) > 0.005)
                    .toList();
            marketValue = activeStats.stream().mapToDouble(s -> nz(s.getMarketValue())).sum();
            cash = activeStats.stream().mapToDouble(s -> nz(s.getCashBalance())).sum();
            balance = marketValue + cash;
        } else {
            balance = openedPositionRepository.findAll().stream()
                    .mapToDouble(position -> currencyRateService.convertToBaseCurrency(
                            nz(position.getPurchaseValue()) + nz(position.getProfit()),
                            portfolio.getBaseCurrency(), position.getCurrency(), java.time.LocalDate.now()))
                    .sum();
            marketValue = balance;
            cash = 0.0;
        }
        portfolio.setBalance(balance);
        portfolio.setCash(cash);
        double netDep = portfolio.getNetDeposits();
        portfolio.setRoi(netDep > 0 ? (balance - netDep) / netDep * 100.0 : 0.0);
    }

    private void applyInvestmentProfitFromWealth(Portfolio portfolio) {
        double netDeposits = portfolio.getNetDeposits();
        double investmentProfit = portfolio.getBalance() - netDeposits;
        portfolio.setTotalProfit(investmentProfit);
        portfolio.setRoi(netDeposits > 0 ? investmentProfit / netDeposits * 100.0 : 0.0);
    }

    private void ensureCurrencyRows(Portfolio portfolio) {
        // Ensure every supported currency (incl. PLN) is represented across all breakdowns,
        // so accounts with no positions/dividends still show a 0 row instead of disappearing.
        for (CurrencyType currency : CurrencyType.values()) {
            portfolio.getRealizedByCurrency().putIfAbsent(currency, 0.0);
            portfolio.getUnrealizedByCurrency().putIfAbsent(currency, 0.0);
            portfolio.getDividendsByCurrency().putIfAbsent(currency, 0.0);
        }
    }

    private List<AccountBalance> calculateAccountBalances(CurrencyType baseCurrency) {
        List<AccountStatistics> stats = accountStatisticsRepository.findAll();
        if (!CollectionUtils.isEmpty(stats)) {
            Map<Long, Account> accountsById = accountsById(stats.stream()
                    .map(AccountStatistics::getAccountId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet()));
            return stats.stream()
                    .sorted(Comparator.comparing(AccountStatistics::getAccountId))
                    .filter(this::hasDashboardAccountSurface)
                    .map(stat -> {
                        double balance = nz(stat.getCashBalance()) + nz(stat.getMarketValue());
                        double netDeposit = nz(stat.getNetDeposit());
                        Account account = accountsById.get(stat.getAccountId());
                        CurrencyType localCurrency = account.getCurrency();
                        return new AccountBalance(
                                stat.getAccountId(),
                                account.getName(),
                                netDeposit,
                                profitLossPercent(balance, netDeposit),
                                balance,
                                nz(stat.getCashBalance()),
                                localCurrency,
                                localBalance(balance, baseCurrency, localCurrency, LocalDate.now()));
                    })
                    .toList();
        }

        return List.of();
    }

    private boolean hasDashboardAccountSurface(AccountStatistics stat) {
        return Math.abs(nz(stat.getCashBalance()) + nz(stat.getMarketValue())) > 0.005
                || Math.abs(nz(stat.getNetDeposit())) > 0.005;
    }

    private Double profitLossPercent(double balance, double netDeposit) {
        return Math.abs(netDeposit) > 0.005
                ? (balance - netDeposit) / netDeposit * 100.0
                : null;
    }

    private List<OpenPositionValue> calculateOpenPositionValues() {
        List<PortfolioAssetAllocation> allocations = portfolioAssetAllocationRepository.findAll();
        if (CollectionUtils.isEmpty(allocations)) {
            return List.of();
        }

        double totalValue = allocations.stream()
                .mapToDouble(allocation -> nz(allocation.getTotalValueInBaseCurrency()))
                .sum();

        return allocations.stream()
                .filter(allocation -> Math.abs(nz(allocation.getTotalValueInBaseCurrency())) > 0.005)
                .sorted(Comparator.comparing(
                        (PortfolioAssetAllocation allocation) -> nz(allocation.getTotalValueInBaseCurrency()))
                        .reversed())
                .map(allocation -> new OpenPositionValue(
                        allocation.getAssetId(),
                        nz(allocation.getTotalVolume()),
                        nz(allocation.getCostBasisInBaseCurrency()),
                        nz(allocation.getTotalVolume()) > 0.005
                                ? nz(allocation.getCostBasisInBaseCurrency()) / nz(allocation.getTotalVolume())
                                : 0.0,
                        nz(allocation.getTotalValueInBaseCurrency()),
                        nz(allocation.getUnrealizedPlInBaseCurrency()),
                        positionProfitLossPercent(
                                nz(allocation.getUnrealizedPlInBaseCurrency()),
                                nz(allocation.getCostBasisInBaseCurrency())),
                        allocation.getBaseCurrency(),
                        Math.abs(totalValue) > 0.005
                                ? nz(allocation.getTotalValueInBaseCurrency()) / totalValue * 100.0
                                : 0.0))
                .toList();
    }

    private Double positionProfitLossPercent(double unrealized, double costBase) {
        return Math.abs(costBase) > 0.005
                ? unrealized / costBase * 100.0
                : null;
    }

    private List<DividendGainer> calculateDividendGainers(CurrencyType baseCurrency) {
        List<CashOperation> operations = cashOperationRepository.findAll();
        if (CollectionUtils.isEmpty(operations)) {
            return List.of();
        }

        Map<String, Double> dividendsBySymbol = operations.stream()
                .filter(operation -> operation.getType() == CashOperationType.DIVIDEND
                        || operation.getType() == CashOperationType.WITHHOLDING_TAX)
                .filter(operation -> operation.getSymbol() != null && !operation.getSymbol().isBlank())
                .collect(Collectors.groupingBy(
                        CashOperation::getSymbol,
                        Collectors.summingDouble(operation -> {
                            CurrencyType currency = operation.getCurrency() != null
                                    ? operation.getCurrency()
                                    : baseCurrency;
                            LocalDate rateDate = operation.getDate() != null
                                    ? operation.getDate().toLocalDate()
                                    : LocalDate.now();
                            return currencyRateService.convertToBaseCurrency(
                                    nz(operation.getAmount()), baseCurrency, currency, rateDate);
                        })));

        if (dividendsBySymbol.isEmpty()) {
            return List.of();
        }

        List<DividendGainer> sortedRows = dividendsBySymbol.entrySet().stream()
                .filter(entry -> Math.abs(entry.getValue()) > 0.005)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(entry -> new DividendGainer(entry.getKey(), entry.getValue()))
                .toList();

        if (sortedRows.size() <= 10) {
            return sortedRows;
        }

        List<DividendGainer> topRows = new ArrayList<>(sortedRows.subList(0, 9));
        double otherDividends = sortedRows.subList(9, sortedRows.size()).stream()
                .mapToDouble(DividendGainer::getDividends)
                .sum();
        topRows.add(new DividendGainer("Other", otherDividends));
        return topRows;
    }

    private boolean applyAccountStatisticsCurrencyBreakdowns(Portfolio portfolio, boolean updateTotals) {
        List<AccountStatistics> stats = accountStatisticsRepository.findAll();
        if (CollectionUtils.isEmpty(stats)) {
            return false;
        }

        Map<Long, Account> accountsById = accountsById(stats.stream()
                .map(AccountStatistics::getAccountId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

        portfolio.getRealizedByCurrency().clear();
        portfolio.getUnrealizedByCurrency().clear();
        portfolio.getDividendsByCurrency().clear();

        double realizedInBase = 0.0;
        double unrealizedInBase = 0.0;
        double dividendsInBase = 0.0;
        for (AccountStatistics stat : stats) {
            CurrencyType currency = accountsById.get(stat.getAccountId()).getCurrency();
            double realized = nz(stat.getRealizedProfit());
            double unrealized = nz(stat.getUnrealizedProfit());
            double dividends = nz(stat.getDividends());
            portfolio.getRealizedByCurrency().merge(
                    currency,
                    realized,
                    Double::sum);
            portfolio.getUnrealizedByCurrency().merge(
                    currency,
                    unrealized,
                    Double::sum);
            portfolio.getDividendsByCurrency().merge(
                    currency,
                    dividends,
                    Double::sum);
            realizedInBase += realized;
            unrealizedInBase += unrealized;
            dividendsInBase += dividends;
        }
        if (updateTotals) {
            portfolio.setRealizedProfit(realizedInBase);
            portfolio.setUnrealizedProfit(unrealizedInBase);
            portfolio.setDividends(dividendsInBase);
            portfolio.setTotalProfit(realizedInBase + unrealizedInBase + dividendsInBase);
        }
        return true;
    }

    private boolean applyPortfolioCurrencyBreakdowns(Portfolio portfolio, boolean updateTotals) {
        List<PortfolioCurrencyBreakdown> rows = portfolioCurrencyBreakdownRepository.findAll();
        if (CollectionUtils.isEmpty(rows)) {
            return false;
        }

        portfolio.getRealizedByCurrency().clear();
        portfolio.getUnrealizedByCurrency().clear();
        portfolio.getDividendsByCurrency().clear();

        double realizedInBase = 0.0;
        double unrealizedInBase = 0.0;
        double dividendsInBase = 0.0;
        for (PortfolioCurrencyBreakdown row : rows) {
            CurrencyType currency = row.getCurrency();
            double localAmount = nz(row.getAmountLocal());
            double baseAmount = nz(row.getAmountInBaseCurrency());
            switch (row.getMetricType()) {
                case "REALIZED" -> {
                    portfolio.getRealizedByCurrency().merge(currency, localAmount, Double::sum);
                    realizedInBase += baseAmount;
                }
                case "UNREALIZED" -> {
                    portfolio.getUnrealizedByCurrency().merge(currency, localAmount, Double::sum);
                    unrealizedInBase += baseAmount;
                }
                case "DIVIDENDS" -> {
                    portfolio.getDividendsByCurrency().merge(currency, localAmount, Double::sum);
                    dividendsInBase += baseAmount;
                }
                default -> {
                    // Ignore unknown projection metric rows; the MV owns the supported set.
                }
            }
        }

        if (updateTotals) {
            portfolio.setRealizedProfit(realizedInBase);
            portfolio.setUnrealizedProfit(unrealizedInBase);
            portfolio.setDividends(dividendsInBase);
            portfolio.setTotalProfit(realizedInBase + unrealizedInBase + dividendsInBase);
        }
        return true;
    }

    private Map<Long, Account> accountsById(Collection<Long> accountIds) {
        return accountRepository.findMapByIdIn(accountIds);
    }

    private void applyOpenPositionUnrealizedCurrencyBreakdown(Portfolio portfolio, boolean updateTotals) {
        portfolio.getUnrealizedByCurrency().clear();
        double unrealizedProfit = 0.0;
        LocalDate rateDate = LocalDate.now();
        for (OpenedPosition position : openedPositionRepository.findAll()) {
            CurrencyType currency =
                    position.getCurrency() != null ? position.getCurrency() : portfolio.getBaseCurrency();
            double unrealized = nz(position.getProfit()) + nz(position.getCommission()) + nz(position.getSwap());
            portfolio.getUnrealizedByCurrency().merge(currency, unrealized, Double::sum);
            unrealizedProfit += currencyRateService.convertToBaseCurrency(
                    unrealized, portfolio.getBaseCurrency(), currency, rateDate);
        }
        if (updateTotals) {
            portfolio.setUnrealizedProfit(unrealizedProfit);
            portfolio.setTotalProfit(portfolio.getRealizedProfit() + unrealizedProfit + portfolio.getDividends());
        }
    }

    private Double localBalance(
            double balance, CurrencyType baseCurrency, CurrencyType localCurrency, LocalDate rateDate) {
        if (localCurrency == baseCurrency) {
            return null;
        }
        return currencyRateService.convertToBaseCurrency(balance, localCurrency, baseCurrency, rateDate);
    }

    // 2. Monthly Performance
    public Performance calculateMonthlyPerformance() {
        Performance performance = new Performance();
        int currentYear = java.time.Year.now().getValue();

        Map<String, Double> monthlyProfit = new TreeMap<>();
        Map<String, Long> monthlyOps = new TreeMap<>();
        Map<String, Double> monthlyCashflow = new TreeMap<>();

        for (AccountMonthlyPerformance row :
                accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc()) {
            String bucketKey = summaryBucketKey(row.getMonth(), currentYear);
            monthlyProfit.merge(bucketKey, nz(row.getProfit()), Double::sum);
            monthlyCashflow.merge(bucketKey, nz(row.getNetCashflow()), Double::sum);
            if (Math.abs(nz(row.getEndEquity())) >= 50.0
                    || Math.abs(nz(row.getProfit())) >= 0.005
                    || Math.abs(nz(row.getNetCashflow())) >= 0.005) {
                monthlyOps.merge(bucketKey, 1L, Long::sum);
            }
        }

        performance.setCalculateMonthlyPerformance(monthlyProfit);
        performance.setMonthlyOperationsCount(monthlyOps);
        performance.setMonthlyCashflow(monthlyCashflow);
        return performance;
    }

    /**
     * Buckets a summary row for the monthly performance chart.
     * Months before January 1st of the current year are aggregated by year only.
     */
    private String summaryBucketKey(LocalDate month, int currentYear) {
        if (month.getYear() < currentYear) {
            return String.format("%d", month.getYear());
        }
        return String.format("%d-%02d", month.getYear(), month.getMonthValue());
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
                        Collectors.summingDouble(position -> currencyRateService.convertToBaseCurrency(
                                position.getProfit(), baseCurrency, position.getCurrency(),
                                position.getCloseTime() != null ? position.getCloseTime().toLocalDate() : java.time.LocalDate.now()))
                ));

        Map<String, Double> openedProfits = openedPositions.stream()
                .collect(Collectors.groupingBy(
                        OpenedPosition::getSymbol,
                        Collectors.summingDouble(position -> currencyRateService.convertToBaseCurrency(
                                position.getProfit(), baseCurrency, position.getCurrency(), java.time.LocalDate.now()))
                ));

        // Merge symbols from both maps
        Set<String> allSymbols = new HashSet<>();
        allSymbols.addAll(closedProfits.keySet());
        allSymbols.addAll(openedProfits.keySet());

        List<InstrumentPerformance> instrumentPerformances = allSymbols.stream()
                .map(symbol -> {
                    double closedProfit = closedProfits.getOrDefault(symbol, 0.0);
                    double unrealizedProfit = openedProfits.getOrDefault(symbol, 0.0);
                    return new InstrumentPerformance(symbol, closedProfit, unrealizedProfit,
                            closedProfit + unrealizedProfit);
                })
                .sorted(Comparator.comparing(InstrumentPerformance::getTotal))
                .toList();

        double totalSum = instrumentPerformances.stream()
                .filter(Objects::nonNull)
                .mapToDouble(InstrumentPerformance::getTotal).sum();
        double threshold = totalSum * OTHER_BUCKET_RATIO;

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
                .filter(p -> p.getCloseTime() != null)
                .collect(Collectors.groupingBy(
                        // Bucket by ISO date ("yyyy-MM-dd") rather than the full timestamp,
                        // so multiple trades on the same day collapse into one chart point.
                        position -> position.getCloseTime().toLocalDate().toString(),
                        TreeMap::new,
                        Collectors.summingDouble(position -> currencyRateService.convertToBaseCurrency(
                                nz(position.getProfit()), baseCurrency, position.getCurrency(), position.getCloseTime().toLocalDate()))));
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


