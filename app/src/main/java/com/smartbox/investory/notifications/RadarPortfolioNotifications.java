package com.smartbox.investory.notifications;

import com.smartbox.investory.integrations.notifications.application.NotificationProperties;
import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import com.smartbox.investory.marketradar.api.MarketRadarApi;
import com.smartbox.investory.marketradar.api.ThemeRadarApi;
import com.smartbox.investory.marketradar.application.OutlookTradingDates;
import com.smartbox.investory.marketradar.domain.RadarRunStatus;
import com.smartbox.investory.marketradar.domain.RadarRunSummary;
import com.smartbox.investory.marketradar.domain.RadarSnapshot;
import com.smartbox.investory.marketradar.domain.RadarState;
import com.smartbox.investory.marketradar.domain.ThemeState;
import com.smartbox.investory.profile.api.ProfileSnapshotReader;
import com.smartbox.investory.profile.api.model.AssetHorizon;
import com.smartbox.investory.profile.api.model.EconomicBucket;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

/** Checks completed close observations and sends scoped reviews through the durable outbox. */
@Component
@ConditionalOnProperty(name = "app.notifications.radar.enabled", havingValue = "true")
@EnableConfigurationProperties(RadarNotificationSettings.class)
class RadarPortfolioNotifications {
  private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

  private final MarketRadarApi radar;
  private final ThemeRadarApi themes;
  private final InvestmentDashboardApi investment;
  private final ProfileSnapshotReader profiles;
  private final NotificationProperties notifications;
  private final RadarNotificationSettings settings;
  private final RadarNotificationState state;
  private final Clock clock;
  private final boolean scannerEnabled;
  private final String benchmark;
  private final CronExpression scanCron;
  private final CronExpression themeCron;

  RadarPortfolioNotifications(
      MarketRadarApi radar,
      ThemeRadarApi themes,
      InvestmentDashboardApi investment,
      ProfileSnapshotReader profiles,
      NotificationProperties notifications,
      RadarNotificationSettings settings,
      RadarNotificationState state,
      Clock clock,
      @Value("${app.market-radar.enabled:false}") boolean scannerEnabled,
      @Value("${app.market-radar.benchmark:SPY}") String benchmark,
      @Value("${app.market-radar.cron:0 30 22 * * 1-5}") String scanCron,
      @Value("${app.market-radar.theme-cron:0 50 22 * * 1-5}") String themeCron) {
    this.radar = radar;
    this.themes = themes;
    this.investment = investment;
    this.profiles = profiles;
    this.notifications = notifications;
    this.settings = settings;
    this.state = state;
    this.clock = clock;
    this.scannerEnabled = scannerEnabled;
    this.benchmark = benchmark.strip().toUpperCase(Locale.ROOT);
    this.scanCron = parseCron(scanCron);
    this.themeCron = parseCron(themeCron);
  }

  @Scheduled(fixedDelayString = "${app.notifications.radar.scan-check-ms:60000}")
  public void checkClose() {
    if (!notifications.isEnabled() || !scannerEnabled || scanCron == null) return;
    long portfolio = notifications.getPortfolioId();
    var now = clock.instant().atZone(WARSAW);
    var due = latestDue(now);
    if (due == null) return;
    var session = due.toLocalDate();
    var runs = radar.recentRuns(30);
    var run =
        runs.stream()
            .filter(r -> !r.startedAt().isBefore(due.toInstant()))
            .filter(r -> r.startedAt().atZone(WARSAW).toLocalDate().equals(session))
            .max(Comparator.comparing(RadarRunSummary::startedAt))
            .orElse(null);
    if (run == null || run.status() == RadarRunStatus.STARTED || run.completedAt() == null) {
      if (!now.isBefore(due.plus(settings.scanGrace()))) {
        state.scan(
            portfolio,
            session,
            true,
            "Scan for " + session + " missed its completion window." + lastSuccessful(runs),
            now.toInstant());
      }
      return;
    }
    int coverage = run.universeSize() > 0 ? (100 * run.stored()) / run.universeSize() : 0;
    var reference = radar.signalHistory(benchmark, settings.confirmationSessions());
    var freshBenchmark = on(reference, session);
    if (run.status() == RadarRunStatus.FAILED
        || coverage < settings.minimumCoveragePct()
        || !usable(freshBenchmark)) {
      state.scan(
          portfolio,
          session,
          true,
          "Scan for "
              + session
              + " recorded "
              + run.status()
              + ", saved "
              + run.stored()
              + "/"
              + run.universeSize()
              + " symbols ("
              + coverage
              + "%). "
              + (!usable(freshBenchmark)
                  ? "A fresh " + benchmark + " comparison is unavailable."
                  : "")
              + lastSuccessful(runs),
          now.toInstant());
      return;
    }
    state.scan(
        portfolio,
        session,
        false,
        "Scan for "
            + session
            + " saved "
            + run.stored()
            + "/"
            + run.universeSize()
            + " symbols and has a fresh benchmark.",
        now.toInstant());

    var themeDue = dueOn(themeCron, session);
    if ((themeDue != null && now.isBefore(themeDue.plus(settings.themeGrace())))
        || state.digestProcessed(portfolio, session)) return;
    review(portfolio, session, now, reference);
  }

