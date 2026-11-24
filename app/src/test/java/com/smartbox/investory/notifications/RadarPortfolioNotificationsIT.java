package com.smartbox.investory.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.integrations.notifications.application.NotificationProperties;
import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import com.smartbox.investory.investment.api.reporting.model.DataQualityView;
import com.smartbox.investory.investment.api.reporting.model.OpenPositionValue;
import com.smartbox.investory.investment.api.reporting.model.OverviewView;
import com.smartbox.investory.investment.api.reporting.model.PositionsView;
import com.smartbox.investory.marketradar.api.MarketRadarApi;
import com.smartbox.investory.marketradar.api.ThemeRadarApi;
import com.smartbox.investory.marketradar.domain.RadarRunStatus;
import com.smartbox.investory.marketradar.domain.RadarRunSummary;
import com.smartbox.investory.marketradar.domain.RadarSnapshot;
import com.smartbox.investory.marketradar.domain.RadarState;
import com.smartbox.investory.marketradar.domain.ThemeSnapshot;
import com.smartbox.investory.marketradar.domain.ThemeState;
import com.smartbox.investory.profile.api.ProfileSnapshotReader;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.testsupport.FastDatabaseTest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "app.notifications.radar.enabled=true")
class RadarPortfolioNotificationsIT extends FastDatabaseTest {
  private static final long PORTFOLIO = 2;
  private static final LocalDate FIRST = LocalDate.of(2026, 11, 18);
  @Autowired private JdbcTemplate jdbc;
  @Autowired private RadarNotificationState state;
  @Autowired private RadarPortfolioNotifications wiredJob;
  @Autowired private RadarNotificationSettings boundSettings;

  private final MarketRadarApi scanner = mock(MarketRadarApi.class);
  private final ThemeRadarApi themes = mock(ThemeRadarApi.class);
  private final InvestmentDashboardApi reporting = mock(InvestmentDashboardApi.class);

  @BeforeEach
  @AfterEach
  void clean() {
    jdbc.update(
        "DELETE FROM investory.notification_event WHERE source_entity_type='RADAR_NOTIFICATION' AND portfolio_id=?",
        PORTFOLIO);
    jdbc.update("DELETE FROM investory.radar_notification_state WHERE portfolio_id=?", PORTFOLIO);
  }

  @Test
  void consolidatesConfirmedCloseChangesAndDoesNotRepeatOnAnotherEvaluation() {
    assertThat(wiredJob).isNotNull();
    assertThat(boundSettings.watchSymbols()).containsExactly("DIS");

    setupSession(FIRST, false);
    job(FIRST, "22:20:00").checkClose();
    assertThat(messages()).isEmpty();

    var next = FIRST.plusDays(1);
    setupSession(next, true);
    job(next, "21:55:00").checkClose();
    assertThat(messages()).isEmpty();
    job(next, "22:20:00").checkClose();
    job(next, "22:21:00").checkClose();

    assertThat(messages())
        .singleElement()
        .asString()
        .contains(
            "Review NVDA.US",
            "Watch DIS",
            "Defense (ITA) is strong",
            "semiconductors is above",
            "1,500.00 USD to the nearest boundary");
    assertThat(state.digestProcessed(PORTFOLIO, next)).isTrue();
  }

  @Test
  void detectsMissingOrDegradedScanAndSendsOneRecovery() {
    when(scanner.recentRuns(30)).thenReturn(List.of());
    job(FIRST, "21:40:00").checkClose();
    assertThat(messages()).isEmpty();
    job(FIRST, "21:55:00").checkClose();
    job(FIRST, "21:58:00").checkClose();

    when(scanner.recentRuns(30)).thenReturn(List.of(run(FIRST, 81)));
    job(FIRST, "21:59:00").checkClose();
    assertThat(titles()).containsExactly("Market Radar needs attention");

    setupSession(FIRST, false);
    job(FIRST, "22:00:00").checkClose();
    job(FIRST, "22:01:00").checkClose();
    assertThat(titles()).containsExactly("Market Radar needs attention", "Market Radar recovered");
  }

