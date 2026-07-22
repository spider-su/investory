package com.example.demo.controllers.rest.ghostfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.repository.AssetRepository;
import com.example.demo.infrastructure.repository.CashOperationRepository;
import com.example.demo.infrastructure.repository.ClosedPositionRepository;
import com.example.demo.infrastructure.repository.OpenedPositionRepository;
import com.example.demo.infrastructure.repository.account.AccountDailyRepository;
import com.example.demo.services.PortfolioService;
import com.example.demo.services.currency.CurrencyRateService;
import com.example.demo.services.models.OpenPositionValue;
import com.example.demo.services.models.Portfolio;
import com.example.demo.testsupport.portfolio.PortfolioBuilders;
import com.example.demo.testsupport.portfolio.PortfolioTestData;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GhostfolioCompatibilityServiceTest {

    @Mock private PortfolioService portfolioService;
    @Mock private AccountDailyRepository accountDailyRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private CashOperationRepository cashOperationRepository;
    @Mock private OpenedPositionRepository openedPositionRepository;
    @Mock private ClosedPositionRepository closedPositionRepository;
    @Mock private CurrencyRateService currencyRateService;

    private GhostfolioCompatibilityService service;

    @BeforeEach
    void setUp() {
        service =
                new GhostfolioCompatibilityService(
                        portfolioService,
                        accountDailyRepository,
                        assetRepository,
                        cashOperationRepository,
                        openedPositionRepository,
                        closedPositionRepository,
                        currencyRateService);
        lenient().when(cashOperationRepository.findAll()).thenReturn(List.of());
        lenient().when(cashOperationRepository.findAllByOrderByDateDescIdDesc()).thenReturn(List.of());
        lenient().when(openedPositionRepository.findAll()).thenReturn(List.of());
        lenient().when(closedPositionRepository.findAll()).thenReturn(List.of());
        lenient().when(assetRepository.findBySymbol(any())).thenReturn(Optional.empty());
        lenient().when(assetRepository.findAllBySymbolIn(any())).thenReturn(List.of());
        lenient()
                .when(currencyRateService.convertToBaseCurrency(any(Double.class), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void accountBalancesUseOnlyPersistedDailyHistory() {
        when(accountDailyRepository.findAllByAccountIdOrderByDateAsc(1L))
                .thenReturn(
                        List.of(
                                PortfolioBuilders.accountDaily()
                                        .id(11L)
                                        .account(1L)
                                        .on(PortfolioTestData.MID_YEAR)
                                        .equity(1200.0)
                                        .build()));

        Map<String, Object> response = service.accountBalances("1");

        List<?> balances = (List<?>) response.get("balances");
        assertEquals(1, balances.size());
        Map<?, ?> row = (Map<?, ?>) balances.getFirst();
        assertEquals("11", row.get("id"));
        assertEquals("2025-06-30", row.get("date"));
        assertEquals(1200.0, row.get("valueInBaseCurrency"));
        verify(portfolioService, never()).calculateTotalProfitLoss();
    }

    @Test
    void accountBalancesReturnEmptyWhenNoHistoryExists() {
        when(accountDailyRepository.findAllByAccountIdOrderByDateAsc(1L)).thenReturn(List.of());

        Map<String, Object> response = service.accountBalances("1");

        assertTrue(((List<?>) response.get("balances")).isEmpty());
        verify(portfolioService, never()).calculateTotalProfitLoss();
    }

    @Test
    void holdingsReusePortfolioCalculatedPositionValuesAndKeepAccountIds() {
        Portfolio portfolio = new Portfolio();
        portfolio.setBaseCurrency(CurrencyType.USD);
        portfolio.setOpenPositionValues(
                        List.of(
                                new OpenPositionValue(
                                        "AAPL.US", 10.0, 1_500.0, 150.0, 2_000.0, 500.0, 33.3333, CurrencyType.USD, 100.0)));
        when(portfolioService.calculateTotalProfitLoss()).thenReturn(portfolio);
        when(openedPositionRepository.findAll())
                .thenReturn(
                        List.of(
                                PortfolioBuilders.openPosition(PortfolioTestData.AAPL)
                                        .withId(1L)
                                        .forAccount(1L)
                                        .quantity(5.0)
                                        .build(),
                                PortfolioBuilders.openPosition(PortfolioTestData.AAPL)
                                        .withId(2L)
                                        .forAccount(2L)
                                        .quantity(5.0)
                                        .build()));
        when(assetRepository.findAllBySymbolIn(List.of("AAPL.US")))
                .thenReturn(
                        List.of(
                                PortfolioBuilders.asset(PortfolioTestData.AAPL)
                                        .withName("Apple")
                                        .build()));

        Map<String, Object> response = service.holdings(null, null);

        List<?> holdings = (List<?>) response.get("holdings");
        assertEquals(1, holdings.size());
        Map<?, ?> holding = (Map<?, ?>) holdings.getFirst();
        assertEquals("AAPL.US", holding.get("symbol"));
        assertEquals(10.0, holding.get("quantity"));
        assertEquals(1_500.0, holding.get("investment"));
        assertEquals(2_000.0, holding.get("valueInBaseCurrency"));
        assertEquals(500.0, holding.get("netPerformance"));
        assertNull(holding.get("accountId"));
        assertEquals(List.of("1", "2"), holding.get("accountIds"));
    }

    @Test
    void performanceChartUsesAccountDailyAndRangeWithoutCurrentFallback() {
        Portfolio portfolio = new Portfolio();
        portfolio.setBaseCurrency(CurrencyType.USD);
        portfolio.setBalance(1_500.0);
        portfolio.setNetDeposits(1_000.0);
        portfolio.setTotalProfit(500.0);
        when(portfolioService.calculateTotalProfitLoss()).thenReturn(portfolio);
        when(accountDailyRepository.findAllByOrderByDateAscAccountIdAsc())
                .thenReturn(
                        List.of(
                                PortfolioBuilders.accountDaily()
                                        .id(1L)
                                        .account(1L)
                                        .on(PortfolioTestData.JANUARY_MONTH_END)
                                        .valuation(100.0, 900.0, 900.0)
                                        .build(),
                                PortfolioBuilders.accountDaily()
                                        .id(2L)
                                        .account(1L)
                                        .on(PortfolioTestData.MID_YEAR)
                                        .valuation(500.0, 1_000.0, 1_000.0)
                                        .build()));

        Map<String, Object> response = service.performance("1", "max");

        List<?> chart = (List<?>) response.get("chart");
        assertEquals(2, chart.size());
        Map<?, ?> point = (Map<?, ?>) chart.get(1);
        assertEquals(1_500.0, point.get("value"));
        assertFalse(point.containsKey("today"));
        assertNull(response.get("dateOfFirstActivity"));
    }

    @Test
    void activitiesMapLedgerTypesAndApplySortingPaginationAndBaseConversion() {
        Portfolio portfolio = new Portfolio();
        portfolio.setBaseCurrency(CurrencyType.PLN);
        when(portfolioService.calculateTotalProfitLoss()).thenReturn(portfolio);
        when(cashOperationRepository.findAllByOrderByDateDescIdDesc())
                .thenReturn(
                        List.of(
                                PortfolioBuilders.cashOperation()
                                        .withId(1L)
                                        .forAccount(1L)
                                        .dividend(PortfolioTestData.AAPL, 20.0)
                                        .on(PortfolioTestData.MID_YEAR)
                                        .build(),
                                PortfolioBuilders.cashOperation()
                                        .withId(2L)
                                        .forAccount(1L)
                                        .deposit(100.0, CurrencyType.PLN)
                                        .on(PortfolioTestData.YEAR_END)
                                        .build()));
        when(currencyRateService.convertToBaseCurrency(20.0, CurrencyType.PLN, CurrencyType.USD, PortfolioTestData.MID_YEAR))
                .thenReturn(80.0);

        Map<String, Object> response = service.activities("1", "DIVIDEND,DEPOSIT", "max", null, "value", "desc", 1, 0);

        assertEquals(2, response.get("count"));
        List<?> rows = (List<?>) response.get("activities");
        assertEquals(1, rows.size());
        Map<?, ?> first = (Map<?, ?>) rows.getFirst();
        assertEquals("DEPOSIT", first.get("type"));
        assertEquals(100.0, first.get("valueInBaseCurrency"));
    }
}
