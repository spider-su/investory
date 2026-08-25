package com.smartbox.investory.investment.reporting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.accounting.model.Benchmark;
import com.smartbox.investory.investment.infrastructure.market.client.TwelveDataService;
import com.smartbox.investory.investment.infrastructure.persistence.NormalizedCashOperationRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountDailyEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountDailyRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountMonthlyPerformanceEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountMonthlyPerformanceRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountStatisticsEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountStatisticsRepository;
import com.smartbox.investory.investment.infrastructure.persistence.benchmark.BenchmarkMonthlyCloseEntity;
import com.smartbox.investory.investment.infrastructure.persistence.benchmark.BenchmarkMonthlyCloseRepository;
import com.smartbox.investory.investment.market.fx.CurrencyRateService;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class BenchmarkServiceTest {

  @Mock private AccountDailyRepository accountDailyRepository;
  @Mock private AccountMonthlyPerformanceRepository accountMonthlyPerformanceRepository;
  @Mock private AccountRepository accountRepository;
  @Mock private AccountStatisticsRepository accountStatisticsRepository;
  @Mock private NormalizedCashOperationRepository normalizedCashOperationRepository;
  @Mock private BenchmarkMonthlyCloseRepository benchmarkMonthlyCloseRepository;
  @Mock private TwelveDataService twelveDataService;
  @Mock private CurrencyRateService currencyRateService;

  private BenchmarkService benchmarkService;

  @BeforeEach
  void setUp() {
    benchmarkService =
        new BenchmarkService(
            accountDailyRepository,
            accountMonthlyPerformanceRepository,
            accountRepository,
            accountStatisticsRepository,
            new BenchmarkAccountValueService(currencyRateService),
            new BenchmarkMarketDataService(benchmarkMonthlyCloseRepository, twelveDataService),
            "2026-01");
    org.mockito.Mockito.lenient()
        .when(accountRepository.findMapByIdIn(any()))
        .thenAnswer(
            invocation ->
                requestedAccounts(invocation.getArgument(0), Map.of(1L, account(1L, "Main"))));
    org.mockito.Mockito.lenient().when(accountStatisticsRepository.findAll()).thenReturn(List.of());
    org.mockito.Mockito.lenient()
        .when(accountRepository.findPortfolioCurrenciesByAccountIdIn(any()))
        .thenReturn(List.of());
    org.mockito.Mockito.lenient()
        .when(accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc())
        .thenReturn(List.of());
    org.mockito.Mockito.lenient()
        .when(benchmarkMonthlyCloseRepository.findBySymbolOrderByMonthDateAsc("SPY"))
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
        .thenReturn(
            List.of(
                monthly(1L, "2026-01-01", 10_000.0, 10_000.0),
                monthly(1L, "2026-02-01", 0.0, 10_120.0)));
    when(accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc())
        .thenReturn(
            List.of(
                monthlyPerformance(1L, "2026-01-01", 10_000.0, 0.0, 0.0),
                monthlyPerformance(1L, "2026-02-01", 10_000.0, 120.0, 0.0)));

    TreeMap<String, Double> closes = new TreeMap<>();
    closes.put("2025-12", 500.0);
    closes.put("2026-01", 500.0);
    closes.put("2026-02", 525.0);
    when(twelveDataService.fetchMonthlyCloses(anyString(), anyInt())).thenReturn(closes);
    when(benchmarkMonthlyCloseRepository.findBySymbolOrderByMonthDateAsc("SPY"))
        .thenReturn(List.of())
        .thenReturn(
            List.of(
                benchmarkClose("2025-12", 500.0),
                benchmarkClose("2026-01", 500.0),
                benchmarkClose("2026-02", 525.0)));

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
    Benchmark.AccountSeries series = benchmark.getAccountSeries().getFirst();
    assertEquals(List.of(10_000.0, 10_000.0), series.returnCapitalCurve());
    assertEquals(List.of(0.0, 120.0), series.returnContributionCurve());
    assertEquals(
        benchmark.getPortfolioReturnPct() - benchmark.getBenchmarkReturnPct(),
        benchmark.getAlpha(),
        0.01);
    verify(benchmarkMonthlyCloseRepository).saveAll(anyList());
  }

  @Test
  void calculate_keepsPortfolioHistoryWhenSpyClosesAreMissing() {
    when(accountDailyRepository.findAll())
        .thenReturn(
            List.of(
                monthly(1L, "2026-01-01", 10_000.0, 10_000.0),
                monthly(1L, "2026-02-01", 10_120.0, 10_120.0)));
    when(accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc())
        .thenReturn(
            List.of(
                monthlyPerformance(1L, "2026-01-01", 10_000.0, 0.0, 0.0),
                monthlyPerformance(1L, "2026-02-01", 10_000.0, 120.0, 0.0)));

    Benchmark benchmark = benchmarkService.calculate();

    assertTrue(benchmark.isAvailable());
    assertEquals(List.of(0.0, 120.0), benchmark.getPortfolioCurve());
    assertEquals(java.util.Arrays.asList(null, null), benchmark.getBenchmarkReturnCurve());
  }

  @Test
  void calculate_usesFirstInRangeSpyCloseWhenThePredecessorIsUnavailable() {
    when(accountDailyRepository.findAll())
        .thenReturn(
            List.of(
                monthly(1L, "2026-01-01", 10_000.0, 10_000.0),
                monthly(1L, "2026-02-01", 0.0, 10_120.0)));
    when(accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc())
        .thenReturn(
            List.of(
                monthlyPerformance(1L, "2026-01-01", 10_000.0, 0.0, 0.0),
                monthlyPerformance(1L, "2026-02-01", 10_000.0, 120.0, 0.0)));
    when(benchmarkMonthlyCloseRepository.findBySymbolOrderByMonthDateAsc("SPY"))
        .thenReturn(List.of(benchmarkClose("2026-01", 500.0), benchmarkClose("2026-02", 525.0)));

    Benchmark benchmark = benchmarkService.calculate();

    assertTrue(benchmark.isBenchmarkAvailable());
    assertEquals(List.of(0.0, 5.0), benchmark.getBenchmarkReturnCurve());
    assertEquals(List.of(0.0, 500.0), benchmark.getBenchmarkCurve());
  }

  @Test
  void calculate_comparesMonthlyAccountValueChangesToSameSpyStartingValue() {
    when(accountDailyRepository.findAll())
        .thenReturn(
            List.of(
                monthly(1L, "2026-01-01", 0.0, 1000.0),
                monthly(1L, "2026-02-01", 0.0, 1100.0),
                monthly(1L, "2026-03-01", 0.0, 990.0),
                monthly(1L, "2026-04-01", 0.0, 1088.0)));
    when(accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc())
        .thenReturn(
            List.of(
                monthlyPerformance(1L, "2026-01-01", 1000.0, 0.0, 0.0),
                monthlyPerformance(1L, "2026-02-01", 1000.0, 100.0, 0.0),
                monthlyPerformance(1L, "2026-03-01", 1100.0, -110.0, 0.0),
                monthlyPerformance(1L, "2026-04-01", 990.0, 98.0, 0.0)));
    when(benchmarkMonthlyCloseRepository.findBySymbolOrderByMonthDateAsc("SPY"))
        .thenReturn(
            List.of(
                benchmarkClose("2025-12", 100.0),
                benchmarkClose("2026-01", 100.0),
                benchmarkClose("2026-02", 105.0),
                benchmarkClose("2026-03", 102.0),
                benchmarkClose("2026-04", 110.0)));

    Benchmark benchmark = benchmarkService.calculate();

    assertTrue(benchmark.isAvailable());
    assertEquals(List.of("2026-01", "2026-02", "2026-03", "2026-04"), benchmark.getLabels());
    assertEquals(List.of(0.0, 100.0, -10.0, 88.0), benchmark.getPortfolioCurve());
    assertEquals(List.of(0.0, 50.0, 20.0, 100.0), benchmark.getBenchmarkCurve());
    assertEquals(8.8, benchmark.getPortfolioReturnPct(), 0.01);
    assertEquals(10.0, benchmark.getBenchmarkReturnPct(), 0.01);
    assertEquals(8.8, benchmark.getPortfolioReturnCurve().getLast(), 0.01);
  }

  @Test
  void calculate_usesDailyReturnSoDepositsDoNotBecomePerformance() {
    when(accountDailyRepository.findAll())
        .thenReturn(
            List.of(
                monthlyWithReturn("2026-01-01", 0.0, 1000.0, null),
                monthlyWithReturn("2026-02-01", 1000.0, 2100.0, 0.10)));
    when(accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc())
        .thenReturn(
            List.of(
                monthlyPerformance(1L, "2026-01-01", 1000.0, 0.0, 0.0),
                monthlyPerformance(1L, "2026-02-01", 1000.0, 100.0, 1000.0)));
    when(benchmarkMonthlyCloseRepository.findBySymbolOrderByMonthDateAsc("SPY"))
        .thenReturn(
            List.of(
                benchmarkClose("2025-12", 100.0),
                benchmarkClose("2026-01", 100.0),
                benchmarkClose("2026-02", 100.0)));

    Benchmark benchmark = benchmarkService.calculate();

    assertTrue(benchmark.isAvailable());
    assertEquals(100.0, benchmark.getPortfolioPl(), 0.01);
    assertEquals(10.0, benchmark.getPortfolioReturnPct(), 0.01);
    assertEquals(0.0, benchmark.getBenchmarkPl(), 0.01);
    assertEquals(
        List.of(1000.0, 1000.0), benchmark.getAccountSeries().getFirst().returnCapitalCurve());
    assertEquals(
        List.of(0.0, 100.0), benchmark.getAccountSeries().getFirst().returnContributionCurve());
  }

  @Test
  void calculate_returnsUnavailableWhenSpyDataIsEmpty() {
    when(accountDailyRepository.findAll())
        .thenReturn(List.of(monthly(1L, "2026-01-01", 1000.0, 1000.0)));
    Benchmark benchmark = benchmarkService.calculate();
    assertFalse(benchmark.isAvailable());
  }

  @Test
  void calculate_usesPersistedBenchmarkClosesWithoutFetching() {
    when(accountDailyRepository.findAll())
        .thenReturn(
            List.of(
                monthlyWithStartingValue(1L, "2026-01-01", 1000.0),
                monthlyWithStartingValue(1L, "2026-02-01", 1100.0)));
    when(accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc())
        .thenReturn(
            List.of(
                monthlyPerformance(1L, "2026-01-01", 1000.0, 0.0, 0.0),
                monthlyPerformance(1L, "2026-02-01", 1000.0, 100.0, 0.0)));
    when(benchmarkMonthlyCloseRepository.findBySymbolOrderByMonthDateAsc("SPY"))
        .thenReturn(
            List.of(
                benchmarkClose("2025-12", 480.0),
                benchmarkClose("2026-01", 500.0),
                benchmarkClose("2026-02", 525.0),
                benchmarkClose("2026-03", 550.0),
                benchmarkClose("2026-04", 560.0),
                benchmarkClose("2026-05", 570.0),
                benchmarkClose("2026-06", 580.0),
                benchmarkClose("2026-07", 590.0)));

    Benchmark benchmark = benchmarkService.calculate();

    assertTrue(benchmark.isAvailable());
    verify(twelveDataService, never()).fetchMonthlyCloses(anyString(), anyInt());
    verify(benchmarkMonthlyCloseRepository, never()).saveAll(anyList());
  }

  @Test
  void calculate_excludesAccountsWhoseLatestPortfolioValueIsZero() {
    when(accountRepository.findMapByIdIn(any()))
        .thenAnswer(
            invocation ->
                requestedAccounts(
                    invocation.getArgument(0),
                    Map.of(
                        1L, account(1L, "Main"),
                        2L, account(2L, "Closed"),
                        3L, account(3L, "PLN Trading"))));
    when(accountDailyRepository.findAll())
        .thenReturn(
            List.of(
                monthlyWithStartingValue(1L, "2026-01-01", 1100.0),
                monthlyWithStartingValue(2L, "2026-01-01", 0.0),
                monthlyWithStartingValue(3L, "2026-01-01", 0.5)));
    when(accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc())
        .thenReturn(
            List.of(
                monthlyPerformance(1L, "2026-01-01", 1000.0, 100.0, 0.0),
                monthlyPerformance(2L, "2026-01-01", 500.0, -500.0, 0.0),
                monthlyPerformance(3L, "2026-01-01", 500.0, -499.5, 0.0)));
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
  void calculate_excludesAccountsWhoseCurrentStatisticsBalanceIsZero() {
    when(accountRepository.findMapByIdIn(any()))
        .thenAnswer(
            invocation ->
                requestedAccounts(
                    invocation.getArgument(0),
                    Map.of(
                        1L, account(1L, "Main"),
                        2L, account(2L, "Empty"))));
    when(accountStatisticsRepository.findAll())
        .thenReturn(List.of(accountStatistics(1L, 100.0, 900.0), accountStatistics(2L, 0.0, 0.0)));
    when(accountDailyRepository.findAll())
        .thenReturn(
            List.of(
                monthlyWithStartingValue(1L, "2026-01-01", 1000.0),
                monthlyWithStartingValue(2L, "2026-01-01", 500.0)));
    when(accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc())
        .thenReturn(
            List.of(
                monthlyPerformance(1L, "2026-01-01", 1000.0, 0.0, 0.0),
                monthlyPerformance(2L, "2026-01-01", 500.0, 0.0, 0.0)));
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
    when(accountRepository.findMapByIdIn(any()))
        .thenAnswer(
            invocation ->
                requestedAccounts(
                    invocation.getArgument(0),
                    Map.of(
                        1L, account(1L, "Main"),
                        2L, account(2L, "Closed"))));
    when(accountStatisticsRepository.findAll())
        .thenReturn(
            List.of(
                accountStatistics(1L, 100.0, 900.0, 1000.0),
                accountStatistics(2L, 0.0, 0.0, 6500.0)));
    when(accountDailyRepository.findAll())
        .thenReturn(
            List.of(
                monthlyWithStartingValue(1L, "2026-01-01", 1000.0),
                monthlyWithStartingValue(2L, "2026-01-01", 500.0)));
    when(accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc())
        .thenReturn(
            List.of(
                monthlyPerformance(1L, "2026-01-01", 1000.0, 0.0, 0.0),
                monthlyPerformance(2L, "2026-01-01", 6500.0, -6000.0, 0.0)));
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
    when(accountRepository.findMapByIdIn(any()))
        .thenAnswer(
            invocation ->
                requestedAccounts(
                    invocation.getArgument(0),
                    Map.of(
                        1L, account(1L, "Main"),
                        2L, account(2L, "Side"))));
    when(accountDailyRepository.findAll())
        .thenReturn(
            List.of(
                monthlyWithStartingValue(1L, "2026-01-01", 1000.0),
                monthlyWithStartingValue(1L, "2026-02-01", 1100.0),
                monthlyWithStartingValue(2L, "2026-01-01", 5000.0),
                monthlyWithStartingValue(2L, "2026-02-01", 5000.0)));
    when(accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc())
        .thenReturn(
            List.of(
                monthlyPerformance(1L, "2026-01-01", 1000.0, 0.0, 0.0),
                monthlyPerformance(1L, "2026-02-01", 1000.0, 100.0, 0.0),
                monthlyPerformance(2L, "2026-01-01", 5000.0, 0.0, 0.0),
                monthlyPerformance(2L, "2026-02-01", 5000.0, 0.0, 0.0)));
    TreeMap<String, Double> closes = new TreeMap<>();
    closes.put("2025-12", 500.0);
    closes.put("2026-01", 500.0);
    closes.put("2026-02", 525.0);
    when(twelveDataService.fetchMonthlyCloses(anyString(), anyInt())).thenReturn(closes);
    when(benchmarkMonthlyCloseRepository.findBySymbolOrderByMonthDateAsc("SPY"))
        .thenReturn(List.of())
        .thenReturn(
            List.of(
                benchmarkClose("2025-12", 500.0),
                benchmarkClose("2026-01", 500.0),
                benchmarkClose("2026-02", 525.0)));

    Benchmark benchmark = benchmarkService.calculate(List.of(1L));

    assertTrue(benchmark.isAvailable());
    assertEquals(1000.0, benchmark.getInvestedCapital());
    assertEquals(100.0, benchmark.getPortfolioPl(), 0.01);
    assertTrue(
        benchmark.getAccountOptions().stream()
            .anyMatch(option -> option.id().equals(1L) && option.selected()));
    assertTrue(
        benchmark.getAccountOptions().stream()
            .anyMatch(option -> option.id().equals(2L) && !option.selected()));
    assertEquals(10.0, benchmark.getPortfolioReturnPct(), 0.01);
  }

  @Test
  void calculate_chainsMultipleMonthlyReturnsInsteadOfAddingPercentages() {
    when(accountDailyRepository.findAll())
        .thenReturn(
            List.of(
                monthlyWithStartingValue(1L, "2026-01-01", 10_000.0),
                monthlyWithStartingValue(1L, "2026-02-01", 11_000.0),
                monthlyWithStartingValue(1L, "2026-03-01", 12_100.0)));
    when(accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc())
        .thenReturn(
            List.of(
                monthlyPerformance(1L, "2026-01-01", 10_000.0, 1_000.0, 0.0),
                monthlyPerformance(1L, "2026-02-01", 10_000.0, 1_000.0, 0.0),
                monthlyPerformance(1L, "2026-03-01", 11_000.0, 1_100.0, 0.0)));
    when(benchmarkMonthlyCloseRepository.findBySymbolOrderByMonthDateAsc("SPY"))
        .thenReturn(
            List.of(
                benchmarkClose("2025-12", 100.0),
                benchmarkClose("2026-01", 100.0),
                benchmarkClose("2026-02", 100.0),
                benchmarkClose("2026-03", 100.0)));

    Benchmark benchmark = benchmarkService.calculate();

    assertEquals(33.1, benchmark.getPortfolioReturnPct(), 0.01);
    assertNotEquals(30.0, benchmark.getPortfolioReturnPct(), 0.01);
  }

  @Test
  void calculate_excludesDepositsFromReturnButKeepsThemInProfitAmount() {
    when(accountDailyRepository.findAll())
        .thenReturn(
            List.of(
                monthlyWithStartingValue(1L, "2026-01-01", 0.0),
                monthlyWithStartingValue(1L, "2026-02-01", 11_000.0),
                monthlyWithStartingValue(1L, "2026-03-01", 22_000.0)));
    when(accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc())
        .thenReturn(
            List.of(
                monthlyPerformance(1L, "2026-01-01", 0.0, 0.0, 10_000.0),
                monthlyPerformance(1L, "2026-02-01", 10_000.0, 1_000.0, 0.0),
                monthlyPerformance(1L, "2026-03-01", 11_000.0, 1_000.0, 10_000.0)));
    when(benchmarkMonthlyCloseRepository.findBySymbolOrderByMonthDateAsc("SPY"))
        .thenReturn(
            List.of(
                benchmarkClose("2025-12", 100.0),
                benchmarkClose("2026-01", 100.0),
                benchmarkClose("2026-02", 100.0),
                benchmarkClose("2026-03", 100.0)));

    Benchmark benchmark = benchmarkService.calculate();

    assertEquals(2_000.0, benchmark.getPortfolioPl(), 0.01);
    assertEquals(20.0, benchmark.getPortfolioReturnPct(), 0.01);
    assertNotEquals(10.0, benchmark.getPortfolioReturnPct(), 0.01);
  }

  @Test
  void calculate_excludesWithdrawalsFromReturnButKeepsThemInProfitAmount() {
    when(accountDailyRepository.findAll())
        .thenReturn(
            List.of(
                monthlyWithStartingValue(1L, "2026-01-01", 10_000.0),
                monthlyWithStartingValue(1L, "2026-02-01", 7_000.0)));
    when(accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc())
        .thenReturn(
            List.of(
                monthlyPerformance(1L, "2026-01-01", 10_000.0, 1_000.0, 0.0),
                monthlyPerformance(1L, "2026-02-01", 11_000.0, 1_000.0, -5_000.0)));
    when(benchmarkMonthlyCloseRepository.findBySymbolOrderByMonthDateAsc("SPY"))
        .thenReturn(
            List.of(
                benchmarkClose("2025-12", 100.0),
                benchmarkClose("2026-01", 100.0),
                benchmarkClose("2026-02", 100.0)));

    Benchmark benchmark = benchmarkService.calculate();

    assertEquals(2_000.0, benchmark.getPortfolioPl(), 0.01);
    assertEquals(20.0, benchmark.getPortfolioReturnPct(), 0.01);
  }

  @Test
  void calculate_buildsAccountValueYearsFromDailyPortfolioValues() {
    when(accountRepository.findMapByIdIn(any()))
        .thenAnswer(
            invocation ->
                requestedAccounts(
                    invocation.getArgument(0),
                    Map.of(
                        1L, account(1L, "Main"),
                        2L, account(2L, "Side"))));
    when(accountDailyRepository.findAll())
        .thenReturn(
            List.of(
                daily(1L, "2025-12-29", 0.0, 900.0, null),
                daily(1L, "2026-01-05", 0.0, 1000.0, null),
                daily(1L, "2026-01-06", 200.0, 1200.0, null),
                daily(2L, "2026-01-05", 0.0, 500.0, null),
                daily(2L, "2026-01-06", 200.0, 700.0, null)));
    Benchmark benchmark = benchmarkService.calculate();

    assertTrue(benchmark.isAccountValuesAvailable());
    assertEquals(2026, benchmark.getSelectedAccountValueYear());
    assertEquals(
        List.of(2026, 2025),
        benchmark.getAccountValueYears().stream().map(Benchmark.AccountValueYear::year).toList());

    Benchmark.AccountValueYear year2026 = benchmark.getAccountValueYears().getFirst();
    assertEquals("2026-01-05", year2026.labels().getFirst());
    assertEquals("2026-01-06", year2026.labels().getLast());
    assertEquals(2, year2026.accountSeries().size());
    assertEquals(List.of(0.0, 200.0), year2026.accountSeries().getFirst().profitValues());
    assertEquals(List.of(0.0, 200.0), year2026.accountSeries().get(1).profitValues());
    assertEquals(List.of(0.0, 400.0), year2026.totalProfitValues());
  }

  @Test
  void calculate_carriesPreviousDailyValueAcrossMissingAccountDays() {
    when(accountRepository.findMapByIdIn(any()))
        .thenAnswer(
            invocation ->
                requestedAccounts(
                    invocation.getArgument(0),
                    Map.of(
                        1L, account(1L, "Main"),
                        2L, account(2L, "Side"))));
    when(accountDailyRepository.findAll())
        .thenReturn(
            List.of(
                daily(1L, "2026-01-05", 0.0, 1000.0, null),
                daily(1L, "2026-01-06", 50.0, 1050.0, null),
                daily(2L, "2026-01-06", 0.0, 500.0, null)));
    Benchmark benchmark = benchmarkService.calculate();

    assertTrue(benchmark.isAccountValuesAvailable());
    Benchmark.AccountValueYear year2026 = benchmark.getAccountValueYears().getFirst();
    assertEquals(List.of("2026-01-05", "2026-01-06"), year2026.labels());
    assertEquals(List.of(0.0, 50.0), year2026.accountSeries().getFirst().profitValues());
    assertEquals(List.of(0.0, 0.0), year2026.accountSeries().get(1).profitValues());
    assertEquals(List.of(0.0, 50.0), year2026.totalProfitValues());
  }

  @Test
  void calculate_portfolioDailyProfitExcludesCashOnlyAccounts() {
    AccountEntity cashOnly = account(2L, "Cash reserve");
    cashOnly.setCashOnly(true);
    when(accountRepository.findMapByIdIn(any()))
        .thenAnswer(
            invocation ->
                requestedAccounts(
                    invocation.getArgument(0), Map.of(1L, account(1L, "Main"), 2L, cashOnly)));
    when(accountRepository.findAll()).thenReturn(List.of(account(1L, "Main"), cashOnly));
    when(accountDailyRepository.findAll())
        .thenReturn(
            List.of(
                daily(1L, "2026-01-01", 10.0, 1_010.0, null),
                daily(1L, "2026-01-02", 5.0, 1_015.0, null),
                daily(2L, "2026-01-01", 3.0, 503.0, null),
                daily(2L, "2026-01-02", -1.0, 502.0, null)));

    Benchmark benchmark = benchmarkService.calculate();

    Benchmark.AccountValueYear year = benchmark.getAccountValueYears().getFirst();
    assertEquals(List.of(10.0, 15.0), year.totalProfitValues());
    assertEquals(1, year.accountSeries().size());
    assertEquals(List.of(10.0, 15.0), year.accountSeries().getFirst().profitValues());
  }

  @Test
  void calculate_portfolioDailyProfitConvertsNonUsdRowsAtSnapshotDate() {
    when(currencyRateService.convertToBaseCurrency(
            10.0, CurrencyType.USD, CurrencyType.PLN, LocalDate.parse("2026-01-02")))
        .thenReturn(20.0);
    AccountDailyEntity row = daily(1L, "2026-01-02", 10.0, 1_010.0, null);
    row.setValuationCurrency("PLN");
    when(accountDailyRepository.findAll()).thenReturn(List.of(row));

    Benchmark benchmark = benchmarkService.calculate();

    assertEquals(List.of(20.0), benchmark.getAccountValueYears().getFirst().totalProfitValues());
    verify(currencyRateService)
        .convertToBaseCurrency(
            10.0, CurrencyType.USD, CurrencyType.PLN, LocalDate.parse("2026-01-02"));
  }

  @Test
  void calculate_normalizesMixedCurrencyAccountValuesToDashboardBaseCurrency() {
    when(accountRepository.findMapByIdIn(any()))
        .thenAnswer(
            invocation ->
                requestedAccounts(
                    invocation.getArgument(0),
                    Map.of(1L, account(1L, "PLN account"), 2L, account(2L, "EUR account"))));
    AccountDailyEntity pln = daily(1L, "2026-01-02", 10.0, 1_010.0, 0.01);
    pln.setValuationCurrency("PLN");
    AccountDailyEntity eur = daily(2L, "2026-01-02", 10.0, 1_010.0, 0.01);
    eur.setValuationCurrency("EUR");
    when(accountDailyRepository.findAll()).thenReturn(List.of(pln, eur));
    when(currencyRateService.convertToBaseCurrency(
            10.0, CurrencyType.USD, CurrencyType.PLN, LocalDate.parse("2026-01-02")))
        .thenReturn(2.5);
    when(currencyRateService.convertToBaseCurrency(
            10.0, CurrencyType.USD, CurrencyType.EUR, LocalDate.parse("2026-01-02")))
        .thenReturn(12.0);

    Benchmark.AccountValueYear year =
        benchmarkService.calculate().getAccountValueYears().getFirst();

    assertEquals(
        List.of(2.5),
        year.accountSeries().stream()
            .filter(series -> series.id().equals(1L))
            .findFirst()
            .orElseThrow()
            .profitValues());
    assertEquals(
        List.of(12.0),
        year.accountSeries().stream()
            .filter(series -> series.id().equals(2L))
            .findFirst()
            .orElseThrow()
            .profitValues());
    assertEquals(List.of(14.5), year.totalProfitValues());
  }

  @Test
  void calculate_marksOnlyRequestedAccountsSelectedForSharedDashboardFilters() {
    when(accountRepository.findMapByIdIn(any()))
        .thenAnswer(
            invocation ->
                requestedAccounts(
                    invocation.getArgument(0),
                    Map.of(
                        1L, account(1L, "Main"),
                        2L, account(2L, "Side"))));
    when(accountDailyRepository.findAll())
        .thenReturn(
            List.of(monthly(1L, "2026-01-01", 0.0, 1000.0), monthly(2L, "2026-01-01", 0.0, 500.0)));
    when(accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc())
        .thenReturn(
            List.of(
                monthlyPerformance(1L, "2026-01-01", 1000.0, 0.0, 0.0),
                monthlyPerformance(2L, "2026-01-01", 500.0, 0.0, 0.0)));
    when(benchmarkMonthlyCloseRepository.findBySymbolOrderByMonthDateAsc("SPY"))
        .thenReturn(List.of(benchmarkClose("2025-12", 500.0), benchmarkClose("2026-01", 500.0)));

    Benchmark benchmark = benchmarkService.calculate(List.of(2L));

    assertTrue(
        benchmark.getAccountOptions().stream()
            .anyMatch(option -> option.id().equals(1L) && !option.selected()));
    assertTrue(
        benchmark.getAccountOptions().stream()
            .anyMatch(option -> option.id().equals(2L) && option.selected()));
    assertEquals(1, benchmark.getAccountValueYears().getFirst().accountSeries().size());
    assertEquals(2L, benchmark.getAccountValueYears().getFirst().accountSeries().getFirst().id());
  }

  @Test
  void calculate_selectedAccountReturnCanStartAfterAnotherAccountsEarlierHistory() {
    when(accountRepository.findMapByIdIn(any()))
        .thenAnswer(
            invocation ->
                requestedAccounts(
                    invocation.getArgument(0),
                    Map.of(1L, account(1L, "Early"), 2L, account(2L, "Selected"))));
    when(accountDailyRepository.findAll())
        .thenReturn(
            List.of(
                monthly(1L, "2026-01-01", 0.0, 1_000.0), monthly(2L, "2026-02-01", 0.0, 1_100.0)));
    when(accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc())
        .thenReturn(
            List.of(
                monthlyPerformance(1L, "2026-01-01", 1_000.0, 0.0, 0.0),
                monthlyPerformance(2L, "2026-02-01", 1_000.0, 100.0, 0.0)));
    when(benchmarkMonthlyCloseRepository.findBySymbolOrderByMonthDateAsc("SPY"))
        .thenReturn(
            List.of(
                benchmarkClose("2025-12", 100.0),
                benchmarkClose("2026-01", 100.0),
                benchmarkClose("2026-02", 100.0)));

    Benchmark benchmark = benchmarkService.calculate(List.of(2L));

    assertEquals(java.util.Arrays.asList(null, 10.0), benchmark.getPortfolioReturnCurve());
    assertEquals(10.0, benchmark.getPortfolioReturnPct());
  }

  @Test
  void calculate_ignoresBookkeepingAndOtherCashMovementsInCanonicalAccountProfit() {
    when(accountRepository.findMapByIdIn(any()))
        .thenAnswer(
            invocation ->
                requestedAccounts(invocation.getArgument(0), Map.of(1L, account(1L, "Main"))));
    when(accountDailyRepository.findAll())
        .thenReturn(
            List.of(
                daily(1L, "2026-01-01", 0.0, 1_000.0, 0.0),
                daily(1L, "2026-01-02", 0.0, 101_000.0, 0.0),
                daily(1L, "2026-01-03", 0.0, 1_000.0, 0.0),
                daily(1L, "2026-01-04", 25.0, 1_025.0, 0.1)));

    Benchmark.AccountValueSeries series =
        benchmarkService.calculate().getAccountValueYears().getFirst().accountSeries().getFirst();

    assertEquals(List.of(0.0, 0.0, 0.0, 25.0), series.profitValues());
    assertEquals(List.of(0.0, 0.0, 0.0, 10.0), series.profitPctValues());
  }

  private static AccountDailyEntity monthly(
      Long accountId, String month, double netCashFlow, double portfolioValue) {
    AccountDailyEntity row = new AccountDailyEntity();
    row.setAccountId(accountId);
    row.setDate(LocalDate.parse(month));
    row.setDeposits(Math.max(netCashFlow, 0.0));
    row.setWithdrawals(Math.min(netCashFlow, 0.0));
    row.setEquity(portfolioValue);
    return row;
  }

  private static AccountDailyEntity daily(
      Long accountId, String date, double dailyProfit, double equity, Double dailyReturn) {
    AccountDailyEntity row = monthly(accountId, date, 0.0, equity);
    row.setDailyProfitAmount(dailyProfit);
    row.setDailyReturn(dailyReturn);
    return row;
  }

  private static AccountDailyEntity monthlyWithReturn(
      String month, double netCashFlow, double portfolioValue, Double monthlyReturn) {
    AccountDailyEntity row = monthly(1L, month, netCashFlow, portfolioValue);
    row.setDailyReturn(monthlyReturn);
    return row;
  }

  private static AccountDailyEntity monthlyWithStartingValue(
      Long accountId, String month, double portfolioValue) {
    return monthly(accountId, month, 0.0, portfolioValue);
  }

  private static AccountMonthlyPerformanceEntity monthlyPerformance(
      Long accountId, String month, double startEquity, double profit, double netCashflow) {
    LocalDate monthDate = LocalDate.parse(month);
    return new AccountMonthlyPerformanceEntity(
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
        startEquity == 0.0 ? 0.0 : profit / startEquity,
        ZonedDateTime.now());
  }

  private static AccountEntity account(Long id, String name) {
    AccountEntity account = new AccountEntity();
    account.setId(id);
    account.setName(name);
    return account;
  }

  private static Map<Long, AccountEntity> requestedAccounts(
      Collection<Long> requestedIds, Map<Long, AccountEntity> accounts) {
    if (requestedIds == null || requestedIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, AccountEntity> requested = new LinkedHashMap<>();
    for (Long id : requestedIds) {
      AccountEntity account = accounts.get(id);
      if (account != null) {
        requested.put(id, account);
      }
    }
    return requested;
  }

  private static AccountStatisticsEntity accountStatistics(
      Long accountId, double cashBalance, double marketValue) {
    return accountStatistics(accountId, cashBalance, marketValue, cashBalance + marketValue);
  }

  private static AccountStatisticsEntity accountStatistics(
      Long accountId, double cashBalance, double marketValue, double netDeposit) {
    AccountStatisticsEntity statistics = new AccountStatisticsEntity();
    statistics.setAccountId(accountId);
    statistics.setCashBalance(cashBalance);
    statistics.setMarketValue(marketValue);
    statistics.setNetDeposit(netDeposit);
    return statistics;
  }

  private static BenchmarkMonthlyCloseEntity benchmarkClose(String month, double close) {
    return BenchmarkMonthlyCloseEntity.builder()
        .symbol("SPY")
        .monthDate(LocalDate.parse(month + "-01"))
        .closePrice(java.math.BigDecimal.valueOf(close))
        .build();
  }
}
