package com.smartbox.investory.marketradar.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.smartbox.investory.marketradar.domain.DailyMarketBar;
import com.smartbox.investory.marketradar.domain.StrategistForecast;
import com.smartbox.investory.marketradar.port.HistoricalMarketDataPort;
import com.smartbox.investory.marketradar.port.StrategistForecastStore;
import com.smartbox.investory.marketradar.port.StrategistForecastStore.ForecastObservation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class StrategistOutlookServiceTest {
  private final StrategistForecastStore store = Mockito.mock(StrategistForecastStore.class);
  private final HistoricalMarketDataPort market = Mockito.mock(HistoricalMarketDataPort.class);
  private final StrategistOutlookService service = new StrategistOutlookService(store, market);
  private final LocalDate horizon = LocalDate.of(2026, 12, 31);
  private final LocalDate asOf = LocalDate.of(2026, 10, 1);

  @Test
  void calculatesPriceIndexYtdAnnualAndRemainingWithEvenMedianAndPointDispersion() {
    var forecasts =
        List.of(
            forecast(1, "a", asOf.minusDays(10), "5100"),
            forecast(2, "b", asOf.minusDays(8), "5300"));
    when(store.observations(horizon)).thenReturn(forecasts);
    when(market.dailyBars(Mockito.eq("^GSPC"), Mockito.any(), Mockito.eq(horizon.plusDays(10))))
        .thenReturn(
            List.of(bar(LocalDate.of(2025, 12, 31), 5000), bar(LocalDate.of(2026, 9, 30), 5500)));

    var result = service.outlook(horizon, asOf);

    assertThat(result.actualYtdPercent())
        .isCloseTo(10.0, org.assertj.core.data.Offset.offset(0.000001));
    assertThat(result.medianFullYearPercent())
        .isCloseTo(4.0, org.assertj.core.data.Offset.offset(0.000001));
    assertThat(result.medianRemainingPercent())
        .isCloseTo(-5.45454545454545, org.assertj.core.data.Offset.offset(0.000001));
    assertThat(result.dispersionPercentagePoints())
        .isCloseTo(4.0, org.assertj.core.data.Offset.offset(0.000001));
    assertThat(result.eligibleInstitutions()).isEqualTo(2);
    assertThat(result.vix().latest()).isNull();
  }

  @Test
  void excludesPostAsOfCapturesAndStaleLatestInstitutionAndSegregatesByTarget() {
    var visible = forecast(1, "a", asOf.minusDays(20), "5200");
    var capturedLater =
        new ForecastObservation(
            2,
            forecastValue("a", asOf.minusDays(1), "6000", asOf.plusDays(1)),
            "VERIFIED",
            "ELIGIBLE");
    var old = forecast(3, "b", asOf.minusDays(150), "5100");
    when(store.observations(horizon)).thenReturn(List.of(visible, capturedLater, old));
    when(market.dailyBars(Mockito.eq("^GSPC"), Mockito.any(), Mockito.any())).thenReturn(List.of());

    var result = service.outlook(horizon, asOf);

    assertThat(result.eligibleInstitutions()).isEqualTo(1);
    assertThat(result.staleExclusions()).isEqualTo(1);
    assertThat(result.medianFullYearPercent()).isNull();
  }

  @Test
  void reportsHistoricalOutcomeOnlyWhenNextTradingCloseExists() {
    LocalDate target = LocalDate.of(2026, 6, 7); // Sunday; policy uses next trading-day close.
    var maturedForecast =
        new StrategistForecast(
            "a",
            "a",
            null,
            LocalDate.of(2026, 1, 1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
            target,
            new BigDecimal("5500"),
            "https://example.com/matured",
            "Source",
            StrategistForecast.SourceType.PRIMARY,
            LocalDate.of(2026, 1, 2).atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
    var matured = new ForecastObservation(9, maturedForecast, "VERIFIED", "ELIGIBLE");
    when(store.observations(target)).thenReturn(List.of(matured));
    when(market.dailyBars(Mockito.eq("^GSPC"), Mockito.any(), Mockito.any()))
        .thenReturn(List.of(bar(LocalDate.of(2026, 6, 8), 5000)));
    var result = service.outlook(target, LocalDate.of(2026, 6, 10));
    assertThat(result.accuracy().scoredForecasts()).isEqualTo(1);
    assertThat(result.accuracy().meanAbsoluteErrorPoints()).isEqualTo(500.0);
  }

  private ForecastObservation forecast(long id, String key, LocalDate published, String target) {
    return new ForecastObservation(
        id, forecastValue(key, published, target, published.plusDays(1)), "VERIFIED", "ELIGIBLE");
  }

  private StrategistForecast forecastValue(
      String key, LocalDate published, String target, LocalDate observed) {
    return new StrategistForecast(
        key,
        key,
        null,
        published.atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
        horizon,
        new BigDecimal(target),
        "https://example.com/" + key + published,
        "Source",
        StrategistForecast.SourceType.PRIMARY,
        observed.atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
  }

  private DailyMarketBar bar(LocalDate date, double close) {
    return new DailyMarketBar(date, close, close, close, close, 1);
  }
}