  @Scheduled(fixedDelayString = "${app.notifications.radar.reserve-check-ms:300000}")
  public void checkReserve() {
    var config = settings.reserve();
    if (!notifications.isEnabled() || config == null || config.floor() == null) return;
    long portfolio = notifications.getPortfolioId();
    var observedAt = clock.instant();
    var profile = profiles.loadProfile(portfolio);
    if (profile == null
        || profile.currency() != config.currency()
        || !profile.allocationReconciliation().balanced()) return;
    var shortCash = cash(profile.allocations(), AssetHorizon.SHORT_TERM);
    var longCash = cash(profile.allocations(), AssetHorizon.LONG_TERM);
    if (shortCash
            .add(longCash)
            .subtract(profile.retirementReserve())
            .abs()
            .compareTo(new BigDecimal("0.01"))
        > 0) return;
    state.reserve(portfolio, shortCash, longCash, config, observedAt);
  }

  private static BigDecimal cash(
      List<com.smartbox.investory.profile.api.model.ProfileAllocation> allocations,
      AssetHorizon horizon) {
    return allocations.stream()
        .filter(
            a ->
                a.assetHorizon() == horizon
                    && a.bucket() == EconomicBucket.LIQUID_CASH
                    && a.liquidity() == com.smartbox.investory.profile.api.model.Liquidity.LIQUID)
        .map(com.smartbox.investory.profile.api.model.ProfileAllocation::value)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private void review(
      long portfolio, LocalDate session, ZonedDateTime now, List<RadarSnapshot> reference) {
    var page =
        investment.loadDashboard(
            new InvestmentDashboardApi.DashboardQuery(
                List.of(), false, DashboardPeriod.MAX, portfolio));
    if (page == null
        || page.overview() == null
        || page.overview().balance() == null
        || page.overview().baseCurrency() == null
        || page.overview().positions() == null
        || page.overview().balance().signum() <= 0) return;
    var quality = page.dataQuality();
    if (quality == null
        || quality.latestPriceDate() == null
        || !quality.latestPriceDate().equals(session)
        || quality.missingPriceCount() != 0
        || quality.stalePriceCount() != 0
        || quality.missingFxCount() != 0
        || quality.estimatedPriceCount() != 0
        || quality.pricedOpenPositions() < quality.totalOpenPositions()) return;
    var overview = page.overview();
    var holdings = new TreeMap<String, BigDecimal>();
    for (var row : overview.openPositionValues()) {
      if (row.getSymbol() != null && row.getValue() != null) {
        holdings.merge(row.getSymbol().toUpperCase(Locale.ROOT), row.getValue(), BigDecimal::add);
      }
    }

    var sessions = new ArrayList<LocalDate>();
    for (var date = session;
        sessions.size() < settings.confirmationSessions();
        date = OutlookTradingDates.previousOrSame(date.minusDays(1))) sessions.add(date);
    var observations = new ArrayList<RadarNotificationState.Observation>();
    var latest =
        radar.latestSignals().stream()
            .filter(s -> s.date().equals(session))
            .collect(java.util.stream.Collectors.toMap(RadarSnapshot::symbol, s -> s));
    for (var held : holdings.entrySet()) {
      String symbol = held.getKey();
      String radarSymbol = radarSymbol(symbol, latest);
      if (radarSymbol == null) continue;
      double weight = weight(held.getValue(), overview.balance());
      addSymbol(
          observations,
          "HOLD:" + symbol,
          radarSymbol,
          sessions,
          reference,
          s -> s.state() == RadarState.COOLING,
          weight > settings.materialWeightPct(),
          "Review " + symbol + " (" + pct(weight) + " of brokerage)",
          "/portfolios/" + portfolio + "/dashboard/assets/" + segment(symbol));
    }
    for (String watched :
        settings.watchSymbols().stream()
            .map(s -> s.strip().toUpperCase(Locale.ROOT))
            .distinct()
            .toList()) {
      String radarSymbol = radarSymbol(watched, latest);
      if (radarSymbol == null) continue;
      addSymbol(
          observations,
          "WATCH:" + watched,
          radarSymbol,
          sessions,
          reference,
          s -> s.state() == RadarState.TRENDING || s.state() == RadarState.HOT,
          true,
          "Watch " + watched,
          "/market-radar/" + segment(radarSymbol));
    }
    for (var theme : themes.latestThemes()) {
      if (!theme.date().equals(session)) continue;
      var configured = settings.themeHoldings().getOrDefault(theme.theme(), List.of());
      var exposed =
          configured.stream()
              .map(s -> s.toUpperCase(Locale.ROOT))
              .distinct()
              .filter(s -> holdings.getOrDefault(s, BigDecimal.ZERO).signum() > 0)
              .toList();
      if (!settings.watchThemes().contains(theme.theme()) && exposed.isEmpty()) continue;
      var history = themes.themeHistory(theme.theme(), settings.confirmationSessions());
      boolean qualifies = theme.state() == ThemeState.STRONG && theme.proxyReturn20d() > 0;
      boolean confirmed =
          sessions.stream()
              .allMatch(
                  d ->
                      history.stream()
                          .anyMatch(
                              t ->
                                  t.date().equals(d)
                                      && t.state() == ThemeState.STRONG
                                      && t.proxyReturn20d() > 0));
      observations.add(
          new RadarNotificationState.Observation(
              "THEME:" + theme.theme(),
              qualifies,
              confirmed,
              theme.theme()
                  + " ("
                  + theme.proxySymbol()
                  + ") is strong. Proxy "
                  + pct(theme.proxyReturn20d() * 100)
                  + ", versus "
                  + benchmark
                  + " "
                  + points(theme.relativeStrength20d() * 100)
                  + ". "
                  + (exposed.isEmpty()
                      ? "No configured held exposure."
                      : "Direct holdings: "
                          + String.join(", ", exposed)
                          + " ("
                          + pct(
                              weight(
                                  exposed.stream()
                                      .map(holdings::get)
                                      .reduce(BigDecimal.ZERO, BigDecimal::add),
                                  overview.balance()))
                          + " of brokerage)."),
              "/market-radar/themes"));
    }
    settings.allocationBands().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry -> {
              var band = entry.getValue();
              var symbols =
                  band.symbols().stream()
                      .map(s -> s.strip().toUpperCase(Locale.ROOT))
                      .distinct()
                      .toList();
              var amount =
                  symbols.stream()
                      .map(s -> holdings.getOrDefault(s, BigDecimal.ZERO))
                      .reduce(BigDecimal.ZERO, BigDecimal::add);
              double current = weight(amount, overview.balance());
              boolean below = current < band.minimumPct(), above = current > band.maximumPct();
              double boundary = below ? band.minimumPct() : band.maximumPct();
              var difference =
                  overview
                      .balance()
                      .multiply(BigDecimal.valueOf(boundary).movePointLeft(2))
                      .subtract(amount)
                      .abs();
              addBand(
                  observations,
                  entry.getKey(),
                  below,
                  "below",
                  current,
                  band,
                  difference,
                  overview.baseCurrency().name(),
                  portfolio);
              addBand(
                  observations,
                  entry.getKey(),
                  above,
                  "above",
                  current,
                  band,
                  difference,
                  overview.baseCurrency().name(),
                  portfolio);
            });
    state.digest(
        portfolio,
        session,
        sessions.get(1),
        settings.confirmationSessions(),
        observations,
        holdings.keySet(),
        now.toInstant());
  }

