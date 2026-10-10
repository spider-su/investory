package com.smartbox.investory.marketradar.application;

import com.smartbox.investory.marketradar.api.StrategistOutlookApi;
import com.smartbox.investory.marketradar.api.StrategistOutlookApi.*;
import com.smartbox.investory.marketradar.domain.DailyMarketBar;
import com.smartbox.investory.marketradar.port.HistoricalMarketDataPort;
import com.smartbox.investory.marketradar.port.StrategistForecastStore;
import com.smartbox.investory.marketradar.port.StrategistForecastStore.ForecastObservation;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class StrategistOutlookService implements StrategistOutlookApi {
  private static final int STALE_DAYS = 120;
  private final StrategistForecastStore store;
  private final HistoricalMarketDataPort market;

  public StrategistOutlookService(StrategistForecastStore store, HistoricalMarketDataPort market) {
    this.store = store;
    this.market = market;
  }

  @Override
  public Outlook outlook(LocalDate targetDate, LocalDate asOf) {
    if (targetDate == null || asOf == null)
      throw new IllegalArgumentException("targetDate and asOf are required");
    List<ForecastObservation> all =
        store.observations(targetDate).stream()
            .filter(o -> o.forecast().targetDate().equals(targetDate))
            .toList();
    List<ForecastObservation> eligible =
        all.stream()
            .filter(
                o ->
                    o.status().equals("ELIGIBLE")
                        && !utcDate(o.forecast().publishedAt()).isAfter(asOf)
                        && !utcDate(o.forecast().observedAt()).isAfter(asOf))
            .toList();
    Map<String, ForecastObservation> latest = new TreeMap<>();
    eligible.forEach(
        o -> latest.merge(o.forecast().institutionKey(), o, (a, b) -> compare(a, b) >= 0 ? a : b));
    int stale = (int) latest.values().stream().filter(o -> age(o, asOf) > STALE_DAYS).count();
    List<ForecastObservation> current =
        latest.values().stream().filter(o -> age(o, asOf) <= STALE_DAYS).toList();

    LocalDate targetBaselineDate = LocalDate.of(targetDate.getYear() - 1, 12, 31);
    // A fresh New Year quote can still belong to the preceding calendar year.
    LocalDate ytdHistoryBaseline = LocalDate.of(asOf.getYear() - 2, 12, 31);
    LocalDate earliestBaseline =
        targetBaselineDate.isBefore(ytdHistoryBaseline) ? targetBaselineDate : ytdHistoryBaseline;
    LocalDate earliestPublication =
        eligible.stream()
            .map(o -> publicationReferenceDate(o.forecast().publishedAt()))
            .min(LocalDate::compareTo)
            .orElse(earliestBaseline);
    LocalDate dataFrom =
        (earliestPublication.isBefore(earliestBaseline) ? earliestPublication : earliestBaseline)
            .minusDays(10);
    LocalDate dataThrough = asOf.isAfter(targetDate.plusDays(10)) ? asOf : targetDate.plusDays(10);
    List<DailyMarketBar> bars =
        loadBars("^GSPC", dataFrom, dataThrough).stream()
            .filter(b -> !b.date().isAfter(asOf))
            .sorted(Comparator.comparing(DailyMarketBar::date))
            .toList();
    DailyMarketBar baselineQuote =
        exact(bars, OutlookTradingDates.previousOrSame(targetBaselineDate));
    DailyMarketBar targetBaseline =
        baselineQuote != null && baselineQuote.date().isBefore(asOf) ? baselineQuote : null;
    DailyMarketBar latestBar = bars.isEmpty() ? null : bars.get(bars.size() - 1);
    // The selected date may be intraday; at least the preceding regular session must be present.
    LocalDate requiredClose = OutlookTradingDates.previousOrSame(asOf.minusDays(1));
    DailyMarketBar currentBar =
        latestBar == null || latestBar.date().isBefore(requiredClose) ? null : latestBar;
    DailyMarketBar ytdBaseline =
        currentBar == null
            ? null
            : exact(
                bars,
                OutlookTradingDates.previousOrSame(
                    LocalDate.of(currentBar.date().getYear() - 1, 12, 31)));
    Double ytd =
        currentBar == null || ytdBaseline == null
            ? null
            : pct(currentBar.close(), ytdBaseline.close());
    boolean calendarYearEnd = targetDate.getMonthValue() == 12 && targetDate.getDayOfMonth() == 31;
    List<Double> annual =
        current.stream()
            .map(
                o ->
                    !calendarYearEnd || targetBaseline == null
                        ? null
                        : pct(o.forecast().targetLevel().doubleValue(), targetBaseline.close()))
            .filter(Objects::nonNull)
            .sorted()
            .toList();
    List<Double> remaining =
        current.stream()
            .map(
                o ->
                    currentBar == null
                        ? null
                        : pct(o.forecast().targetLevel().doubleValue(), currentBar.close()))
            .filter(Objects::nonNull)
            .sorted()
            .toList();
    Double median = median(annual), remainingMedian = median(remaining);
    Double min = annual.size() < 2 ? null : annual.get(0),
        max = annual.size() < 2 ? null : annual.get(annual.size() - 1);
    Double prior = calendarYearEnd ? medianAt(all, asOf.minusDays(30), targetBaseline) : null;
    Double revision = median == null || prior == null ? null : median - prior;

    ListRow[] rows =
        current.stream()
            .map(
                o ->
                    row(
                        o,
                        calendarYearEnd ? annualReturn(o, targetBaseline) : null,
                        remainingReturn(o, currentBar),
                        revisionLabel(eligible, o),
                        bars,
                        null,
                        null,
                        null))
            .toArray(ListRow[]::new);
    ListRow[] history =
        eligible.stream()
            .sorted(Comparator.comparing(o -> o.forecast().publishedAt()))
            .map(
                o ->
                    scoredRow(
                        o,
                        calendarYearEnd ? annualReturn(o, targetBaseline) : null,
                        remainingReturn(o, currentBar),
                        bars,
                        asOf,
                        revisionLabel(eligible, o)))
            .toArray(ListRow[]::new);
    Accuracy accuracy = accuracy(eligible, bars, asOf);
    VixContext vix = vix(asOf);
    List<Double> targets =
        current.stream().map(o -> o.forecast().targetLevel().doubleValue()).sorted().toList();
    SortedSet<LocalDate> snapshots = new TreeSet<>();
    eligible.forEach(
        o ->
            snapshots.add(
                Collections.max(
                    List.of(
                        utcDate(o.forecast().publishedAt()), utcDate(o.forecast().observedAt())))));
    snapshots.add(asOf);
    ConsensusPoint[] trend =
        snapshots.stream()
            .map(
                date -> {
                  List<Double> values =
                      selected(all, date).stream()
                          .map(o -> o.forecast().targetLevel().doubleValue())
                          .sorted()
                          .toList();
                  Double lo = values.size() < 2 ? null : values.getFirst(),
                      hi = values.size() < 2 ? null : values.getLast();
                  return new ConsensusPoint(
                      date,
                      median(values),
                      lo,
                      hi,
                      !calendarYearEnd
                              || targetBaseline == null
                              || !targetBaseline.date().isBefore(date)
                              || lo == null
                          ? null
                          : (hi - lo) / targetBaseline.close() * 100);
                })
            .toArray(ConsensusPoint[]::new);
    return new Outlook(
        targetDate,
        asOf,
        ytd,
        median,
        remainingMedian,
        min == null || max == null ? null : max - min,
        min,
        max,
        annual.size() >= 4 ? percentile(annual, .25) : null,
        annual.size() >= 4 ? percentile(annual, .75) : null,
        prior,
        revision,
        current.size(),
        stale,
        current.stream()
            .map(o -> utcDate(o.forecast().publishedAt()))
            .min(LocalDate::compareTo)
            .orElse(null),
        current.stream()
            .map(o -> utcDate(o.forecast().publishedAt()))
            .max(LocalDate::compareTo)
            .orElse(null),
        latestBar != null && currentBar == null
            ? "Stale S&P 500 close; current metrics unavailable"
            : targetBaseline == null || currentBar == null
                ? "S&P 500 price-index history unavailable"
                : "S&P 500 price index; provider quote dates",
        rows,
        history,
        accuracy,
        vix,
        latestBar == null ? null : latestBar.date(),
        median(targets),
        targets.size() < 2 ? null : targets.getFirst(),
        targets.size() < 2 ? null : targets.getLast(),
        targets.size() < 4 ? null : percentile(targets, .25),
        targets.size() < 4 ? null : percentile(targets, .75),
        trend,
        current.stream().map(o -> age(o, asOf)).min(Long::compareTo).orElse(null));
  }

  private static ListRow row(
      ForecastObservation o,
      Double annual,
      Double remaining,
      String revision,
      List<DailyMarketBar> bars,
      Double error,
      Double errorPercent,
      LocalDate outcome) {
    var f = o.forecast();
    DailyMarketBar publication = publicationClose(bars, f.publishedAt());
    return new ListRow(
        f.institutionName(),
        f.strategist(),
        utcDate(f.publishedAt()),
        f.targetDate(),
        f.targetLevel().doubleValue(),
        annual,
        remaining,
        f.sourceTitle(),
        f.sourceUrl(),
        o.quality(),
        revision,
        error,
        errorPercent,
        outcome,
        publication == null ? null : publication.close(),
        publication == null ? null : publication.date(),
        publication == null ? null : pct(f.targetLevel().doubleValue(), publication.close()));
  }

  private static ListRow scoredRow(
      ForecastObservation o,
      Double annual,
      Double remaining,
      List<DailyMarketBar> bars,
      LocalDate asOf,
      String revision) {
    LocalDate target = o.forecast().targetDate();
    if (target.isAfter(asOf)) return row(o, annual, remaining, revision, bars, null, null, null);
    LocalDate outcomeDate = OutlookTradingDates.nextOrSame(target);
    if (!outcomeDate.isBefore(asOf))
      return row(o, annual, remaining, revision, bars, null, null, null);
    DailyMarketBar actual = exact(bars, outcomeDate);
    if (actual == null) return row(o, annual, remaining, revision, bars, null, null, null);
    double error = o.forecast().targetLevel().doubleValue() - actual.close();
    return row(
        o, annual, remaining, revision, bars, error, error / actual.close() * 100, actual.date());
  }

  private static Double annualReturn(ForecastObservation o, DailyMarketBar b) {
    return b == null ? null : pct(o.forecast().targetLevel().doubleValue(), b.close());
  }

  private static Double remainingReturn(ForecastObservation o, DailyMarketBar b) {
    return b == null ? null : pct(o.forecast().targetLevel().doubleValue(), b.close());
  }

  private static int compare(ForecastObservation a, ForecastObservation b) {
    int c = a.forecast().publishedAt().compareTo(b.forecast().publishedAt());
    if (c == 0) c = a.forecast().observedAt().compareTo(b.forecast().observedAt());
    return c == 0 ? Long.compare(a.id(), b.id()) : c;
  }

  private static long age(ForecastObservation o, LocalDate d) {
    return ChronoUnit.DAYS.between(utcDate(o.forecast().publishedAt()), d);
  }

  private static DailyMarketBar lastOnOrBefore(List<DailyMarketBar> bars, LocalDate d) {
    return bars.stream().filter(b -> !b.date().isAfter(d)).reduce((a, b) -> b).orElse(null);
  }

  private static Double medianAt(
      List<ForecastObservation> all, LocalDate date, DailyMarketBar baseline) {
    if (baseline == null || !baseline.date().isBefore(date)) return null;
    return median(
        selected(all, date).stream()
            .map(o -> pct(o.forecast().targetLevel().doubleValue(), baseline.close()))
            .sorted()
            .toList());
  }

  private static String revisionLabel(List<ForecastObservation> all, ForecastObservation o) {
    return all.stream()
        .filter(
            x ->
                x.forecast().institutionKey().equals(o.forecast().institutionKey())
                    && compare(x, o) < 0)
        .max(StrategistOutlookService::compare)
        .map(
            x ->
                o.forecast().targetLevel().compareTo(x.forecast().targetLevel()) > 0
                    ? "Raised"
                    : o.forecast().targetLevel().compareTo(x.forecast().targetLevel()) < 0
                        ? "Lowered"
                        : "Unchanged")
        .orElse("Initial");
  }

  private static Accuracy accuracy(
      List<ForecastObservation> all, List<DailyMarketBar> bars, LocalDate asOf) {
    List<Double> errors = new ArrayList<>(), percentages = new ArrayList<>();
    for (ForecastObservation o : all) {
      if (!o.status().equals("ELIGIBLE") || o.forecast().targetDate().isAfter(asOf)) continue;
      LocalDate target = o.forecast().targetDate();
      LocalDate outcomeDate = OutlookTradingDates.nextOrSame(target);
      if (!outcomeDate.isBefore(asOf)) continue;
      DailyMarketBar actual = exact(bars, outcomeDate);
      if (actual == null) continue;
      double error = o.forecast().targetLevel().doubleValue() - actual.close();
      errors.add(Math.abs(error));
      percentages.add(Math.abs(error / actual.close() * 100));
    }
    return new Accuracy(
        errors.size(),
        errors.isEmpty()
            ? null
            : errors.stream().mapToDouble(Double::doubleValue).average().orElseThrow(),
        percentages.isEmpty()
            ? null
            : percentages.stream().mapToDouble(Double::doubleValue).average().orElseThrow(),
        errors.size() < 5
            ? "Limited sample; no forecaster ranking (minimum 5 scored forecasts)"
            : "Descriptive historical sample");
  }

  private List<DailyMarketBar> loadBars(String symbol, LocalDate from, LocalDate through) {
    try {
      return market.dailyBars(symbol, from, through);
    } catch (RuntimeException unavailable) {
      return List.of();
    }
  }

  private static List<ForecastObservation> selected(List<ForecastObservation> all, LocalDate date) {
    Map<String, ForecastObservation> latest = new TreeMap<>();
    all.stream()
        .filter(
            o ->
                o.status().equals("ELIGIBLE")
                    && !utcDate(o.forecast().publishedAt()).isAfter(date)
                    && !utcDate(o.forecast().observedAt()).isAfter(date))
        .forEach(
            o ->
                latest.merge(
                    o.forecast().institutionKey(), o, (a, b) -> compare(a, b) >= 0 ? a : b));
    return latest.values().stream().filter(o -> age(o, date) <= STALE_DAYS).toList();
  }

  private static DailyMarketBar exact(List<DailyMarketBar> bars, LocalDate date) {
    return bars.stream().filter(b -> b.date().equals(date)).findFirst().orElse(null);
  }

  static DailyMarketBar publicationClose(List<DailyMarketBar> bars, Instant publishedAt) {
    return exact(bars, publicationReferenceDate(publishedAt));
  }

  private static LocalDate publicationReferenceDate(Instant publishedAt) {
    // Date-only imports use 00:00 UTC, conservatively excluding that UTC day's close.
    ZonedDateTime ny = publishedAt.atZone(ZoneId.of("America/New_York"));
    LocalDate latestDate =
        ny.toLocalTime().isBefore(LocalTime.of(16, 0))
            ? ny.toLocalDate().minusDays(1)
            : ny.toLocalDate();
    return OutlookTradingDates.previousOrSame(latestDate);
  }

  private VixContext vix(LocalDate asOf) {
    List<DailyMarketBar> bars =
        loadBars("^VIX", asOf.minusYears(1), asOf).stream()
            .filter(b -> !b.date().isAfter(asOf) && OutlookTradingDates.isTradingDay(b.date()))
            .collect(
                java.util.stream.Collectors.toMap(
                    DailyMarketBar::date, b -> b, (a, b) -> a, TreeMap::new))
            .values()
            .stream()
            .toList();
    String source = "Yahoo Finance chart history (^VIX); provider terms apply";
    if (bars.isEmpty())
      return new VixContext(
          null,
          null,
          null,
          null,
          null,
          "Unavailable: no dated VIX history",
          source,
          new VixPoint[0]);
    DailyMarketBar latest = bars.getLast();
    int n = bars.size();
    Double percentile =
        n < 200 ? null : bars.stream().filter(b -> b.close() <= latest.close()).count() * 100.0 / n;
    boolean stale = latest.date().isBefore(OutlookTradingDates.previousOrSame(asOf.minusDays(1)));
    return new VixContext(
        latest.close(),
        latest.date(),
        vixChange(bars, 5),
        vixChange(bars, 20),
        percentile,
        stale
            ? "Stale VIX quote"
            : n < 200
                ? "Dated VIX; insufficient 1-year percentile history (200 observations required)"
                : "Dated VIX history",
        source,
        bars.stream()
            .filter(b -> !b.date().isBefore(asOf.minusMonths(3)))
            .map(b -> new VixPoint(b.date(), b.close()))
            .toArray(VixPoint[]::new));
  }

  private static Double vixChange(List<DailyMarketBar> bars, int sessions) {
    LocalDate previous = bars.getLast().date();
    for (int i = 0; i < sessions; i++)
      previous = OutlookTradingDates.previousOrSame(previous.minusDays(1));
    DailyMarketBar base = exact(bars, previous);
    return base == null ? null : pct(bars.getLast().close(), base.close());
  }

  private static Double pct(double a, double b) {
    return b == 0 ? null : (a / b - 1) * 100;
  }

  private static LocalDate utcDate(java.time.Instant instant) {
    return instant.atZone(java.time.ZoneOffset.UTC).toLocalDate();
  }

  private static Double median(List<Double> x) {
    if (x.size() < 2) return null;
    int n = x.size();
    return n % 2 == 1 ? x.get(n / 2) : (x.get(n / 2 - 1) + x.get(n / 2)) / 2;
  }

  private static Double percentile(List<Double> x, double p) {
    if (x.isEmpty()) return null;
    double k = (x.size() - 1) * p;
    int f = (int) k, c = (int) Math.ceil(k);
    return x.get(f) + (k - f) * (x.get(c) - x.get(f));
  }
}