  @Test
  void reserveReportsTheChangedBucketsAndRearmsOnlyAfterRecovery() {
    var config =
        new RadarNotificationSettings.Reserve(
            new BigDecimal("6000"), CurrencyType.PLN, new BigDecimal("1000"));
    var now = Instant.parse("2026-11-19T12:00:00Z");
    state.reserve(PORTFOLIO, new BigDecimal("5000"), new BigDecimal("3000"), config, now);
    state.reserve(
        PORTFOLIO, new BigDecimal("3500"), new BigDecimal("2500"), config, now.plusSeconds(1));
    state.reserve(
        PORTFOLIO, new BigDecimal("3500"), new BigDecimal("2500"), config, now.plusSeconds(2));
    assertThat(messages())
        .singleElement()
        .asString()
        .contains(
            "Shortfall 1,000.00 PLN",
            "change -1,500.00 PLN",
            "change -500.00 PLN",
            "Cash after configured commitments 5,000.00 PLN");

    state.reserve(PORTFOLIO, new BigDecimal("5000"), new BigDecimal("3000"), config, now);
    state.reserve(
        PORTFOLIO, new BigDecimal("3500"), new BigDecimal("2500"), config, now.plusMillis(2500));
    assertThat(titles()).containsExactly("Retirement reserve below floor");

    state.reserve(
        PORTFOLIO, new BigDecimal("5000"), new BigDecimal("3000"), config, now.plusSeconds(3));
    state.reserve(
        PORTFOLIO, new BigDecimal("3500"), new BigDecimal("2500"), config, now.plusSeconds(4));
    assertThat(titles())
        .containsExactly("Retirement reserve below floor", "Retirement reserve below floor");
  }

  @Test
  void ignoresAWatchlistSignalThatLagsSpyAndRelativeOnlyThemeStrength() {
    setupSession(FIRST, false);
    var before = FIRST.minusDays(1);
    var lagging = sample("DIS", FIRST, RadarState.TRENDING, 0.01);
    when(scanner.latestSignals()).thenReturn(List.of(lagging));
    when(scanner.signalHistory("DIS", 2))
        .thenReturn(List.of(lagging, sample("DIS", before, RadarState.TRENDING, 0.06)));
    var theme =
        new ThemeSnapshot(
            "Defense", "ITA", FIRST, ThemeState.STRONG, 5, 0.7, 0.8, -0.01, -0.10, 0.09);
    when(themes.latestThemes()).thenReturn(List.of(theme));

    job(FIRST, "22:20:00").checkClose();

    assertThat(messages()).isEmpty();
    assertThat(state.digestProcessed(PORTFOLIO, FIRST)).isTrue();
  }

  @Test
  void insufficientCoverageSuppressesDigestAndMissingSessionBreaksAnAllocationStreak() {
    setupSession(FIRST, false);
    job(FIRST, "22:20:00").checkClose();
    var next = FIRST.plusDays(1);
    when(scanner.recentRuns(30)).thenReturn(List.of(run(next, 89)));
    job(next, "22:20:00").checkClose();
    assertThat(state.digestProcessed(PORTFOLIO, next)).isFalse();

    var third = next.plusDays(1);
    setupSession(third, false);
    job(third, "22:20:00").checkClose();
    assertThat(titles()).containsExactly("Market Radar needs attention", "Market Radar recovered");
  }