  private void addSymbol(
      List<RadarNotificationState.Observation> observations,
      String rule,
      String symbol,
      List<LocalDate> sessions,
      List<RadarSnapshot> reference,
      Predicate<RadarSnapshot> condition,
      boolean eligible,
      String title,
      String path) {
    var history = radar.signalHistory(symbol, sessions.size());
    var current = on(history, sessions.getFirst());
    var compared = on(reference, sessions.getFirst());
    if (!usable(current) || !usable(compared)) return;
    boolean watch = rule.startsWith("WATCH:");
    boolean qualifies =
        eligible
            && condition.test(current)
            && (!watch || current.return20d() > compared.return20d());
    boolean confirmed =
        qualifies
            && sessions.stream()
                .allMatch(
                    date -> {
                      var snapshot = on(history, date);
                      var benchmarkSnapshot = on(reference, date);
                      return usable(snapshot)
                          && usable(benchmarkSnapshot)
                          && condition.test(snapshot)
                          && (!watch || snapshot.return20d() > benchmarkSnapshot.return20d());
                    });
    String volume =
        current.relativeVolume20d() == null
            ? ""
            : ", relative volume "
                + String.format(Locale.ROOT, "%.2f×", current.relativeVolume20d());
    observations.add(
        new RadarNotificationState.Observation(
            rule,
            qualifies,
            confirmed,
            title
                + " is "
                + current.state()
                + " across "
                + sessions.size()
                + " confirmed closes. Twenty-session return "
                + pct(100 * current.return20d())
                + ", versus "
                + benchmark
                + " "
                + points(100 * (current.return20d() - compared.return20d()))
                + volume
                + ".",
            path));
  }

