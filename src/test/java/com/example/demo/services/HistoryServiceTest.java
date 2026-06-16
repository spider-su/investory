package com.example.demo.services;

import com.example.demo.data.CashOperationType;
import com.example.demo.data.CurrencyType;
import com.example.demo.data.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoryServiceTest {

    @Mock private CurrencyRateService currencyRateService;
    @Mock private OpenedPositionRepository openedPositionRepository;
    @Mock private ClosedPositionRepository closedPositionRepository;
    @Mock private CashOperationRepository cashOperationRepository;
    @Mock private StockRepository stockRepository;
    @Mock private OpenPositionHistoryRepository openPositionHistoryRepository;
    @Mock private PortfolioHistoryRepository portfolioHistoryRepository;

    private HistoryService historyService;

    @BeforeEach
    void setUp() {
        historyService = new HistoryService(currencyRateService, openedPositionRepository,
                closedPositionRepository, cashOperationRepository, stockRepository,
                openPositionHistoryRepository, portfolioHistoryRepository);
        when(currencyRateService.convertToBaseCurrency(anyDouble(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0, Double.class));
    }

    @Test
    void saveHistory_writesOpenPositionAndPortfolioSnapshotForFirstRunOfDay() {
        OpenedPosition position = new OpenedPosition();
        position.setSymbol("AAPL.US");
        position.setCurrency(CurrencyType.USD);
        position.setVolume(10.0);
        position.setOpenPrice(150.0);
        position.setMarketPrice(160.0);
        position.setProfit(100.0);
        position.setCommission(-1.0);

        Stock stock = new Stock();
        stock.setSymbol("AAPL.US");
        stock.setDayOpenPrice(158.0);
        stock.setMarketPrice(162.0);

        when(openedPositionRepository.findAll()).thenReturn(List.of(position));
        when(stockRepository.findAll()).thenReturn(List.of(stock));
        when(openPositionHistoryRepository.findAllAfterDate(any())).thenReturn(List.of());
        when(portfolioHistoryRepository.findOneAfterDate(any())).thenReturn(Optional.empty());
        when(closedPositionRepository.findAll()).thenReturn(List.of(
                closed(50.0, -1.0)
        ));
        when(cashOperationRepository.findAll()).thenReturn(List.of(
                cashDividend(20.0)
        ));

        Collection<OpenPositionHistory> result = historyService.saveHistory();

        assertEquals(1, result.size());
        OpenPositionHistory history = result.iterator().next();
        assertEquals("AAPL.US", history.getSymbol());
        assertEquals(10.0, history.getAmount());
        assertEquals(158.0, history.getOpenPrice());
        assertEquals(162.0, history.getClosePrice());
        // openProfit = (dayOpen - costBasis) * amount = (158 - 150) * 10 = 80
        assertEquals(80.0, history.getOpenProfit(), 0.01);
        // closeProfit = (162 - 150) * 10 = 120
        assertEquals(120.0, history.getCloseProfit(), 0.01);
        verify(openPositionHistoryRepository).saveAll(any());

        ArgumentCaptor<PortfolioHistory> snapshotCaptor = ArgumentCaptor.forClass(PortfolioHistory.class);
        verify(portfolioHistoryRepository).save(snapshotCaptor.capture());
        PortfolioHistory snapshot = snapshotCaptor.getValue();
        assertEquals(CurrencyType.USD, snapshot.getCurrency());
        // First write of the day -> openTotal populated.
        assertNotNull(snapshot.getOpenTotal());
        // Portfolio total = open net (profit + commission) + closed net + dividends
        //                 = (100 - 1) + (50 - 1) + 20 = 168
        assertEquals(168.0, snapshot.getOpenTotal(), 0.01);
        assertNull(snapshot.getCloseTotal());
    }

    @Test
    void saveHistory_setsCloseTotalOnSubsequentRunOfDay() {
        OpenedPosition position = new OpenedPosition();
        position.setSymbol("AAPL.US");
        position.setCurrency(CurrencyType.USD);
        position.setVolume(1.0);
        position.setOpenPrice(100.0);
        position.setMarketPrice(110.0);
        position.setProfit(10.0);
        position.setCommission(0.0);

        Stock stock = new Stock();
        stock.setSymbol("AAPL.US");
        stock.setDayOpenPrice(105.0);
        stock.setMarketPrice(110.0);

        PortfolioHistory existing = new PortfolioHistory();
        existing.setId(1L);
        existing.setOpenTotal(10.0); // already written earlier today

        when(openedPositionRepository.findAll()).thenReturn(List.of(position));
        when(stockRepository.findAll()).thenReturn(List.of(stock));
        when(openPositionHistoryRepository.findAllAfterDate(any())).thenReturn(List.of());
        when(portfolioHistoryRepository.findOneAfterDate(any())).thenReturn(Optional.of(existing));
        when(closedPositionRepository.findAll()).thenReturn(List.of());
        when(cashOperationRepository.findAll()).thenReturn(List.of());

        historyService.saveHistory();

        ArgumentCaptor<PortfolioHistory> snapshotCaptor = ArgumentCaptor.forClass(PortfolioHistory.class);
        verify(portfolioHistoryRepository).save(snapshotCaptor.capture());
        PortfolioHistory snapshot = snapshotCaptor.getValue();
        assertEquals(10.0, snapshot.getOpenTotal());     // untouched
        assertEquals(10.0, snapshot.getCloseTotal(), 0.01); // captured now
    }

    private static ClosedPosition closed(double profit, double commission) {
        ClosedPosition cp = new ClosedPosition();
        cp.setProfit(profit);
        cp.setCommission(commission);
        cp.setCurrency(CurrencyType.USD);
        cp.setCloseTime(ZonedDateTime.now());
        return cp;
    }

    private static CashOperation cashDividend(double amount) {
        CashOperation c = new CashOperation();
        c.setType(CashOperationType.DIVIDEND);
        c.setAmount(amount);
        c.setCurrency(CurrencyType.USD);
        c.setDate(ZonedDateTime.now());
        return c;
    }
}

