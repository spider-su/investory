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

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.CsvSource({
    "2026-07-01T19:59:00Z,2026-06-30", // before 16:00 New York
    "2026-07-01T20:01:00Z,2026-07-01", // after close
    "2026-07-04T22:00:00Z,2026-07-02", // Saturday, Friday is holiday
    "2026-07-01T00:00:00Z,2026-06-30" // date-only, conservative
  })
  void publicationReferenceNeverUsesAnUncompletedSession(String timestamp, String expected) {
    var bars =
        List.of(
            bar(LocalDate.of(2026, 6, 30), 5000),
            bar(LocalDate.of(2026, 7, 1), 5500),
            bar(LocalDate.of(2026, 7, 2), 5600));
    var reference =
        StrategistOutlookService.publicationClose(bars, java.time.Instant.parse(timestamp));
    assertThat(reference.date()).isEqualTo(LocalDate.parse(expected));
    assertThat(
            StrategistOutlookService.publicationClose(
                List.of(), java.time.Instant.parse(timestamp)))
        .isNull();
  }

  @Test
  void computesOddMedianIqrAndHistoricalSnapshotsWithoutCaptureLookAhead() {
    var first = forecast(1, "a", asOf.minusDays(40), "5000");
    var second = forecast(2, "b", asOf.minusDays(40), "5400");
    var revision = forecast(3, "a", asOf.minusDays(10), "5200");
    when(store.observations(horizon))
        .thenReturn(
            List.of(
                first,
                second,
                revision,
                forecast(4, "c", asOf.minusDays(10), "5600"),
                forecast(5, "d", asOf.minusDays(10), "5800")));
    when(market.dailyBars(Mockito.eq("^GSPC"), Mockito.any(), Mockito.any()))
        .thenReturn(List.of(bar(LocalDate.of(2025, 12, 31), 5000)));
    var result = service.outlook(horizon, asOf);
    assertThat(result.medianTargetLevel()).isEqualTo(5500);
    assertThat(result.p25TargetLevel()).isEqualTo(5350);
    assertThat(result.p75TargetLevel()).isEqualTo(5650);
    assertThat(result.revisionPercentagePoints())
        .isCloseTo(6, org.assertj.core.data.Offset.offset(.000001));
    assertThat(result.consensusTrend()[0].medianTargetLevel()).isEqualTo(5200);
    when(store.observations(horizon))
        .thenReturn(List.of(first, second, revision, forecast(4, "c", asOf.minusDays(10), "5600")));
    assertThat(service.outlook(horizon, asOf).medianTargetLevel()).isEqualTo(5400);
  }

  @Test
  void vixUsesTradingSessionsAndNeverChangesConsensus() {
    when(store.observations(horizon))
        .thenReturn(
            List.of(
                forecast(1, "a", asOf.minusDays(5), "5100"),
                forecast(2, "b", asOf.minusDays(5), "5300")));
    when(market.dailyBars(Mockito.eq("^GSPC"), Mockito.any(), Mockito.any()))
        .thenReturn(List.of(bar(LocalDate.of(2025, 12, 31), 5000)));
    var before = service.outlook(horizon, asOf);
    var vix = new java.util.ArrayList<DailyMarketBar>();
    for (LocalDate date = asOf.minusYears(1); !date.isAfter(asOf); date = date.plusDays(1))
      if (OutlookTradingDates.isTradingDay(date)) vix.add(bar(date, 10 + vix.size()));
    when(market.dailyBars(Mockito.eq("^VIX"), Mockito.any(), Mockito.any())).thenReturn(vix);
    var after = service.outlook(horizon, asOf);
    assertThat(after.medianFullYearPercent()).isEqualTo(before.medianFullYearPercent());
    double last = vix.getLast().close();
    assertThat(after.vix().change5TradingDaysPercent())
        .isCloseTo((last / (last - 5) - 1) * 100, org.assertj.core.data.Offset.offset(.000001));
    assertThat(after.vix().change20TradingDaysPercent())
        .isCloseTo((last / (last - 20) - 1) * 100, org.assertj.core.data.Offset.offset(.000001));
    assertThat(after.vix().percentileOneYear()).isEqualTo(100);
    assertThat(after.vix().trend()).allMatch(p -> OutlookTradingDates.isTradingDay(p.date()));
    when(market.dailyBars(Mockito.eq("^VIX"), Mockito.any(), Mockito.any()))
        .thenReturn(List.of(bar(asOf.minusDays(15), 20)));
    assertThat(service.outlook(horizon, asOf).vix().status()).contains("Stale");
    assertThat(service.outlook(horizon, asOf).vix().change5TradingDaysPercent()).isNull();
  }

  @Test
  void doesNotSubstituteLaterCloseForMissingOutcomeOrYearEndBaseline() {
    LocalDate target = LocalDate.of(2026, 6, 7);
    var f = forecast(1, "a", asOf.minusDays(5), "5000").forecast();
    var matured =
        new StrategistForecast(
            f.institutionKey(),
            f.institutionName(),
            null,
            java.time.Instant.parse("2026-01-01T00:00:00Z"),
            target,
            f.targetLevel(),
            f.sourceUrl(),
            f.sourceTitle(),
            f.sourceType(),
            java.time.Instant.parse("2026-01-02T00:00:00Z"));
    when(store.observations(target))
        .thenReturn(List.of(new ForecastObservation(1, matured, "VERIFIED", "ELIGIBLE")));
    when(market.dailyBars(Mockito.eq("^GSPC"), Mockito.any(), Mockito.any()))
        .thenReturn(List.of(bar(target.plusDays(2), 5000)));
    assertThat(service.outlook(target, asOf).accuracy().scoredForecasts()).isZero();
    when(store.observations(horizon))
        .thenReturn(
            List.of(
                forecast(1, "a", asOf.minusDays(5), "5100"),
                forecast(2, "b", asOf.minusDays(5), "5300")));
    when(market.dailyBars(Mockito.eq("^GSPC"), Mockito.any(), Mockito.any()))
        .thenReturn(List.of(bar(LocalDate.of(2025, 12, 30), 5000)));
    assertThat(service.outlook(horizon, asOf).medianFullYearPercent()).isNull();
  }

  @Test
  void staleIndexCloseKeepsItsDateButCannotSupplyCurrentMetrics() {
    when(store.observations(horizon))
        .thenReturn(
            List.of(
                forecast(1, "a", asOf.minusDays(5), "5100"),
                forecast(2, "b", asOf.minusDays(5), "5300")));
    when(market.dailyBars(Mockito.eq("^GSPC"), Mockito.any(), Mockito.any()))
        .thenReturn(List.of(bar(LocalDate.of(2025, 12, 31), 5000)));
    var result = service.outlook(horizon, asOf);
    assertThat(result.actualYtdPercent()).isNull();
    assertThat(result.medianRemainingPercent()).isNull();
    assertThat(result.forecasts())
        .allSatisfy(row -> assertThat(row.remainingReturnPercent()).isNull());
    assertThat(result.benchmarkQuoteDate()).isEqualTo(LocalDate.of(2025, 12, 31));
    assertThat(result.benchmarkStatus()).contains("Stale");
    // Target-year baseline still independently supports full-year forecasts.
    assertThat(result.medianFullYearPercent())
        .isCloseTo(4.0, org.assertj.core.data.Offset.offset(0.000001));
  }

  @Test
  void longHorizonHistoryRequestIncludesThePublicationReferenceClose() {
    LocalDate longTarget = LocalDate.of(2030, 12, 31);
    var published = java.time.Instant.parse("2020-07-01T12:00:00Z");
    var observation =
        new ForecastObservation(
            1,
            new StrategistForecast(
                "a",
                "a",
                null,
                published,
                longTarget,
                new BigDecimal("6000"),
                "https://example.com/long-horizon",
                "Source",
                StrategistForecast.SourceType.PRIMARY,
                published.plusSeconds(60)),
            "VERIFIED",
            "ELIGIBLE");
    when(store.observations(longTarget)).thenReturn(List.of(observation));
    LocalDate referenceDate = LocalDate.of(2020, 6, 30);
    when(market.dailyBars(Mockito.eq("^GSPC"), Mockito.any(), Mockito.any()))
        .thenAnswer(
            invocation ->
                referenceDate.isBefore(invocation.<LocalDate>getArgument(1))
                    ? List.of()
                    : List.of(bar(referenceDate, 3000)));
    var result = service.outlook(longTarget, asOf);
    assertThat(result.history()).hasSize(1);
    assertThat(result.history()[0].publicationQuoteDate()).isEqualTo(referenceDate);
    assertThat(result.history()[0].indexLevelAtPublication()).isEqualTo(3000);
    assertThat(result.history()[0].impliedChangePercent()).isEqualTo(100);
  }

  @Test
  void preBaselineSnapshotsRetainTargetsWithoutFutureReturnDispersion() {
    LocalDate january = LocalDate.of(2026, 1, 20);
    when(store.observations(horizon))
        .thenReturn(
            List.of(
                forecast(1, "a", LocalDate.of(2025, 12, 10), "5100"),
                forecast(2, "b", LocalDate.of(2025, 12, 11), "5300")));
    when(market.dailyBars(Mockito.eq("^GSPC"), Mockito.any(), Mockito.any()))
        .thenReturn(List.of(bar(LocalDate.of(2025, 12, 31), 5000)));
    var result = service.outlook(horizon, january);
    assertThat(result.consensusTrend())
        .filteredOn(p -> p.asOf().isBefore(LocalDate.of(2025, 12, 31)))
        .allSatisfy(p -> assertThat(p.dispersionPercentagePoints()).isNull());
    assertThat(result.consensusTrend()[1].medianTargetLevel()).isEqualTo(5200);
    assertThat(result.priorMedianFullYearPercent()).isNull();
    assertThat(result.revisionPercentagePoints()).isNull();
    assertThat(result.lastUpdateAgeDays()).isEqualTo(40);
    when(store.observations(horizon)).thenReturn(List.of());
    assertThat(service.outlook(horizon, january).lastUpdateAgeDays()).isNull();
  }

  @Test
  void vixFreshnessAcceptsFridayBeforeMondayHolidayButRequiresThatSession() {
    LocalDate tuesday = LocalDate.of(2026, 9, 8); // Monday was Labor Day.
    when(store.observations(horizon)).thenReturn(List.of());
    when(market.dailyBars(Mockito.eq("^VIX"), Mockito.any(), Mockito.any()))
        .thenReturn(List.of(bar(LocalDate.of(2026, 9, 4), 20)));
    var fresh = service.outlook(horizon, tuesday).vix();
    assertThat(fresh.status()).doesNotContain("Stale");
    assertThat(fresh.asOf()).isEqualTo(LocalDate.of(2026, 9, 4));
    when(market.dailyBars(Mockito.eq("^VIX"), Mockito.any(), Mockito.any()))
        .thenReturn(List.of(bar(LocalDate.of(2026, 9, 3), 20)));
    assertThat(service.outlook(horizon, tuesday).vix().status()).contains("Stale");
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"2026-01-01", "2026-01-02"})
  void newYearYtdUsesTheDisplayedQuoteCalendarYear(String selectedDate) {
    when(store.observations(horizon)).thenReturn(List.of());
    LocalDate previousBaseline = LocalDate.of(2024, 12, 31);
    when(market.dailyBars(Mockito.eq("^GSPC"), Mockito.any(), Mockito.any()))
        .thenAnswer(
            invocation ->
                List.of(bar(previousBaseline, 4000), bar(LocalDate.of(2025, 12, 31), 5000)).stream()
                    .filter(b -> !b.date().isBefore(invocation.<LocalDate>getArgument(1)))
                    .toList());
    var result = service.outlook(horizon, LocalDate.parse(selectedDate));
    assertThat(result.benchmarkQuoteDate()).isEqualTo(LocalDate.of(2025, 12, 31));
    assertThat(result.actualYtdPercent()).isEqualTo(25);
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