  private static void addBand(
      List<RadarNotificationState.Observation> observations,
      String name,
      boolean outside,
      String direction,
      double weight,
      RadarNotificationSettings.AllocationBand band,
      BigDecimal difference,
      String currency,
      long portfolio) {
    observations.add(
        new RadarNotificationState.Observation(
            "BAND:" + direction + ":" + name,
            outside,
            false,
            name
                + " is "
                + direction
                + " its band at "
                + pct(weight)
                + " (target "
                + pct(band.minimumPct())
                + "–"
                + pct(band.maximumPct())
                + "). "
                + RadarNotificationState.money(difference, currency)
                + " to the nearest boundary at current valuation. Direct positions only.",
            "/portfolios/" + portfolio + "/dashboard"));
  }

  private ZonedDateTime latestDue(ZonedDateTime now) {
    for (int i = 0; i < 8; i++) {
      var date = now.toLocalDate().minusDays(i);
      if (!OutlookTradingDates.isTradingDay(date)) continue;
      var due = dueOn(scanCron, date);
      if (due != null && !due.isAfter(now)) return due;
    }
    return null;
  }

  private static ZonedDateTime dueOn(CronExpression cron, LocalDate day) {
    var first = day.atStartOfDay(WARSAW);
    var next = cron == null ? null : cron.next(first.minusSeconds(1));
    return next != null && next.toLocalDate().equals(day) ? next : null;
  }

  private static CronExpression parseCron(String value) {
    return "-".equals(value) ? null : CronExpression.parse(value);
  }

  private String lastSuccessful(List<RadarRunSummary> runs) {
    return runs.stream()
        .filter(r -> r.status() == RadarRunStatus.SUCCESS && r.completedAt() != null)
        .map(RadarRunSummary::completedAt)
        .max(Comparator.naturalOrder())
        .map(t -> " Last successful run " + t.atZone(WARSAW).toLocalDate() + ".")
        .orElse("");
  }

  static RadarSnapshot on(List<RadarSnapshot> history, LocalDate date) {
    return history.stream().filter(s -> date.equals(s.date())).findFirst().orElse(null);
  }

  private static boolean usable(RadarSnapshot s) {
    return s != null && s.return20d() != null && Double.isFinite(s.return20d());
  }

  private static String radarSymbol(String symbol, Map<String, RadarSnapshot> latest) {
    if (latest.containsKey(symbol)) return symbol;
    if (symbol.endsWith(".US")) {
      var us = symbol.substring(0, symbol.length() - 3);
      if (latest.containsKey(us)) return us;
    }
    return null;
  }

  private static double weight(BigDecimal value, BigDecimal total) {
    return value.movePointRight(2).divide(total, 8, java.math.RoundingMode.HALF_UP).doubleValue();
  }

  private static String pct(double value) {
    return String.format(Locale.ROOT, "%.1f%%", value);
  }

  private static String points(double value) {
    return String.format(Locale.ROOT, "%+.1f pp", value);
  }

  private static String segment(String value) {
    return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
  }
}
