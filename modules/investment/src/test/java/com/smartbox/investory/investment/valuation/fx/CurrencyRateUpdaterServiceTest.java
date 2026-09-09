package com.smartbox.investory.investment.valuation.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.performance.InvestmentCalculationCache;
import com.smartbox.investory.investment.port.fx.FxRateHistoryProvider;
import com.smartbox.investory.investment.port.fx.FxRateProvider;
import com.smartbox.investory.investment.port.fx.FxRateProvider.FxQuote;
import com.smartbox.investory.investment.port.fx.FxRateProviderException;
import com.smartbox.investory.investment.projection.PortfolioProjectionRefreshService;
import com.smartbox.investory.investment.valuation.fx.CurrencyRateUpdaterService.CurrencyRateRefreshResult;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.time.ClockApplicationTime;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Currency Rate Updater Service")
class CurrencyRateUpdaterServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 9, 5);
  private static final ClockApplicationTime TIME =
      new ClockApplicationTime(
          Clock.fixed(Instant.parse("2026-09-05T08:00:00Z"), ZoneOffset.UTC),
          ZoneId.of("Europe/Warsaw"));

  @Mock private FxRateProvider fxRateProvider;
  @Mock private FxRateHistoryProvider fxRateHistoryProvider;
  @Mock private CurrencyRateService currencyRateService;
  @Mock private DailyFxRateService dailyFxRateService;
  @Mock private InvestmentCalculationCache calculationCache;
  @Mock private PortfolioProjectionRefreshService projectionRefreshService;

  private CurrencyRateUpdaterService updater;

  @BeforeEach
  void setUp() {
    updater =
        new CurrencyRateUpdaterService(
            fxRateProvider,
            fxRateHistoryProvider,
            currencyRateService,
            dailyFxRateService,
            calculationCache,
            projectionRefreshService,
            TIME);
  }

  @DisplayName("update Currency Rates pushes Rates For Usd Eur And Pln")
  @Test
  void updateCurrencyRates_pushesRatesForUsdEurAndPln() {
    when(fxRateProvider.fetchRates(any())).thenReturn(response(0.9, 4.0, TODAY));

    CurrencyRateRefreshResult result = updater.updateCurrencyRates();

    assertEquals(List.of("USD", "EUR", "PLN"), result.updated());
    assertTrue(result.failed().isEmpty());
    verify(currencyRateService).activateDailyHistoryAt(TODAY);
    verify(fxRateProvider).fetchRates(any());
  }

  @DisplayName("update Currency Rates For Date writes Only That Date")
  @Test
  void updateCurrencyRatesForDate_writesOnlyThatDate() {
    when(fxRateProvider.fetchRates(any()))
        .thenReturn(response(0.9, 4.0, LocalDate.of(2026, 8, 17)));

    CurrencyRateRefreshResult result =
        updater.updateCurrencyRatesForDate(LocalDate.of(2026, 8, 17));

    assertEquals(LocalDate.of(2026, 8, 17), result.rateDate());
    verify(currencyRateService).activateDailyHistoryAt(LocalDate.of(2026, 8, 17));
    verify(projectionRefreshService)
        .refreshApplicationViews(
            PortfolioProjectionRefreshService.ApplicationRefreshScope.FX_UPDATE);
  }

  @DisplayName("update Currency Rates records Failure When Response Is Null")
  @Test
  void updateCurrencyRates_recordsFailureWhenResponseIsNull() {
    when(fxRateProvider.fetchRates(any())).thenReturn(null);

    CurrencyRateRefreshResult result = updater.updateCurrencyRates();

    assertTrue(result.updated().isEmpty());
    assertEquals(1, result.failed().size());
  }

  @Test
  void updateCurrencyRates_rejectsInvalidQuotesBeforePersistence() {
    LocalDate date = LocalDate.of(2026, 8, 21);
    when(fxRateProvider.fetchRates(any()))
        .thenReturn(
            List.of(
                new FxQuote(CurrencyType.USD, CurrencyType.EUR, null, date, date),
                new FxQuote(
                    CurrencyType.USD, CurrencyType.PLN, BigDecimal.valueOf(4), date, date)));

    CurrencyRateRefreshResult result = updater.updateCurrencyRatesForDate(date);

    assertTrue(result.updated().isEmpty());
    verify(currencyRateService, never()).activateDailyHistoryAt(any());
  }

  @Test
  void updateCurrencyRates_rejectsConflictingDuplicateAndInconsistentDates() {
    LocalDate date = LocalDate.of(2026, 8, 21);
    when(fxRateProvider.fetchRates(any()))
        .thenReturn(
            List.of(
                new FxQuote(CurrencyType.USD, CurrencyType.EUR, BigDecimal.valueOf(.9), date, date),
                new FxQuote(
                    CurrencyType.USD, CurrencyType.EUR, BigDecimal.valueOf(.91), date, date),
                new FxQuote(
                    CurrencyType.USD, CurrencyType.PLN, BigDecimal.valueOf(4), date, date)));

    CurrencyRateRefreshResult result = updater.updateCurrencyRatesForDate(date);

    assertTrue(result.updated().isEmpty());
  }

  @DisplayName("update Currency Rates records Failure When Usd Request Is Rate Limited")
  @Test
  void updateCurrencyRates_recordsFailureWhenUsdRequestIsRateLimited() {
    when(fxRateProvider.fetchRates(any()))
        .thenThrow(
            new FxRateProviderException("exchangerate.host returned HTTP 429 for /live", null));

    CurrencyRateRefreshResult result = updater.updateCurrencyRates();

    assertTrue(result.updated().isEmpty());
    assertEquals(1, result.failed().size());
    assertTrue(result.failed().getFirst().contains("429"));
  }

  private static List<FxQuote> response(double eur, double pln, LocalDate providerDate) {
    return List.of(
        new FxQuote(
            CurrencyType.USD,
            CurrencyType.EUR,
            BigDecimal.valueOf(eur),
            providerDate,
            providerDate),
        new FxQuote(
            CurrencyType.USD,
            CurrencyType.PLN,
            BigDecimal.valueOf(pln),
            providerDate,
            providerDate));
  }
}