  private void setupSession(LocalDate date, boolean qualifies) {
    var before = date.minusDays(1);
    var spy = sample("SPY", date, RadarState.NORMAL, 0.02);
    var spyPrevious = sample("SPY", before, RadarState.NORMAL, 0.01);
    var cooling = qualifies ? RadarState.COOLING : RadarState.NORMAL;
    var trending = qualifies ? RadarState.TRENDING : RadarState.NORMAL;
    var nvda = sample("NVDA", date, cooling, -0.10);
    var dis = sample("DIS", date, trending, 0.08);
    when(scanner.recentRuns(30)).thenReturn(List.of(run(date, 100)));
    when(scanner.latestSignals()).thenReturn(List.of(spy, nvda, dis));
    when(scanner.signalHistory("SPY", 2)).thenReturn(List.of(spy, spyPrevious));
    when(scanner.signalHistory("NVDA", 2))
        .thenReturn(List.of(nvda, sample("NVDA", before, cooling, -0.08)));
    when(scanner.signalHistory("DIS", 2))
        .thenReturn(List.of(dis, sample("DIS", before, trending, 0.06)));
    var defense =
        new ThemeSnapshot(
            "Defense",
            "ITA",
            date,
            qualifies ? ThemeState.STRONG : ThemeState.NEUTRAL,
            5,
            0.7,
            0.8,
            0.03,
            0.02,
            0.01);
    var defenseBefore =
        new ThemeSnapshot("Defense", "ITA", before, defense.state(), 5, 0.7, 0.8, 0.04, 0.01, 0.03);
    when(themes.latestThemes()).thenReturn(List.of(defense));
    when(themes.themeHistory("Defense", 2)).thenReturn(List.of(defense, defenseBefore));

    var quality = mock(DataQualityView.class);
    when(quality.latestPriceDate()).thenReturn(date);
    var overview = mock(OverviewView.class);
    when(overview.balance()).thenReturn(new BigDecimal("100000"));
    when(overview.baseCurrency()).thenReturn(CurrencyType.USD);
    when(overview.positions()).thenReturn(mock(PositionsView.class));
    var position = new OpenPositionValue();
    position.setSymbol("NVDA.US");
    position.setValue(new BigDecimal("6500"));
    when(overview.openPositionValues()).thenReturn(List.of(position));
    var page = mock(InvestmentDashboardApi.DashboardPageView.class);
    when(page.dataQuality()).thenReturn(quality);
    when(page.overview()).thenReturn(overview);
    when(reporting.loadDashboard(any())).thenReturn(page);
  }

  private RadarPortfolioNotifications job(LocalDate date, String utc) {
    var notifications = new NotificationProperties();
    notifications.setEnabled(true);
    notifications.setPortfolioId(PORTFOLIO);
    var settings =
        new RadarNotificationSettings(
            3,
            2,
            90,
            Duration.ofMinutes(20),
            Duration.ofMinutes(15),
            List.of("DIS"),
            List.of("Defense"),
            Map.of(),
            Map.of(
                "semiconductors",
                new RadarNotificationSettings.AllocationBand(0, 5, List.of("NVDA.US"))),
            null);
    return new RadarPortfolioNotifications(
        scanner,
        themes,
        reporting,
        mock(ProfileSnapshotReader.class),
        notifications,
        settings,
        state,
        Clock.fixed(Instant.parse(date + "T" + utc + "Z"), ZoneOffset.UTC),
        true,
        "SPY",
        "0 30 22 * * 1-5",
        "0 50 22 * * 1-5");
  }

  private static RadarRunSummary run(LocalDate date, int stored) {
    var start = Instant.parse(date + "T21:30:00Z");
    return new RadarRunSummary(
        UUID.randomUUID(),
        start,
        start.plusSeconds(60),
        RadarRunStatus.SUCCESS,
        100,
        100,
        stored,
        100 - stored,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        60_000,
        null);
  }

  private static RadarSnapshot sample(
      String symbol, LocalDate day, RadarState state, double relative) {
    return new RadarSnapshot(symbol, day, state, 100, relative, null, 1.4, null, null, List.of());
  }

  private List<String> messages() {
    return jdbc.queryForList(
        "SELECT payload->>'message' FROM investory.notification_event WHERE portfolio_id=? AND source_entity_type='RADAR_NOTIFICATION' ORDER BY id",
        String.class,
        PORTFOLIO);
  }

  private List<String> titles() {
    return jdbc.queryForList(
        "SELECT title FROM investory.notification_event WHERE portfolio_id=? AND source_entity_type='RADAR_NOTIFICATION' ORDER BY id",
        String.class,
        PORTFOLIO);
  }
}
