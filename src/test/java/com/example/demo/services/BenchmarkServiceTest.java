package com.example.demo.services;

import com.example.demo.clients.market.TwelveDataService;
import com.example.demo.infrastructure.repository.account.Account;
import com.example.demo.infrastructure.repository.account.AccountDaily;
import com.example.demo.infrastructure.repository.account.AccountDailyRepository;
import com.example.demo.infrastructure.repository.account.AccountMonthlyPerformance;
import com.example.demo.infrastructure.repository.account.AccountMonthlyPerformanceRepository;
import com.example.demo.infrastructure.repository.account.AccountRepository;
import com.example.demo.infrastructure.repository.account.AccountStatistics;
import com.example.demo.infrastructure.repository.account.AccountStatisticsRepository;
import com.example.demo.infrastructure.repository.benchmark.BenchmarkMonthlyClose;
import com.example.demo.infrastructure.repository.benchmark.BenchmarkMonthlyCloseRepository;
import com.example.demo.services.models.Benchmark;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BenchmarkServiceTest {

    @Mock private AccountDailyRepository accountDailyRepository;
    @Mock private AccountMonthlyPerformanceRepository accountMonthlyPerformanceRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private AccountStatisticsRepository accountStatisticsRepository;
    @Mock private BenchmarkMonthlyCloseRepository benchmarkMonthlyCloseRepository;
    @Mock private TwelveDataService twelveDataService;

    private BenchmarkService benchmarkService;

    @BeforeEach
    void setUp() {
        benchmarkService = new BenchmarkService(
                accountDailyRepository,
                accountMonthlyPerformanceRepository,
                accountRepository,
                accountStatisticsRepository,
                benchmarkMonthlyCloseRepository,
                twelveDataService,
                "2026-01");
        org.mockito.Mockito.lenient()
                .when(accountRepository.findMapByIdIn(any()))
                .thenAnswer(invocation -> requestedAccounts(
                        invocation.getArgument(0),
                        Map.of(1L, account(1L, "Main"))));
        org.mockito.Mockito.lenient().when(accountStatisticsRepository.findAll()).thenReturn(List.of());
        org.mockito.Mockito.lenient()
                .when(accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc())
                .thenReturn(List.of());
        org.mockito.Mockito.lenient().when(benchmarkMonthlyCloseRepository.findBySymbolOrderByMonthDateAsc("SPY"))
                .thenReturn(List.of());
    }

    @Test
    void calculate_returnsUnavailableWhenNoDailyRows() {
        when(accountDailyRepository.findAll()).thenReturn(List.of());

        Benchmark benchmark = benchmarkService.calculate();

        assertFalse(benchmark.isAvailable());
    }

    @Test
    void calculate_isNotWrappedInOneServiceTransaction() {
        assertNull(BenchmarkService.class.getAnnotation(Transactional.class));
    }

    @Test
    void calculate_returnsUnavailableWhenProjectionTablesAreNotReady() {
        when(accountDailyRepository.findAll()).thenThrow(new RuntimeException("account_daily missing"));

        Benchmark benchmark = benchmarkService.calculate();

        assertFalse(benchmark.isAvailable());
    }

    @Test
    void calculate_buildsPortfolioAndBenchmarkCurves() {
        when(accountDailyRepository.findAll())
                .thenReturn(List.of(
                        monthly(1L, "2026-01-01", 10_000.0, 10_000.0),
                        monthly(1L, "2026-02-01", 0.0, 10_120.0)
                ));
        when(accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc())
                .thenReturn(List.of(
                        monthlyPerformance(1L, "2026-01-01", 10_000.0, 0.0, 0.0),
                        monthlyPerformance(1L, "2026-02-01", 10_000.0, 120.0, 0.0)
                ));

        TreeMap<String, Double> closes = new TreeMap<>();
        closes.put("2025-12", 500.0);
        closes.put("2026-01", 500.0);
        closes.put("2026-02", 525.0);
        when(twelveDataService.fetchMonthlyCloses(anyString(), anyInt())).thenReturn(closes);
        when(benchmarkMonthlyCloseRepository.findBySymbolOrderByMonthDateAsc("SPY"))
                .thenReturn(List.of(), List.of(
                        benchmarkClose("2025-12", 500.0),
                        benchmarkClose("2026-01", 500.0),
                        benchmarkClose("2026-02", 525.0)
                ));

        Benchmark benchmark = benchmarkService.calculate();

        assertTrue(benchmark.isAvailable());
        assertNotNull(benchmark.getLabels());
        assertFalse(benchmark.getLabels().isEmpty());
        assertEquals("2026-01", benchmark.getLabels().getFirst());
        assertEquals(0.0, benchmark.getPortfolioCurve().getFirst(), 0.01);
        assertEquals(0.0, benchmark.getBenchmarkCurve().getFirst(), 0.01);
        assertEquals(10000.0, benchmark.getInvestedCapital());
        assertEquals(120.0, benchmark.getPortfolioPl(), 0.01);
        assertEquals(500.0, benchmark.getBenchmarkPl(), 0.01);
        assertEquals(benchmark.getPortfolioReturnPct() - benchmark.getBenchmarkReturnPct(),
                benchmark.getAlpha(), 0.01);
        verify(benchmarkMonthlyCloseRepository).saveAll(anyList());
    }

    @Test
    void calculate_comparesMonthlyAccountValueChangesToSameSpyStartingValue() {
        when(accountDailyRepository.findAll())
                .thenReturn(List.of(
                        monthly(1L, "2026-01-01", 0.0, 1000.0),
                        monthly(1L, "2026-02-01", 0.0, 1100.0),
                        monthly(1L, "2026-03-01", 0.0, 990.0),
                        monthly(1L, "2026-04-01", 0.0, 1088.0)
                ));
        when(accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc())
                .thenReturn(List.of(
                        monthlyPerformance(1L, "2026-01-01", 1000.0, 0.0, 0.0),
                        monthlyPerformance(1L, "2026-02-01", 1000.0, 100.0, 0.0),
                        monthlyPerformance(1L, "2026-03-01", 1100.0, -110.0, 0.0),
                        monthlyPerformance(1L, "2026-04-01", 990.0, 98.0, 0.0)
                ));
        when(benchmarkMonthlyCloseRepository.findBySymbolOrderByMonthDateAsc("SPY"))
                .thenReturn(List.of(
                        benchmarkClose("2025-12", 100.0),
                        benchmarkClose("2026-01", 100.0),
                        benchmarkClose("2026-02", 105.0),
                        benchmarkClose("2026-03", 102.0),
                        benchmarkClose("2026-04", 110.0)
                ));

        Benchmark benchmark = benchmarkService.calculate();

        assertTrue(benchmark.isAvailable());
        assertEquals(List.of("2026-01", "2026-02", "2026-03", "2026-04"), benchmark.getLabels());
        assertEquals(List.of(0.0, 100.0, -10.0, 88.0), benchmark.getPortfolioCurve());
        assertEquals(List.of(0.0, 50.0, 20.0, 100.0), benchmark.getBenchmarkCurve());
        assertEquals(8.8, benchmark.getPortfolioReturnPct(), 0.01);
        assertEquals(10.0, benchmark.getBenchmarkReturnPct(), 0.01);
    }

    @Test
    void calculate_usesDailyReturnSoDepositsDoNotBecomePerformance() {
        when(accountDailyRepository.findAll())
                .thenReturn(List.of(
                        monthlyWithReturn(1L, "2026-01-01", 0.0, 1000.0, null),
                        monthlyWithReturn(1L, "2026-02-01", 1000.0, 2100.0, 0.10)
                ));
        when(accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc())
                .thenReturn(List.of(
                        monthlyPerformance(1L, "2026-01-01", 1000.0, 0.0, 0.0),
                        monthlyPerformance(1L, "2026-02-01", 1000.0, 100.0, 1000.0)
                ));
        when(benchmarkMonthlyCloseRepository.findBySymbolOrderByMonthDateAsc("SPY"))
                .thenReturn(List.of(
                        benchmarkClose("2025-12", 100.0),
                        benchmarkClose("2026-01", 100.0),
                        benchmarkClose("2026-02", 100.0)
                ));

        Benchmark benchmark = benchmarkService.calculate();

        assertTrue(benchmark.isAvailable());
        assertEquals(100.0, benchmark.getPortfolioPl(), 0.01);
        assertEquals(10.0, benchmark.getPortfolioReturnPct(), 0.01);
        assertEquals(0.0, benchmark.getBenchmarkPl(), 0.01);
    }

    @Test
    void calculate_returnsUnavailableWhenSpyDataIsEmpty() {
        when(accountDailyRepository.findAll()).thenReturn(List.of(monthly(1L, "2026-01-01", 1000.0, 1000.0)));
        Benchmark benchmark = benchmarkService.calculate();
        assertFalse(benchmark.isAvailable());
    }

    @Test
    void calculate_usesPersistedBenchmarkClosesWithoutFetching() {
        when(accountDailyRepository.findAll())
                .thenReturn(List.of(
                        monthlyWithStartingValue(1L, "2026-01-01", 1000.0, 0.0, 1000.0),
                        monthlyWithStartingValue(1L, "2026-02-01", 0.0, 0.0, 1100.0)
                ));
        when(accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc())
                .thenReturn(List.of(
                        monthlyPerformance(1L, "2026-01-01", 1000.0, 0.0, 0.0),
                        monthlyPerformance(1L, "2026-02-01", 1000.0, 100.0, 0.0)
                ));
        when(benchmarkMonthlyCloseRepository.findBySymbolOrderByMonthDateAsc("SPY"))
                .thenReturn(List.of(
                        benchmarkClose("2025-12", 480.0),
                        benchmarkClose("2026-01", 500.0),
                        benchmarkClose("2026-02", 525.0),
                        benchmarkClose("2026-03", 550.0),
                        benchmarkClose("2026-04", 560.0),
                        benchmarkClose("2026-05", 570.0),
                        benchmarkClose("2026-06", 580.0),
                        benchmarkClose("2026-07", 590.0)
                ));

        Benchmark benchmark = benchmarkService.calculate();

        assertTrue(benchmark.isAvailable());
        verify(twelveDataService, never()).fetchMonthlyCloses(anyString(), anyInt());
        verify(benchmarkMonthlyCloseRepository, never()).saveAll(anyList());
    }

    @Test
    void calculate_excludesAccountsWhoseLatestPortfolioValueIsZero() {
        when(accountRepository.findMapByIdIn(any())).thenAnswer(invocation -> requestedAccounts(
                invocation.getArgument(0),
                Map.of(
                        1L, account(1L, "Main"),
                        2L, account(2L, "Closed"),
                        3L, account(3L, "PLN Trading"))));
        when(accountDailyRepository.findAll())
                .thenReturn(List.of(
                        monthlyWithStartingValue(1L, "2026-01-01", 1000.0, 0.0, 1100.0),
                        monthlyWithStartingValue(2L, "2026-01-01", 500.0, 0.0, 0.0),
                        monthlyWithStartingValue(3L, "2026-01-01", 500.0, 0.0, 0.5)
                ));
        when(accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc())
                .thenReturn(List.of(
                        monthlyPerformance(1L, "2026-01-01", 1000.0, 100.0, 0.0),
                        monthlyPerformance(2L, "2026-01-01", 500.0, -500.0, 0.0),
                        monthlyPerformance(3L, "2026-01-01", 500.0, -499.5, 0.0)
                ));
        when(benchmarkMonthlyCloseRepository.findBySymbolOrderByMonthDateAsc("SPY"))
                .thenReturn(List.of(
                        benchmarkClose("2025-12", 500.0),
                        benchmarkClose("2026-01", 500.0)
                ));

        Benchmark benchmark = benchmarkService.calculate();

        assertTrue(benchmark.isAvailable());
        assertEquals(1, benchmark.getAccountOptions().size());
        assertEquals(1L, benchmark.getAccountOptions().getFirst().id());
        assertEquals(1, benchmark.getAccountSeries().size());
        assertEquals(1L, benchmark.getAccountSeries().getFirst().id());
    }

    @Test
    void calculate_excludesAccountsWhoseCurrentStatisticsBalanceIsZero() {
        when(accountRepository.findMapByIdIn(any())).thenAnswer(invocation -> requestedAccounts(
                invocation.getArgument(0),
                Map.of(
                        1L, account(1L, "Main"),
                        2L, account(2L, "Empty"))));
        when(accountStatisticsRepository.findAll())
                .thenReturn(List.of(
                        accountStatistics(1L, 100.0, 900.0),
                        accountStatistics(2L, 0.0, 0.0)
                ));
        when(accountDailyRepository.findAll())
                .thenReturn(List.of(
                        monthlyWithStartingValue(1L, "2026-01-01", 1000.0, 0.0, 1000.0),
                        monthlyWithStartingValue(2L, "2026-01-01", 500.0, 0.0, 500.0)
                ));
        when(accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc())
                .thenReturn(List.of(
                        monthlyPerformance(1L, "2026-01-01", 1000.0, 0.0, 0.0),
                        monthlyPerformance(2L, "2026-01-01", 500.0, 0.0, 0.0)
                ));
        when(benchmarkMonthlyCloseRepository.findBySymbolOrderByMonthDateAsc("SPY"))
                .thenReturn(List.of(benchmarkClose("2025-12", 500.0), benchmarkClose("2026-01", 500.0)));

        Benchmark benchmark = benchmarkService.calculate();

        assertTrue(benchmark.isAvailable());
        assertEquals(1, benchmark.getAccountOptions().size());
        assertEquals(1L, benchmark.getAccountOptions().getFirst().id());
        assertEquals(1, benchmark.getAccountSeries().size());
        assertEquals(1L, benchmark.getAccountSeries().getFirst().id());
    }

    @Test
    void calculate_keepsFundedZeroBalanceAccountsAvailableFromStatistics() {
        when(accountRepository.findMapByIdIn(any())).thenAnswer(invocation -> requestedAccounts(
                invocation.getArgument(0),
                Map.of(
                        1L, account(1L, "Main"),
                        2L, account(2L, "Closed"))));
        when(accountStatisticsRepository.findAll())
                .thenReturn(List.of(
                        accountStatistics(1L, 100.0, 900.0, 1000.0),
                        accountStatistics(2L, 0.0, 0.0, 6500.0)
                ));
        when(accountDailyRepository.findAll())
                .thenReturn(List.of(
                        monthlyWithStartingValue(1L, "2026-01-01", 1000.0, 0.0, 1000.0),
                        monthlyWithStartingValue(2L, "2026-01-01", 6500.0, 0.0, 500.0)
                ));
        when(accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc())
                .thenReturn(List.of(
                        monthlyPerformance(1L, "2026-01-01", 1000.0, 0.0, 0.0),
                        monthlyPerformance(2L, "2026-01-01", 6500.0, -6000.0, 0.0)
                ));
        when(benchmarkMonthlyCloseRepository.findBySymbolOrderByMonthDateAsc("SPY"))
                .thenReturn(List.of(benchmarkClose("2025-12", 500.0), benchmarkClose("2026-01", 500.0)));

        Benchmark benchmark = benchmarkService.calculate();

        assertTrue(benchmark.isAvailable());
        assertEquals(2, benchmark.getAccountOptions().size());
        assertTrue(benchmark.getAccountOptions().stream().anyMatch(option -> option.id().equals(2L)));
        assertEquals(2, benchmark.getAccountSeries().size());
    }

    @Test
    void calculate_filtersAccountDailyRowsBySelectedAccounts() {
        when(accountRepository.findMapByIdIn(any())).thenAnswer(invocation -> requestedAccounts(
                invocation.getArgument(0),
                Map.of(
                        1L, account(1L, "Main"),
                        2L, account(2L, "Side"))));
        when(accountDailyRepository.findAll())
                .thenReturn(List.of(
                        monthlyWithStartingValue(1L, "2026-01-01", 1000.0, 0.0, 1000.0),
                        monthlyWithStartingValue(1L, "2026-02-01", 0.0, 0.0, 1100.0),
                        monthlyWithStartingValue(2L, "2026-01-01", 5000.0, 0.0, 5000.0),
                        monthlyWithStartingValue(2L, "2026-02-01", 0.0, 0.0, 5000.0)
                ));
        when(accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc())
                .thenReturn(List.of(
                        monthlyPerformance(1L, "2026-01-01", 1000.0, 0.0, 0.0),
                        monthlyPerformance(1L, "2026-02-01", 1000.0, 100.0, 0.0),
                        monthlyPerformance(2L, "2026-01-01", 5000.0, 0.0, 0.0),
                        monthlyPerformance(2L, "2026-02-01", 5000.0, 0.0, 0.0)
                ));
        TreeMap<String, Double> closes = new TreeMap<>();
        closes.put("2025-12", 500.0);
        closes.put("2026-01", 500.0);
        closes.put("2026-02", 525.0);
        when(twelveDataService.fetchMonthlyCloses(anyString(), anyInt())).thenReturn(closes);
        when(benchmarkMonthlyCloseRepository.findBySymbolOrderByMonthDateAsc("SPY"))
                .thenReturn(List.of(), List.of(
                        benchmarkClose("2025-12", 500.0),
                        benchmarkClose("2026-01", 500.0),
                        benchmarkClose("2026-02", 525.0)));

        Benchmark benchmark = benchmarkService.calculate(List.of(1L));

        assertTrue(benchmark.isAvailable());
        assertEquals(1000.0, benchmark.getInvestedCapital());
        assertEquals(100.0, benchmark.getPortfolioPl(), 0.01);
        assertTrue(benchmark.getAccountOptions().stream().anyMatch(option -> option.id().equals(1L) && option.selected()));
        assertTrue(benchmark.getAccountOptions().stream().anyMatch(option -> option.id().equals(2L) && !option.selected()));
    }

    @Test
    void calculate_buildsAccountValueYearsFromDailyPortfolioValues() {
        when(accountRepository.findMapByIdIn(any())).thenAnswer(invocation -> requestedAccounts(
                invocation.getArgument(0),
                Map.of(
                        1L, account(1L, "Main"),
                        2L, account(2L, "Side"))));
        when(accountDailyRepository.findAll())
                .thenReturn(List.of(
                        monthly(1L, "2025-12-29", 0.0, 900.0),
                        monthly(1L, "2026-01-05", 0.0, 1000.0),
                        monthly(1L, "2026-01-06", 0.0, 1200.0),
                        monthly(2L, "2026-01-05", 0.0, 500.0),
                        monthly(2L, "2026-01-06", 0.0, 700.0)
                ));
        Benchmark benchmark = benchmarkService.calculate();

        assertTrue(benchmark.isAccountValuesAvailable());
        assertEquals(2026, benchmark.getSelectedAccountValueYear());
        assertEquals(List.of(2026, 2025), benchmark.getAccountValueYears().stream()
                .map(Benchmark.AccountValueYear::year)
                .toList());

        Benchmark.AccountValueYear year2026 = benchmark.getAccountValueYears().getFirst();
        assertEquals("2026-01-05", year2026.labels().getFirst());
        assertEquals("2026-01-06", year2026.labels().getLast());
        assertEquals(2, year2026.accountSeries().size());
        assertEquals(List.of(1000.0, 1200.0), year2026.accountSeries().getFirst().values());
        assertEquals(List.of(500.0, 700.0), year2026.accountSeries().get(1).values());
    }

    @Test
    void calculate_carriesPreviousDailyValueAcrossMissingAccountDays() {
        when(accountRepository.findMapByIdIn(any())).thenAnswer(invocation -> requestedAccounts(
                invocation.getArgument(0),
                Map.of(
                        1L, account(1L, "Main"),
                        2L, account(2L, "Side"))));
        when(accountDailyRepository.findAll())
                .thenReturn(List.of(
                        monthly(1L, "2026-01-05", 0.0, 1000.0),
                        monthly(1L, "2026-01-06", 0.0, 1050.0),
                        monthly(2L, "2026-01-06", 0.0, 500.0)
                ));
        Benchmark benchmark = benchmarkService.calculate();

        assertTrue(benchmark.isAccountValuesAvailable());
        Benchmark.AccountValueYear year2026 = benchmark.getAccountValueYears().getFirst();
        assertEquals(List.of("2026-01-05", "2026-01-06"), year2026.labels());
        assertEquals(List.of(1000.0, 1050.0), year2026.accountSeries().getFirst().values());
        assertEquals(List.of(0.0, 500.0), year2026.accountSeries().get(1).values());
    }

    @Test
    void calculate_marksOnlyRequestedAccountsSelectedForSharedDashboardFilters() {
        when(accountRepository.findMapByIdIn(any())).thenAnswer(invocation -> requestedAccounts(
                invocation.getArgument(0),
                Map.of(
                        1L, account(1L, "Main"),
                        2L, account(2L, "Side"))));
        when(accountDailyRepository.findAll())
                .thenReturn(List.of(
                        monthly(1L, "2026-01-01", 0.0, 1000.0),
                        monthly(2L, "2026-01-01", 0.0, 500.0)
                ));
        when(accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc())
                .thenReturn(List.of(
                        monthlyPerformance(1L, "2026-01-01", 1000.0, 0.0, 0.0),
                        monthlyPerformance(2L, "2026-01-01", 500.0, 0.0, 0.0)
                ));
        when(benchmarkMonthlyCloseRepository.findBySymbolOrderByMonthDateAsc("SPY"))
                .thenReturn(List.of(benchmarkClose("2025-12", 500.0), benchmarkClose("2026-01", 500.0)));

        Benchmark benchmark = benchmarkService.calculate(List.of(2L));

        assertTrue(benchmark.getAccountOptions().stream().anyMatch(option -> option.id().equals(1L) && !option.selected()));
        assertTrue(benchmark.getAccountOptions().stream().anyMatch(option -> option.id().equals(2L) && option.selected()));
        assertEquals(2, benchmark.getAccountValueYears().getFirst().accountSeries().size());
    }

    private static AccountDaily monthly(Long accountId, String month, double netCashFlow, double portfolioValue) {
        AccountDaily row = new AccountDaily();
        row.setAccountId(accountId);
        row.setDate(LocalDate.parse(month));
        row.setDeposits(Math.max(netCashFlow, 0.0));
        row.setWithdrawals(Math.min(netCashFlow, 0.0));
        row.setEquity(portfolioValue);
        return row;
    }

    private static AccountDaily monthlyWithReturn(
            Long accountId, String month, double netCashFlow, double portfolioValue, Double monthlyReturn) {
        AccountDaily row = monthly(accountId, month, netCashFlow, portfolioValue);
        row.setDailyReturn(monthlyReturn);
        return row;
    }

    private static AccountDaily monthlyWithStartingValue(
            Long accountId, String month, double startingValue, double netCashFlow, double portfolioValue) {
        AccountDaily row = monthly(accountId, month, netCashFlow, portfolioValue);
        return row;
    }

    private static AccountMonthlyPerformance monthlyPerformance(
            Long accountId, String month, double startEquity, double profit, double netCashflow) {
        LocalDate monthDate = LocalDate.parse(month);
        return new AccountMonthlyPerformance(
                accountId + ":" + month,
                accountId,
                monthDate,
                monthDate.withDayOfMonth(monthDate.lengthOfMonth()),
                startEquity,
                startEquity + profit + netCashflow,
                Math.max(netCashflow, 0.0),
                Math.min(netCashflow, 0.0),
                netCashflow,
                profit,
                startEquity == 0.0 ? 0.0 : profit / startEquity);
    }

    private static Account account(Long id, String name) {
        Account account = new Account();
        account.setId(id);
        account.setName(name);
        return account;
    }

    private static Map<Long, Account> requestedAccounts(Collection<Long> requestedIds, Map<Long, Account> accounts) {
        if (requestedIds == null || requestedIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Account> requested = new LinkedHashMap<>();
        for (Long id : requestedIds) {
            Account account = accounts.get(id);
            if (account != null) {
                requested.put(id, account);
            }
        }
        return requested;
    }

    private static AccountStatistics accountStatistics(Long accountId, double cashBalance, double marketValue) {
        return accountStatistics(accountId, cashBalance, marketValue, cashBalance + marketValue);
    }

    private static AccountStatistics accountStatistics(
            Long accountId, double cashBalance, double marketValue, double netDeposit) {
        AccountStatistics statistics = new AccountStatistics();
        statistics.setAccountId(accountId);
        statistics.setCashBalance(cashBalance);
        statistics.setMarketValue(marketValue);
        statistics.setNetDeposit(netDeposit);
        return statistics;
    }

    private static BenchmarkMonthlyClose benchmarkClose(String month, double close) {
        return BenchmarkMonthlyClose.builder()
                .symbol("SPY")
                .monthDate(LocalDate.parse(month + "-01"))
                .closePrice(close)
                .build();
    }
}
